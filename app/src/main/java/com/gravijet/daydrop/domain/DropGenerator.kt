package com.gravijet.daydrop.domain

import com.gravijet.daydrop.data.local.ContentRepository
import com.gravijet.daydrop.data.local.FactEntry
import com.gravijet.daydrop.data.model.Drop
import com.gravijet.daydrop.data.model.DropType
import com.gravijet.daydrop.data.model.Quiz
import com.gravijet.daydrop.data.remote.HistoryEvent
import com.gravijet.daydrop.data.remote.HistorySource
import com.gravijet.daydrop.data.remote.WikipediaClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import kotlin.random.Random

/** How many cards of each sort one day consumes. */
private const val PERSONAL_PER_DAY = 3
private const val GENERAL_PER_DAY = 3
private const val MIN_CARDS = 6

/**
 * Ceiling on how long the network may hold up a feed. Everything Wikipedia adds
 * is a bonus on top of the bundled content, so a slow or captive connection
 * costs a few seconds at most and then the offline feed is shown instead.
 */
private const val NETWORK_BUDGET_MS = 6_000L

/**
 * Builds the drop of the day.
 *
 * Three rules drive everything here:
 *  - the same date always produces the same feed, so closing and reopening the
 *    app never reshuffles the cards under you;
 *  - each pool is walked as a seeded permutation at a fixed number of cards per
 *    day, so nothing comes back until the whole pool has been shown once;
 *  - the personalised and the general cards draw from disjoint pools, so one
 *    entry can never turn up twice wearing two different hats.
 */
class DropGenerator(
    private val content: ContentRepository,
    private val remote: HistorySource = WikipediaClient
) {

    /**
     * Deterministic, repeat-free draw from [pool].
     *
     * The pool is shuffled once into a fixed order - the shuffle depends only on
     * [salt], never on the date - and each day starts [stride] entries further
     * along it. A slot taking [stride] cards a day therefore sees every entry in
     * the pool before any of them comes back, with an exact gap of
     * `pool.size / stride` days between two showings of the same card.
     *
     * Reshuffling on wrap would look more varied but breaks that guarantee: a
     * fresh permutation puts cards from last week straight back at the front.
     */
    private class Picker<T>(pool: List<T>, epochDay: Long, salt: Int, stride: Int = 1) {
        private val order: List<T> =
            if (pool.isEmpty()) emptyList() else pool.shuffled(Random(salt * 1_000_003L))
        private val offset: Int =
            if (order.isEmpty()) 0 else (epochDay * stride).mod(order.size)
        private var taken = 0

        fun next(exclude: Set<String>, idOf: (T) -> String): T? {
            if (order.isEmpty()) return null
            repeat(order.size) {
                val candidate = order[(offset + taken) % order.size]
                taken++
                if (idOf(candidate) !in exclude) return candidate
            }
            return null
        }
    }

    suspend fun buildFeed(date: LocalDate, interests: Set<String>): List<Drop> = coroutineScope {
        val epochDay = date.toEpochDay()
        val monthDay = "%02d-%02d".format(date.monthValue, date.dayOfMonth)
        val used = mutableSetOf<String>()
        val drops = mutableListOf<Drop>()

        // History comes from Wikipedia when there is a connection; the bundled
        // dated entries keep the slot filled when there is not. The holiday call
        // is only made on days the curated calendar does not cover.
        val historyDeferred = async {
            withTimeoutOrNull(NETWORK_BUDGET_MS) {
                remote.onThisDay(date.monthValue, date.dayOfMonth)
            }.orEmpty()
        }
        val holidaysDeferred = if (content.days[monthDay].isNullOrEmpty()) {
            async {
                withTimeoutOrNull(NETWORK_BUDGET_MS) {
                    remote.holidays(date.monthValue, date.dayOfMonth)
                }.orEmpty()
            }
        } else null

        // Two sequences, each sized to what it is asked for per day. The general
        // slots share one sequence rather than getting one per card type: split
        // per type, every pool would be small enough to wrap within a fortnight.
        val personal = Picker(personalPool(interests), epochDay, 21, PERSONAL_PER_DAY)
        val general = Picker(generalPool(interests), epochDay, 11, GENERAL_PER_DAY)

        fun add(drop: Drop?) {
            if (drop != null && used.add(drop.id)) drops += drop
        }

        fun nextGeneral(): Drop? = general.next(used) { it.id }?.toDrop(personalised = false)

        fun nextPersonal(): Drop? {
            val entry = personal.next(used) { it.id } ?: return nextGeneral()
            return entry.toDrop(personalised = true)
        }

        // 1 - Heute ist ...
        add(
            todayIs(monthDay, epochDay)
                ?: holidaysDeferred?.await()?.let { wikiHoliday(it, monthDay, epochDay) }
        )

        // 2 and 3 - Heute vor X Jahren, two events from different eras
        historyDrops(historyDeferred.await(), date, monthDay, epochDay).forEach(::add)

        // 4 - a general card opens the fact section
        add(nextGeneral())

        // 5 - für dich
        add(nextPersonal())

        // 6 - Mini-Frage
        add(quizDrop(epochDay))

        // 7 - für dich
        add(nextPersonal())

        // 8 - anything written for exactly today, e.g. a film that premiered now
        add(datedDrop(monthDay, epochDay, used) ?: nextGeneral())

        // 9 - für dich
        add(nextPersonal())

        // 10 - a general card closes the day out
        add(nextGeneral())

        while (drops.size < MIN_CARDS) {
            add(nextGeneral() ?: break)
        }

        resolveImages(drops)
    }

    // ---- pools -------------------------------------------------------------

    /**
     * Entries that belong to one specific calendar day. They would feel
     * arbitrary on any other date, so the everyday sequences leave them alone
     * and they surface only when their day comes round.
     */
    private fun FactEntry.isCalendarBound() = kind == "history" || date != null

    private fun personalPool(interests: Set<String>): List<FactEntry> {
        if (interests.isEmpty()) return emptyList()
        return content.facts.filter {
            !it.isCalendarBound() && it.topics.any { topic -> topic in interests }
        }
    }

    /** Everything the personalised slots do not own. */
    private fun generalPool(interests: Set<String>): List<FactEntry> = content.facts.filter {
        !it.isCalendarBound() && it.topics.none { topic -> topic in interests }
    }

    // ---- slots -------------------------------------------------------------

    private fun todayIs(monthDay: String, epochDay: Long): Drop? {
        val entries = content.days[monthDay].orEmpty()
        if (entries.isEmpty()) return null
        val entry = entries[(epochDay % entries.size).toInt()]
        return Drop(
            id = "day-$monthDay-${entry.title.hashCode()}",
            type = DropType.TODAY_IS,
            eyebrow = "Heute ist",
            title = entry.title,
            body = entry.text,
            topics = entry.topics,
            sourceUrl = entry.wiki?.let(::wikiUrl),
            sourceLabel = entry.wiki?.let { "Wikipedia · $it" }
        ).withWiki(entry.wiki)
    }

    /** Turns a Wikipedia observance line into a "Heute ist" card. */
    private fun wikiHoliday(holidays: List<String>, monthDay: String, epochDay: Long): Drop? {
        if (holidays.isEmpty()) return null
        val text = holidays[(epochDay % holidays.size).toInt()]
        // The lines read like "Tag der X in Y" - the part before the first comma
        // or bracket makes a better headline than the whole sentence.
        val title = text.substringBefore(',').substringBefore(" (").trim()
            .takeIf { it.length in 4..70 } ?: text.take(70)
        val (month, day) = monthDay.split('-').map(String::toInt)
        return Drop(
            id = "wikiday-$monthDay-${text.hashCode()}",
            type = DropType.TODAY_IS,
            eyebrow = "Heute ist",
            title = title,
            body = text,
            sourceUrl = wikiUrl("$day. ${monthName(month)}"),
            sourceLabel = "Wikipedia"
        )
    }

    private fun historyDrops(
        events: List<HistoryEvent>,
        date: LocalDate,
        monthDay: String,
        epochDay: Long
    ): List<Drop> {
        if (events.isEmpty()) {
            // Offline fallback: bundled entries carrying today's date.
            val local = content.facts.filter { it.kind == "history" && it.date == monthDay }
            if (local.isEmpty()) return emptyList()
            return listOf(local[(epochDay % local.size).toInt()].toDrop(personalised = false))
        }
        // Two events from different eras beat two from the same decade.
        val usable = events.filter { it.text.length in 30..320 }.ifEmpty { events }
        val rng = Random(epochDay)
        val older = usable.filter { date.year - it.year >= 100 }
        val newer = usable.filter { date.year - it.year < 100 }
        val chosen = listOfNotNull(
            newer.randomOrNull(rng) ?: usable.randomOrNull(rng),
            older.randomOrNull(rng)
        ).distinctBy { it.year to it.text }

        return chosen.map { event ->
            val years = date.year - event.year
            Drop(
                id = "hist-$monthDay-${event.year}-${event.text.hashCode()}",
                type = DropType.HISTORY,
                eyebrow = when {
                    years <= 0 -> "Heute"
                    years == 1 -> "Heute vor einem Jahr"
                    else -> "Heute vor $years Jahren"
                },
                title = "${event.year}",
                body = event.text,
                imageUrl = event.imageUrl,
                sourceUrl = event.articleUrl,
                sourceLabel = event.articleTitle?.let { "Wikipedia · $it" } ?: "Wikipedia",
                topics = listOf("history")
            )
        }
    }

    /** An entry written for exactly this calendar day, if there is one. */
    private fun datedDrop(monthDay: String, epochDay: Long, used: Set<String>): Drop? {
        val dated = content.facts.filter {
            it.date == monthDay && it.kind != "history" && it.id !in used
        }
        if (dated.isEmpty()) return null
        return dated[(epochDay % dated.size).toInt()].toDrop(personalised = false)
    }

    private fun quizDrop(epochDay: Long): Drop? {
        val entry = Picker(content.quizzes, epochDay, 61).next(emptySet()) { it.id } ?: return null
        return Drop(
            id = "quiz-${entry.id}",
            type = DropType.QUIZ,
            eyebrow = "Was glaubst du?",
            title = entry.question,
            body = entry.explanation,
            quiz = Quiz(entry.question, entry.options, entry.answerIndex, entry.explanation),
            topics = entry.topics
        )
    }

    // ---- helpers -----------------------------------------------------------

    private fun typeFor(kind: String) = when (kind) {
        "fact" -> DropType.FACT
        "know" -> DropType.DID_YOU_KNOW
        "science" -> DropType.SCIENCE
        "pop" -> DropType.POP_CULTURE
        "history" -> DropType.HISTORY
        else -> DropType.RANDOM
    }

    private fun FactEntry.toDrop(personalised: Boolean): Drop {
        val type = typeFor(kind)
        return Drop(
            id = id,
            type = type,
            eyebrow = type.label,
            title = title,
            body = text,
            topics = topics,
            personalised = personalised,
            sourceUrl = wiki?.let(::wikiUrl),
            sourceLabel = wiki?.let { "Wikipedia · $it" }
        ).withWiki(wiki)
    }

    private fun wikiUrl(title: String) =
        "https://de.wikipedia.org/wiki/" + title.replace(' ', '_')

    private fun monthName(month: Int) = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember"
    )[month - 1]

    /** Fetches the Wikipedia header images for every card that asked for one. */
    private suspend fun resolveImages(drops: List<Drop>): List<Drop> = coroutineScope {
        drops.map { drop ->
            val wiki = wikiTitles[drop.id]
            if (drop.imageUrl != null || wiki == null) async { drop }
            else async {
                val url = withTimeoutOrNull(NETWORK_BUDGET_MS) { remote.thumbnailFor(wiki) }
                drop.copy(imageUrl = url)
            }
        }.map { it.await() }
    }

    /** Side table so [Drop] itself stays a clean serialisable model. */
    private val wikiTitles = mutableMapOf<String, String>()

    private fun Drop.withWiki(title: String?): Drop {
        if (title != null) wikiTitles[id] = title
        return this
    }
}
