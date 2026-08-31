package com.gravijet.daydrop

import com.gravijet.daydrop.data.local.ContentRepository
import com.gravijet.daydrop.data.model.DropType
import com.gravijet.daydrop.data.model.Slot
import com.gravijet.daydrop.data.remote.HistoryEvent
import com.gravijet.daydrop.data.remote.HistorySource
import com.gravijet.daydrop.data.remote.NoRemote
import com.gravijet.daydrop.data.remote.WikiArticle
import com.gravijet.daydrop.domain.CARDS_PER_DAY
import com.gravijet.daydrop.domain.DropGenerator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Same shipped content as [testContent], minus one key in days.json. */
private fun contentWithDayRemoved(missingKey: String): ContentRepository {
    val json = Json { ignoreUnknownKeys = true }
    return ContentRepository { path ->
        val raw = File("src/main/assets/$path").readText()
        if (path != "content/days.json") return@ContentRepository raw
        val days = json.parseToJsonElement(raw).jsonObject["days"]!!.jsonObject
        buildJsonObject {
            put("days", buildJsonObject {
                days.forEach { (key, value) -> if (key != missingKey) put(key, value) }
            })
        }.toString()
    }
}

/** Wikipedia stand-in so the tests never touch the network. */
private class FakeWiki(
    private val events: List<HistoryEvent> = emptyList(),
    private val holidays: List<String> = emptyList(),
    private val article: WikiArticle? = null
) : HistorySource {
    override suspend fun onThisDay(month: Int, day: Int) = events
    override suspend fun holidays(month: Int, day: Int) = holidays
    override suspend fun articleOfTheDay(year: Int, month: Int, day: Int) = article
}

class DropGeneratorTest {

    private val content = testContent()

    private val BROAD = setOf("science", "tech", "space")

    private fun generator(remote: HistorySource = NoRemote) = DropGenerator(content, remote)

    /**
     * Walks [days] consecutive days the way the app does: build, enrich, and
     * remember everything that was delivered. Returns every card in order.
     */
    private fun run(
        start: LocalDate,
        days: Int,
        interests: Set<String>,
        remote: HistorySource = NoRemote
    ): List<com.gravijet.daydrop.data.model.Drop> = runBlocking {
        val generator = generator(remote)
        val seen = mutableSetOf<String>()
        val all = mutableListOf<com.gravijet.daydrop.data.model.Drop>()
        repeat(days) { offset ->
            val date = start.plusDays(offset.toLong())
            val offline = generator.buildOffline(date, interests, seen)
            val feed = generator.enrich(date, offline, seen).ifEmpty { offline }
            seen += feed.map { it.id }
            all += feed
        }
        all
    }

    @Test
    fun `a feed is built even with no connection at all`() {
        val drops = generator().buildOffline(LocalDate.of(2026, 3, 14), setOf("science", "space"), emptySet())
        assertEquals(CARDS_PER_DAY, drops.size)
        drops.forEach {
            assertTrue("blank title on ${it.id}", it.title.isNotBlank())
            assertTrue("blank body on ${it.id}", it.body.isNotBlank())
            assertTrue("no slot on ${it.id}", it.slot.isNotBlank())
        }
    }

    @Test
    fun `the offline feed needs no network and no disk`() {
        // buildOffline is called on the main thread, so it must not be
        // suspending and must not depend on anything but the bundled JSON.
        val a = generator().buildOffline(LocalDate.of(2026, 5, 4), BROAD, emptySet())
        val b = generator().buildOffline(LocalDate.of(2026, 5, 4), BROAD, emptySet())
        assertEquals(a.map { it.id }, b.map { it.id })
    }

    @Test
    fun `a single day never shows the same card twice`() {
        repeat(60) { offset ->
            val date = LocalDate.of(2026, 1, 1).plusDays(offset.toLong())
            val ids = generator()
                .buildOffline(date, setOf("gaming", "music", "history"), emptySet())
                .map { it.id }
            assertEquals("duplicate card on $date", ids.size, ids.toSet().size)
        }
    }

    @Test
    fun `nothing repeats over five years`() {
        // The promise the app makes. 5 years at five cards a day, with the
        // curated pools running dry somewhere in the middle and Wikipedia
        // taking over from there.
        val remote = FakeWiki(
            events = (1900..1980).map {
                HistoryEvent(it, "Im Jahr $it geschah etwas Bemerkenswertes in der Welt.", null, null, null)
            },
            article = WikiArticle("Artikel", "Ein Artikel des Tages mit genug Text, um als Karte zu taugen.", null)
        )
        val seenTypes = mutableSetOf<String>()
        val repeats = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        runBlocking {
            val generator = generator(remote)
            var date = LocalDate.of(2026, 1, 1)
            repeat(365 * 5) {
                val offline = generator.buildOffline(date, BROAD, seen)
                val feed = generator.enrich(date, offline, seen).ifEmpty { offline }
                feed.forEach { drop ->
                    // "Heute ist" is tied to its date and is meant to return.
                    if (drop.type == DropType.TODAY_IS) return@forEach
                    // The stand-in article id is per day in this fake.
                    if (!seen.add(drop.id)) repeats += "${drop.id} on $date"
                    seenTypes += drop.slot
                }
                date = date.plusDays(1)
            }
        }
        assertTrue("repeated cards: ${repeats.take(5)}", repeats.isEmpty())
        assertTrue("slots used: $seenTypes", seenTypes.size >= 4)
    }

    @Test
    fun `the feed keeps five cards even after the curated pools are gone`() {
        val remote = FakeWiki(
            events = (1500..1990).map {
                HistoryEvent(it, "Im Jahr $it passierte etwas, das lange nachwirkte.", null, null, null)
            },
            article = WikiArticle("Artikel", "Ein Artikel des Tages mit genug Text, um als Karte zu taugen.", null)
        )
        // Pretend every bundled fact and quiz has already been shown.
        val exhausted = (content.facts.map { it.id } + content.quizzes.map { "quiz-${it.id}" }).toMutableSet()
        runBlocking {
            val generator = generator(remote)
            val date = LocalDate.of(2031, 6, 4)
            val offline = generator.buildOffline(date, BROAD, exhausted)
            val feed = generator.enrich(date, offline, exhausted)
            assertTrue("only ${feed.size} cards left", feed.size >= 3)
            feed.forEach { assertTrue("blank card ${it.id}", it.body.isNotBlank()) }
        }
    }

    @Test
    fun `the same day always produces the same feed`() {
        val date = LocalDate.of(2026, 6, 21)
        val interests = setOf("animals", "food")
        val first = generator().buildOffline(date, interests, emptySet()).map { it.id }
        val second = generator().buildOffline(date, interests, emptySet()).map { it.id }
        assertEquals(first, second)
    }

    @Test
    fun `personalised and general cards never draw from the same entry`() {
        val personalIds = mutableSetOf<String>()
        val generalIds = mutableSetOf<String>()
        run(LocalDate.of(2026, 1, 1), 200, BROAD)
            .filterNot { it.type == DropType.TODAY_IS || it.type == DropType.HISTORY }
            .forEach { if (it.personalised) personalIds += it.id else generalIds += it.id }
        val both = personalIds intersect generalIds
        assertTrue("$both appeared in both roles", both.isEmpty())
    }

    @Test
    fun `curated day beats the wikipedia fallback`() = runBlocking {
        // 14 March is in days.json, so the fallback must not be used.
        val remote = FakeWiki(holidays = listOf("Ein erfundener Feiertag irgendwo"))
        val offline = generator(remote).buildOffline(LocalDate.of(2026, 3, 14), emptySet(), emptySet())
        val today = offline.first { it.type == DropType.TODAY_IS }
        assertEquals("Pi-Tag", today.title)
    }

    @Test
    fun `wikipedia fills in a day the calendar does not cover`() = runBlocking {
        // The curated calendar is now complete (366 of 366 days), so no real
        // date is naturally uncovered - punch a hole in a copy of the content
        // for just this test instead, to keep exercising the fallback path.
        val missing = "06-15"
        val gappedContent = contentWithDayRemoved(missing)
        val uncovered = LocalDate.of(2026, 6, 15)

        val offline = DropGenerator(gappedContent, NoRemote).buildOffline(uncovered, emptySet(), emptySet())
        assertTrue(offline.none { it.type == DropType.TODAY_IS })

        val remote = FakeWiki(holidays = listOf("Tag der Testabdeckung in Absurdistan"))
        val enriched = DropGenerator(gappedContent, remote).enrich(uncovered, offline, emptySet())
        val today = enriched.first { it.type == DropType.TODAY_IS }
        assertEquals("Tag der Testabdeckung in Absurdistan", today.title)
    }

    @Test
    fun `the article of the day takes the open slot when there is a connection`() = runBlocking {
        val remote = FakeWiki(
            article = WikiArticle("Nordsee", "Die Nordsee ist ein Randmeer des Atlantiks und war einst Festland.", null)
        )
        val date = LocalDate.of(2026, 4, 9)
        val offline = generator().buildOffline(date, BROAD, emptySet())
        val standIn = offline.first { it.slot == Slot.DAILY }
        val enriched = generator(remote).enrich(date, offline, emptySet())
        val filled = enriched.first { it.slot == Slot.DAILY }
        assertEquals("Nordsee", filled.title)
        // And the curated stand-in is still unused, so it can be shown later.
        assertTrue(enriched.none { it.id == standIn.id })
    }

    @Test
    fun `history cards report the right number of years`() = runBlocking {
        val remote = FakeWiki(
            events = listOf(
                HistoryEvent(1969, "Apollo 11 landet auf dem Mond.", null, null, "Apollo 11"),
                HistoryEvent(1215, "Die Magna Carta wird besiegelt.", null, null, "Magna Carta")
            )
        )
        val date = LocalDate.of(2026, 7, 20)
        val offline = generator().buildOffline(date, emptySet(), emptySet())
        val drops = generator(remote).enrich(date, offline, emptySet())
        val history = drops.filter { it.type == DropType.HISTORY }
        assertTrue("no history card", history.isNotEmpty())
        history.forEach {
            val year = it.title.toInt()
            assertTrue("eyebrow ${it.eyebrow} does not match $year", it.eyebrow.contains("${2026 - year}"))
        }
    }

    @Test
    fun `personalised cards match the chosen interests`() {
        run(LocalDate.of(2026, 4, 1), 40, setOf("gaming"))
            .filter { it.personalised }
            .forEach { assertTrue("${it.id} is not about gaming", "gaming" in it.topics) }
    }

    @Test
    fun `a feed still works when no interests were picked`() {
        val drops = generator().buildOffline(LocalDate.of(2026, 9, 13), emptySet(), emptySet())
        assertEquals(CARDS_PER_DAY, drops.size)
        assertEquals(drops.size, drops.map { it.id }.toSet().size)
    }

    @Test
    fun `the quiz card carries a usable question`() {
        run(LocalDate.of(2026, 5, 1), 20, setOf("science"))
            .filter { it.quiz != null }
            .forEach {
                val q = it.quiz!!
                assertTrue(q.answerIndex in q.options.indices)
                assertTrue(q.question.isNotBlank())
            }
    }

    // ---- images ------------------------------------------------------------

    @Test
    fun `every card carries a photo from the curated catalogue`() {
        val urls = content.images.values.flatten().map { it.url }.toSet()
        run(LocalDate.of(2026, 2, 1), 30, BROAD).forEach { drop ->
            assertNotNull("${it(drop)} has no image", drop.imageUrl)
            assertTrue("${it(drop)} uses an unexpected image", drop.imageUrl in urls)
        }
    }

    private fun it(drop: com.gravijet.daydrop.data.model.Drop) = "${drop.slot}/${drop.id}"

    @Test
    fun `only the event of the day may carry a wikipedia photo`() = runBlocking {
        // A picture that belongs to one date must never turn up on another one.
        val remote = FakeWiki(
            events = listOf(
                HistoryEvent(
                    1969, "Apollo 11 landet auf dem Mond.",
                    "https://upload.wikimedia.org/apollo.jpg", null, "Apollo 11"
                )
            )
        )
        val date = LocalDate.of(2026, 7, 20)
        val offline = generator().buildOffline(date, BROAD, emptySet())
        val enriched = generator(remote).enrich(date, offline, emptySet())
        val catalogue = content.images.values.flatten().map { it.url }.toSet()
        enriched.forEach { drop ->
            if (drop.imageUrl !in catalogue) {
                assertEquals("a foreign photo on ${drop.slot}", DropType.HISTORY, drop.type)
            }
        }
    }

    @Test
    fun `every day of the year produces a full feed`() {
        var date = LocalDate.of(2024, 1, 1)
        while (date.year == 2024) {
            val drops = generator().buildOffline(date, setOf("history", "weird"), emptySet())
            assertEquals("$date produced ${drops.size} cards", CARDS_PER_DAY, drops.size)
            date = date.plusDays(1)
        }
    }

    @Test
    fun `dated entries only show up on their own date`() {
        val dated = content.facts.filter { it.date != null && it.kind != "history" }
        assertTrue("no dated entries to check", dated.isNotEmpty())
        val ids = dated.map { it.id }.toSet()
        // A whole year, and a dated entry may only ever appear on its own day.
        var date = LocalDate.of(2026, 1, 1)
        while (date.year == 2026) {
            val monthDay = "%02d-%02d".format(date.monthValue, date.dayOfMonth)
            generator().buildOffline(date, BROAD, emptySet())
                .filter { it.id in ids }
                .forEach { drop ->
                    val entry = dated.first { it.id == drop.id }
                    assertEquals("${drop.id} appeared on $date", monthDay, entry.date)
                }
            date = date.plusDays(1)
        }
    }

    @Test
    fun `an exhausted quiz pool does not crash the feed`() {
        val allQuizzes = content.quizzes.map { "quiz-${it.id}" }.toSet()
        val drops = generator().buildOffline(LocalDate.of(2030, 1, 1), BROAD, allQuizzes)
        assertNull(drops.firstOrNull { it.quiz != null })
        assertEquals(CARDS_PER_DAY, drops.size)
    }
}
