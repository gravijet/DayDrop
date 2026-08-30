package com.gravijet.daydrop.domain

import com.gravijet.daydrop.data.local.ContentRepository
import com.gravijet.daydrop.data.local.FactEntry
import com.gravijet.daydrop.data.model.Drop
import com.gravijet.daydrop.data.model.DropType
import com.gravijet.daydrop.data.model.Quiz
import com.gravijet.daydrop.data.model.Slot
import com.gravijet.daydrop.data.remote.HistoryEvent
import com.gravijet.daydrop.data.remote.HistorySource
import com.gravijet.daydrop.data.remote.WikipediaClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import kotlin.random.Random

/**
 * Five cards. Short enough to get through in under a minute, which is the whole
 * point of a daily drop - the old ten-card day needed nine swipes to finish.
 */
const val CARDS_PER_DAY = 5

/**
 * Ceiling on how long the network may hold up an enrichment pass. The feed is
 * already on screen by then, so a slow connection costs nothing but a card that
 * fills itself in a moment later.
 */
private const val NETWORK_BUDGET_MS = 8_000L

/**
 * Builds the drop of the day, in two passes.
 *
 * [buildOffline] runs entirely on the bundled JSON and returns immediately, so
 * the app can paint a complete feed on its first frame. [enrich] then goes to
 * Wikipedia and hands back the same five slots with the live parts filled in.
 *
 * Two rules drive what lands in those slots:
 *
 *  - **Nothing is ever shown twice.** Every card the app has delivered is
 *    remembered by id, and the generator only ever draws from what is left. The
 *    guarantee does not depend on pool sizes or on how often the app is opened.
 *  - **When a curated pool is used up, the slot does not start over** - it
 *    switches to Wikipedia (Artikel des Tages, further events of the day),
 *    which supplies new material indefinitely.
 *
 * Only the "Heute ist" card is meant to come round again: a day of remembrance
 * belongs to its date, and saying so once a year is the point of it.
 */
class DropGenerator(
    private val content: ContentRepository,
    private val remote: HistorySource = WikipediaClient
) {

    private val images = ImagePicker(content.images)

    // ---- pass one: instant, offline ----------------------------------------

    /**
     * The whole feed from bundled content alone. Cheap enough to call on the
     * main thread: it is a handful of list filters over data that is already
     * parsed and in memory.
     */
    fun buildOffline(date: LocalDate, interests: Set<String>, seen: Set<String>): List<Drop> {
        val monthDay = monthDay(date)
        val used = mutableSetOf<String>()
        val drops = mutableListOf<Drop>()

        val pools = pools(interests)

        fun add(drop: Drop?) {
            if (drop != null && used.add(drop.id)) drops += drop
        }

        // 1 - Heute ist ...
        add(todayIs(monthDay, date.year))

        // 2 - Heute vor X Jahren. Offline this is a bundled entry for the date;
        //     enrich() swaps in the real event of the day when there is a line.
        add(bundledHistory(monthDay, seen))

        // 3 - the open slot. Online it becomes Wikipedia's Artikel des Tages,
        //     offline a curated card stands in. Because the stand-in is only
        //     marked as shown when it survives, the bundled pool is spent at
        //     one card a day instead of two whenever there is a connection.
        add(
            pick(pools.general, seen + used)
                ?.toDrop(personalised = false, slot = Slot.DAILY)
        )

        // 4 - für dich: today's dated entry if there is one, otherwise a card
        //     from the chosen topics
        add(
            datedEntry(monthDay, seen + used)
                ?.toDrop(personalised = false, slot = Slot.FOR_YOU)
                ?: pick(pools.personal, seen + used)
                    ?.toDrop(personalised = true, slot = Slot.FOR_YOU)
        )

        // 5 - Mini-Frage
        add(quizDrop(seen + used))

        // Late in the app's life single pools run dry. Anything still open is
        // filled from whatever curated material is left before enrich() reaches
        // for Wikipedia.
        while (drops.size < CARDS_PER_DAY) {
            val filler = pick(pools.general, seen + used)
                ?: pick(pools.personal, seen + used)
                ?: break
            add(filler.toDrop(personalised = false, slot = Slot.DAILY))
        }

        return drops
    }

    // ---- pass two: what the network adds ------------------------------------

    /**
     * Takes the offline feed and gives back the same day with the live parts
     * filled in: the real event of the day, a "Heute ist" for dates the bundled
     * calendar does not cover, and Wikipedia's Artikel des Tages wherever a
     * curated pool has nothing left to give.
     *
     * Never throws and never returns fewer cards than it was given - the worst
     * a dead connection can do is hand [offline] straight back.
     */
    suspend fun enrich(
        date: LocalDate,
        offline: List<Drop>,
        seen: Set<String>
    ): List<Drop> = coroutineScope {
        val monthDay = monthDay(date)

        val eventsDeferred = async {
            withTimeoutOrNull(NETWORK_BUDGET_MS) {
                runCatching { remote.onThisDay(date.monthValue, date.dayOfMonth) }.getOrNull()
            }.orEmpty()
        }
        val needsToday = offline.none { it.type == DropType.TODAY_IS }
        val holidaysDeferred = if (needsToday) async {
            withTimeoutOrNull(NETWORK_BUDGET_MS) {
                runCatching { remote.holidays(date.monthValue, date.dayOfMonth) }.getOrNull()
            }.orEmpty()
        } else null
        val articleDeferred = async {
            withTimeoutOrNull(NETWORK_BUDGET_MS) {
                runCatching {
                    remote.articleOfTheDay(date.year, date.monthValue, date.dayOfMonth)
                }.getOrNull()
            }
        }

        val used = offline.map { it.id }.toMutableSet()
        val result = mutableListOf<Drop>()

        // "Heute ist" for a date the curated calendar does not cover.
        if (needsToday) {
            holidaysDeferred?.await()?.let { wikiHoliday(it, monthDay, seen) }
                ?.takeIf { used.add(it.id) }
                ?.let { result += it }
        }

        // The two provisional slots get their live version, if there is one.
        val events = eventsDeferred.await()
        val liveHistory = historyDrop(events, date, monthDay, seen + used)
        val article = articleDeferred.await()
            ?.let { articleDrop(it, date) }
            ?.takeIf { it.id !in seen }

        offline.forEach { drop ->
            val live = when (drop.slot) {
                Slot.HISTORY -> liveHistory
                Slot.DAILY -> article
                else -> null
            }
            val chosen = live?.takeIf { it.id !in used } ?: drop
            if (used.add(chosen.id) || chosen === drop) result += chosen
        }

        // Slots the bundled pools could not fill at all - late in the app's
        // life this is where the feed keeps going: the article of the day
        // first, then further events from the same date.
        if (result.size < CARDS_PER_DAY && article != null && article.id !in used) {
            used += article.id
            result += article
        }
        if (result.size < CARDS_PER_DAY) {
            events.asSequence()
                .mapNotNull { historyDrop(listOf(it), date, monthDay, seen + used) }
                .filter { it.id !in seen && it.id !in used }
                .take(CARDS_PER_DAY - result.size)
                .forEach { used += it.id; result += it }
        }

        result
    }

    // ---- pools --------------------------------------------------------------

    /**
     * The two pools in their fixed walking order, built once per set of chosen
     * topics.
     *
     * Both the split and the shuffle are pure functions of the interests and a
     * salt, so caching them changes nothing about what is picked - it only stops
     * [buildOffline] from re-filtering and re-shuffling twelve hundred entries
     * on every call. That matters: it runs on the main thread while the app is
     * starting up.
     */
    private class Pools(facts: List<FactEntry>, interests: Set<String>) {
        private fun FactEntry.isCalendarBound() = kind == "history" || date != null

        val personal: List<FactEntry> =
            if (interests.isEmpty()) emptyList()
            else facts.filter { !it.isCalendarBound() && it.topics.any { t -> t in interests } }
                .shuffled(Random(PERSONAL_SALT * 1_000_003L))

        /** Everything the personalised slot does not own. */
        val general: List<FactEntry> =
            facts.filter { !it.isCalendarBound() && it.topics.none { t -> t in interests } }
                .shuffled(Random(GENERAL_SALT * 1_000_003L))
    }

    private var cachedInterests: Set<String>? = null
    private var cachedPools: Pools? = null

    private fun pools(interests: Set<String>): Pools {
        cachedPools?.takeIf { cachedInterests == interests }?.let { return it }
        return Pools(content.facts, interests).also {
            cachedInterests = interests
            cachedPools = it
        }
    }

    /**
     * The next unseen entry of [pool].
     *
     * The order is a permutation seeded by a salt alone - never by the date - so
     * it is the same on every device and every run. Walking it while skipping
     * everything already seen means the pool is worked through exactly once,
     * whether the app is opened daily or twice a year, and `null` means the pool
     * really is exhausted rather than merely unlucky today.
     */
    private fun pick(pool: List<FactEntry>, seen: Set<String>): FactEntry? =
        pool.firstOrNull { it.id !in seen }

    // ---- slots --------------------------------------------------------------

    /**
     * The day's observance. Days with several entries rotate by year, so a date
     * with three entries takes three years to come back to the first one.
     */
    private fun todayIs(monthDay: String, year: Int): Drop? {
        val entries = content.days[monthDay].orEmpty()
        if (entries.isEmpty()) return null
        val entry = entries[year.mod(entries.size)]
        val id = "day-$monthDay-${entry.title.hashCode()}"
        return Drop(
            id = id,
            type = DropType.TODAY_IS,
            eyebrow = "Heute ist",
            title = entry.title,
            body = entry.text,
            topics = entry.topics,
            slot = Slot.TODAY,
            imageUrl = images.imageFor(id, entry.topics),
            sourceUrl = entry.wiki?.let(::wikiUrl),
            sourceLabel = entry.wiki?.let { "Wikipedia · $it" }
        )
    }

    /** Turns a Wikipedia observance line into a "Heute ist" card. */
    private fun wikiHoliday(holidays: List<String>, monthDay: String, seen: Set<String>): Drop? {
        val text = holidays.firstOrNull { "wikiday-$monthDay-${it.hashCode()}" !in seen }
            ?: holidays.firstOrNull()
            ?: return null
        // The lines read like "Tag der X in Y" - the part before the first comma
        // or bracket makes a better headline than the whole sentence.
        val title = text.substringBefore(',').substringBefore(" (").trim()
            .takeIf { it.length in 4..70 } ?: text.take(70)
        val (month, day) = monthDay.split('-').map(String::toInt)
        val id = "wikiday-$monthDay-${text.hashCode()}"
        return Drop(
            id = id,
            type = DropType.TODAY_IS,
            eyebrow = "Heute ist",
            title = title,
            body = text,
            slot = Slot.TODAY,
            imageUrl = images.imageFor(id, emptyList()),
            sourceUrl = wikiUrl("$day. ${monthName(month)}"),
            sourceLabel = "Wikipedia"
        )
    }

    /** Offline stand-in for slot two: a bundled entry written for this date. */
    private fun bundledHistory(monthDay: String, seen: Set<String>): Drop? =
        content.facts
            .filter { it.kind == "history" && it.date == monthDay && it.id !in seen }
            .minByOrNull { it.id }
            ?.toDrop(personalised = false, slot = Slot.HISTORY)

    /**
     * The event of the day. Recent history reads better than antiquity on a
     * phone, so a year within living memory wins when the day offers one.
     */
    private fun historyDrop(
        events: List<HistoryEvent>,
        date: LocalDate,
        monthDay: String,
        seen: Set<String>
    ): Drop? {
        val usable = events
            .filter { it.text.length in 30..320 }
            .ifEmpty { events }
            .filter { "hist-$monthDay-${it.year}-${it.text.hashCode()}" !in seen }
        if (usable.isEmpty()) return null
        val event = usable.firstOrNull { date.year - it.year in 1..120 } ?: usable.first()

        val years = date.year - event.year
        val id = "hist-$monthDay-${event.year}-${event.text.hashCode()}"
        return Drop(
            id = id,
            type = DropType.HISTORY,
            eyebrow = when {
                years <= 0 -> "Heute"
                years == 1 -> "Heute vor einem Jahr"
                else -> "Heute vor $years Jahren"
            },
            title = "${event.year}",
            body = event.text,
            slot = Slot.HISTORY,
            // The one place a date-bound picture belongs: this card is only
            // ever shown on the day the event happened.
            imageUrl = event.imageUrl ?: images.imageFor(id, listOf("history")),
            sourceUrl = event.articleUrl,
            sourceLabel = event.articleTitle?.let { "Wikipedia · $it" } ?: "Wikipedia",
            topics = listOf("history")
        )
    }

    /** Wikipedia's Artikel des Tages - the supply that never runs out. */
    private fun articleDrop(article: com.gravijet.daydrop.data.remote.WikiArticle, date: LocalDate): Drop {
        val id = "tfa-${date.toEpochDay()}"
        return Drop(
            id = id,
            type = DropType.DID_YOU_KNOW,
            eyebrow = "Artikel des Tages",
            title = article.title,
            body = article.extract,
            slot = Slot.DAILY,
            imageUrl = images.imageFor(id, emptyList()),
            sourceUrl = article.articleUrl ?: wikiUrl(article.title),
            sourceLabel = "Wikipedia · ${article.title}"
        )
    }

    /** An entry written for exactly this calendar day, if there is one. */
    private fun datedEntry(monthDay: String, seen: Set<String>): FactEntry? =
        content.facts
            .filter { it.date == monthDay && it.kind != "history" && it.id !in seen }
            .minByOrNull { it.id }

    private fun quizDrop(seen: Set<String>): Drop? {
        val entry = content.quizzes
            .shuffled(Random(QUIZ_SALT * 1_000_003L))
            .firstOrNull { "quiz-${it.id}" !in seen }
            ?: return null
        val id = "quiz-${entry.id}"
        return Drop(
            id = id,
            type = DropType.QUIZ,
            eyebrow = "Was glaubst du?",
            title = entry.question,
            body = entry.explanation,
            slot = Slot.QUIZ,
            imageUrl = images.imageFor(id, entry.topics),
            quiz = Quiz(entry.question, entry.options, entry.answerIndex, entry.explanation),
            topics = entry.topics
        )
    }

    // ---- helpers ------------------------------------------------------------

    private fun monthDay(date: LocalDate) = "%02d-%02d".format(date.monthValue, date.dayOfMonth)

    private fun typeFor(kind: String) = when (kind) {
        "fact" -> DropType.FACT
        "know" -> DropType.DID_YOU_KNOW
        "science" -> DropType.SCIENCE
        "pop" -> DropType.POP_CULTURE
        "history" -> DropType.HISTORY
        else -> DropType.RANDOM
    }

    private fun FactEntry.toDrop(personalised: Boolean, slot: String): Drop {
        val type = typeFor(kind)
        return Drop(
            id = id,
            type = type,
            eyebrow = if (type == DropType.HISTORY) "Aus der Geschichte" else type.label,
            title = title,
            body = text,
            topics = topics,
            personalised = personalised,
            slot = slot,
            imageUrl = images.imageFor(id, topics),
            sourceUrl = wiki?.let(::wikiUrl),
            sourceLabel = wiki?.let { "Wikipedia · $it" }
        )
    }

    private fun wikiUrl(title: String) =
        "https://de.wikipedia.org/wiki/" + title.replace(' ', '_')

    private fun monthName(month: Int) = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember"
    )[month - 1]

    private companion object {
        /**
         * Salts for the three walking orders. They only have to differ from one
         * another; changing one reshuffles that pool for everybody, so they are
         * fixed for good.
         */
        const val GENERAL_SALT = 11
        const val PERSONAL_SALT = 21
        const val QUIZ_SALT = 61
    }
}
