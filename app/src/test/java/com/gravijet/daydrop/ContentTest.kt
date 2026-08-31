package com.gravijet.daydrop

import com.gravijet.daydrop.data.local.ContentRepository
import com.gravijet.daydrop.domain.ImagePicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private val TOPICS = setOf(
    "history", "science", "space", "animals", "nature", "body", "tech",
    "geography", "sports", "gaming", "music", "movies", "food", "language", "weird"
)

private val KINDS = setOf("fact", "know", "science", "pop", "random", "history")

fun testContent(): ContentRepository = ContentRepository { path ->
    File("src/main/assets/$path").readText()
}

/** The shipped JSON is the app's backbone - a typo here breaks every screen. */
class ContentTest {

    private val content = testContent()

    @Test
    fun `all content editions parse`() {
        assertTrue(content.facts.size >= 2300)
        assertTrue(content.days.size > 250)
        assertTrue(content.quizzes.size >= 200)
        assertTrue(content.images.isNotEmpty())
    }

    @Test
    fun `fact ids are unique`() {
        val ids = content.facts.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    /**
     * The same fact under two ids would slip past the no-repeat guarantee, so
     * neither the headline nor the body may appear twice.
     */
    @Test
    fun `no fact is written twice`() {
        val titles = content.facts.map { it.title }
        val duplicateTitles = titles.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue("duplicate titles: $duplicateTitles", duplicateTitles.isEmpty())

        val texts = content.facts.map { it.text }
        val duplicateTexts = texts.groupBy { it }.filterValues { it.size > 1 }.size
        assertEquals("duplicate texts", 0, duplicateTexts)
    }

    /**
     * The same fact told twice in different words would slip past both the id
     * check and the no-repeat guarantee, and it is exactly what makes a feed
     * feel padded. Two entries that share nearly half their vocabulary are
     * almost always the same story.
     */
    @Test
    fun `no fact is a reworded copy of another`() {
        val words = content.facts.map { fact ->
            fact.id to (fact.title + " " + fact.text)
                .lowercase()
                .replace("ä", "a").replace("ö", "o").replace("ü", "u").replace("ß", "ss")
                .split(Regex("[^a-z]+"))
                .filter { it.length >= 4 }
                .toSet()
        }
        val twins = mutableListOf<String>()
        for (i in words.indices) {
            for (j in i + 1 until words.size) {
                val (aId, a) = words[i]
                val (bId, b) = words[j]
                if (a.isEmpty() || b.isEmpty()) continue
                val overlap = a.count { it in b }
                val union = a.size + b.size - overlap
                if (overlap.toDouble() / union >= 0.45) twins += "$aId ~ $bId"
            }
        }
        assertTrue("near-duplicate facts: ${twins.take(10)}", twins.isEmpty())
    }

    @Test
    fun `facts use known kinds and topics and are never blank`() {
        content.facts.forEach { fact ->
            assertTrue("${fact.id}: kind ${fact.kind}", fact.kind in KINDS)
            assertTrue("${fact.id}: topics ${fact.topics}", TOPICS.containsAll(fact.topics))
            assertTrue("${fact.id}: no topic at all", fact.topics.isNotEmpty())
            assertTrue("${fact.id}: title too short", fact.title.length >= 12)
            assertTrue("${fact.id}: title too long", fact.title.length <= 110)
            assertTrue("${fact.id}: body too short", fact.text.length > 55)
            assertTrue("${fact.id}: body too long", fact.text.length <= 400)
            assertTrue("${fact.id}: title repeats the body", fact.title != fact.text)
            fact.sourceUrl?.let { url ->
                assertTrue("${fact.id}: source url must be https", url.startsWith("https://"))
                assertTrue("${fact.id}: source label missing", !fact.sourceLabel.isNullOrBlank())
            }
        }
    }

    /**
     * A card is a headline plus an explanation. One sentence that stops halfway
     * reads like filler, so every body has to carry at least two.
     */
    @Test
    fun `every fact explains itself in more than one sentence`() {
        content.facts.forEach { fact ->
            val sentences = fact.text.count { it == '.' || it == '!' || it == '?' }
            assertTrue("${fact.id}: only $sentences sentence(s)", sentences >= 2)
        }
    }

    @Test
    fun `dated entries use a valid month-day key`() {
        content.facts.mapNotNull { it.date }.forEach { date ->
            assertTrue("bad date $date", date.matches(Regex("\\d{2}-\\d{2}")))
            val (m, d) = date.split("-").map(String::toInt)
            assertTrue("bad month in $date", m in 1..12)
            assertTrue("bad day in $date", d in 1..31)
        }
    }

    @Test
    fun `day keys are valid dates and entries are filled in`() {
        content.days.forEach { (key, entries) ->
            assertTrue("bad key $key", key.matches(Regex("\\d{2}-\\d{2}")))
            val (m, d) = key.split("-").map(String::toInt)
            assertTrue("bad month in $key", m in 1..12)
            assertTrue("bad day in $key", d in 1..31)
            assertTrue("$key has no entry", entries.isNotEmpty())
            entries.forEach {
                assertTrue("$key: empty title", it.title.isNotBlank())
                assertTrue("$key: body too short", it.text.length > 55)
                assertTrue("$key: topics ${it.topics}", TOPICS.containsAll(it.topics))
            }
        }
    }

    @Test
    fun `every quiz has a reachable answer and an explanation`() {
        content.quizzes.forEach { quiz ->
            assertTrue("${quiz.id}: needs options", quiz.options.size >= 2)
            assertTrue(
                "${quiz.id}: answerIndex ${quiz.answerIndex} out of range",
                quiz.answerIndex in quiz.options.indices
            )
            assertTrue("${quiz.id}: no explanation", quiz.explanation.length > 30)
            assertTrue("${quiz.id}: duplicate options", quiz.options.size == quiz.options.toSet().size)
            assertTrue("${quiz.id}: topics", TOPICS.containsAll(quiz.topics))
        }
    }

    @Test
    fun `quiz ids and questions are unique`() {
        val ids = content.quizzes.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        val questions = content.quizzes.map { it.question }
        assertEquals("duplicate questions", questions.size, questions.toSet().size)
    }

    @Test
    fun `every interest has enough material for years of personalised cards`() {
        val pool = content.facts.filter { it.kind != "history" && it.date == null }
        TOPICS.forEach { topic ->
            val count = pool.count { topic in it.topics }
            assertTrue("topic $topic has only $count entries", count >= 30)
        }
    }

    // ---- photos ------------------------------------------------------------

    /**
     * The catalogue is what keeps a picture of a specific date off a card that
     * has nothing to do with that date.
     */
    @Test
    fun `every topic has photos and every photo is a real https url`() {
        TOPICS.forEach { topic ->
            val photos = content.images[topic].orEmpty()
            assertTrue("topic $topic has no photos", photos.size >= 5)
        }
        assertTrue("no default photos", content.images["_default"].orEmpty().size >= 5)

        content.images.forEach { (topic, photos) ->
            photos.forEach {
                assertTrue(
                    "$topic: not an upload url: ${it.url}",
                    it.url.startsWith("https://upload.wikimedia.org/")
                )
            }
        }
    }

    @Test
    fun `no photo is used in two topics by accident`() {
        val urls = content.images.values.flatten().map { it.url }
        // Some overlap is intended (minerals appear under science and weird),
        // but a catalogue that is mostly the same handful of pictures is not.
        assertTrue("only ${urls.toSet().size} distinct photos", urls.toSet().size >= 150)
    }

    @Test
    fun `every fact resolves to a photo`() {
        val picker = ImagePicker(content.images)
        content.facts.forEach { fact ->
            assertTrue(
                "${fact.id}: no image resolved for ${fact.topics}",
                !picker.imageFor(fact.id, fact.topics).isNullOrBlank()
            )
        }
    }
}
