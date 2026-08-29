package com.gravijet.daydrop

import com.gravijet.daydrop.data.model.DropType
import com.gravijet.daydrop.data.remote.HistoryEvent
import com.gravijet.daydrop.data.remote.HistorySource
import com.gravijet.daydrop.data.remote.NoRemote
import com.gravijet.daydrop.domain.DropGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Wikipedia stand-in so the tests never touch the network. */
private class FakeWiki(
    private val events: List<HistoryEvent> = emptyList(),
    private val holidays: List<String> = emptyList()
) : HistorySource {
    override suspend fun onThisDay(month: Int, day: Int) = events
    override suspend fun holidays(month: Int, day: Int) = holidays
    override suspend fun thumbnailFor(title: String): String? = "https://example.test/$title.jpg"
}

class DropGeneratorTest {

    private val content = testContent()

    private val BROAD = setOf("science", "tech", "space")

    private fun generator(remote: HistorySource = NoRemote) = DropGenerator(content, remote)

    @Test
    fun `a feed is built even with no connection at all`() = runBlocking {
        val drops = generator().buildFeed(LocalDate.of(2026, 3, 14), setOf("science", "space"))
        assertTrue("only ${drops.size} cards", drops.size >= 6)
        drops.forEach {
            assertTrue("blank title on ${it.id}", it.title.isNotBlank())
            assertTrue("blank body on ${it.id}", it.body.isNotBlank())
        }
    }

    @Test
    fun `a feed never shows the same card twice`() = runBlocking {
        repeat(60) { offset ->
            val date = LocalDate.of(2026, 1, 1).plusDays(offset.toLong())
            val ids = generator().buildFeed(date, setOf("gaming", "music", "history")).map { it.id }
            assertEquals("duplicate card on $date", ids.size, ids.toSet().size)
        }
    }

    @Test
    fun `the same day always produces the same feed`() = runBlocking {
        val date = LocalDate.of(2026, 6, 21)
        val interests = setOf("animals", "food")
        val first = generator().buildFeed(date, interests).map { it.id }
        val second = generator().buildFeed(date, interests).map { it.id }
        assertEquals(first, second)
    }

    @Test
    fun `different days produce different feeds`() = runBlocking {
        val a = generator().buildFeed(LocalDate.of(2026, 6, 21), setOf("tech")).map { it.id }
        val b = generator().buildFeed(LocalDate.of(2026, 6, 22), setOf("tech")).map { it.id }
        assertTrue("consecutive days repeated everything", a.toSet() != b.toSet())
    }

    @Test
    fun `content does not start repeating within four weeks`() = runBlocking {
        val seen = mutableListOf<String>()
        repeat(28) { offset ->
            val date = LocalDate.of(2026, 2, 1).plusDays(offset.toLong())
            // Cards tied to the calendar are meant to recur, so exclude them.
            seen += generator().buildFeed(date, setOf("science", "tech", "space"))
                .filterNot { it.type == DropType.TODAY_IS || it.type == DropType.HISTORY }
                .map { it.id }
        }
        val repeats = seen.size - seen.toSet().size
        assertTrue("$repeats repeated cards in 28 days", repeats == 0)
    }

    @Test
    fun `the no-repeat window holds from any starting date`() = runBlocking {
        // The walk must not depend on where in its cycle a user happens to start.
        listOf(
            LocalDate.of(2026, 1, 5), LocalDate.of(2026, 5, 17),
            LocalDate.of(2027, 8, 30), LocalDate.of(2029, 11, 2)
        ).forEach { start ->
            val seen = mutableListOf<String>()
            repeat(28) { offset ->
                seen += generator().buildFeed(start.plusDays(offset.toLong()), BROAD)
                    .filterNot { it.type == DropType.TODAY_IS || it.type == DropType.HISTORY }
                    .map { it.id }
            }
            val repeats = seen.size - seen.toSet().size
            assertTrue("$repeats repeats in the 28 days from $start", repeats == 0)
        }
    }

    @Test
    fun `personalised and general cards never draw from the same entry`() = runBlocking {
        val personalIds = mutableSetOf<String>()
        val generalIds = mutableSetOf<String>()
        repeat(120) { offset ->
            val date = LocalDate.of(2026, 1, 1).plusDays(offset.toLong())
            generator().buildFeed(date, BROAD)
                .filterNot { it.type == DropType.TODAY_IS || it.type == DropType.HISTORY }
                .forEach { if (it.personalised) personalIds += it.id else generalIds += it.id }
        }
        val both = personalIds intersect generalIds
        assertTrue("$both appeared in both roles", both.isEmpty())
    }

    @Test
    fun `curated day beats the wikipedia fallback`() = runBlocking {
        // 14 March is in days.json, so the fallback must not be used.
        val drops = generator(FakeWiki(holidays = listOf("Ein erfundener Feiertag irgendwo")))
            .buildFeed(LocalDate.of(2026, 3, 14), emptySet())
        val today = drops.first { it.type == DropType.TODAY_IS }
        assertEquals("Pi-Tag", today.title)
    }

    @Test
    fun `wikipedia fills in a day the calendar does not cover`() = runBlocking {
        val uncovered = (1..28)
            .map { LocalDate.of(2026, 6, it) }
            .first { "%02d-%02d".format(it.monthValue, it.dayOfMonth) !in content.days }

        val without = generator().buildFeed(uncovered, emptySet())
        assertTrue(without.none { it.type == DropType.TODAY_IS })

        val with = generator(FakeWiki(holidays = listOf("Tag der Testabdeckung in Absurdistan")))
            .buildFeed(uncovered, emptySet())
        val today = with.first { it.type == DropType.TODAY_IS }
        assertEquals("Tag der Testabdeckung in Absurdistan", today.title)
    }

    @Test
    fun `history cards report the right number of years`() = runBlocking {
        val remote = FakeWiki(
            events = listOf(
                HistoryEvent(1969, "Apollo 11 landet auf dem Mond.", null, null, "Apollo 11"),
                HistoryEvent(1215, "Die Magna Carta wird besiegelt.", null, null, "Magna Carta")
            )
        )
        val drops = generator(remote).buildFeed(LocalDate.of(2026, 7, 20), emptySet())
        val history = drops.filter { it.type == DropType.HISTORY }
        assertTrue("no history card", history.isNotEmpty())
        history.forEach {
            val year = it.title.toInt()
            assertTrue("eyebrow ${it.eyebrow} does not match $year", it.eyebrow.contains("${2026 - year}"))
        }
    }

    @Test
    fun `personalised cards match the chosen interests`() = runBlocking {
        repeat(30) { offset ->
            val date = LocalDate.of(2026, 4, 1).plusDays(offset.toLong())
            generator().buildFeed(date, setOf("gaming"))
                .filter { it.personalised }
                .forEach {
                    assertTrue("${it.id} on $date is not about gaming", "gaming" in it.topics)
                }
        }
    }

    @Test
    fun `a feed still works when no interests were picked`() = runBlocking {
        val drops = generator().buildFeed(LocalDate.of(2026, 9, 13), emptySet())
        assertTrue(drops.size >= 6)
        assertEquals(drops.size, drops.map { it.id }.toSet().size)
    }

    @Test
    fun `the quiz card carries a usable question`() = runBlocking {
        repeat(20) { offset ->
            val date = LocalDate.of(2026, 5, 1).plusDays(offset.toLong())
            val quiz = generator().buildFeed(date, setOf("science")).firstOrNull { it.quiz != null }
            assertNotNull("no quiz card on $date", quiz)
            val q = quiz!!.quiz!!
            assertTrue(q.answerIndex in q.options.indices)
            assertTrue(q.question.isNotBlank())
        }
    }

    @Test
    fun `wikipedia images are attached to cards that ask for one`() = runBlocking {
        val drops = generator(FakeWiki()).buildFeed(LocalDate.of(2026, 3, 14), setOf("space"))
        assertTrue("no card got an image", drops.any { it.imageUrl != null })
    }

    @Test
    fun `every day of the year produces a feed`() = runBlocking {
        // Includes 29 February via a leap year.
        var date = LocalDate.of(2024, 1, 1)
        while (date.year == 2024) {
            val drops = generator().buildFeed(date, setOf("history", "weird"))
            assertTrue("$date produced only ${drops.size} cards", drops.size >= 6)
            date = date.plusDays(1)
        }
    }
}
