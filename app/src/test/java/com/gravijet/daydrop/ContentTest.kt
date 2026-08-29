package com.gravijet.daydrop

import com.gravijet.daydrop.data.local.ContentRepository
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
    fun `all three content files parse`() {
        assertTrue(content.facts.size > 200)
        assertTrue(content.days.size > 100)
        assertTrue(content.quizzes.size >= 60)
    }

    @Test
    fun `fact ids are unique`() {
        val ids = content.facts.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `facts use known kinds and topics and are never blank`() {
        content.facts.forEach { fact ->
            assertTrue("${fact.id}: kind ${fact.kind}", fact.kind in KINDS)
            assertTrue("${fact.id}: topics ${fact.topics}", TOPICS.containsAll(fact.topics))
            assertTrue("${fact.id}: empty title", fact.title.isNotBlank())
            assertTrue("${fact.id}: body too short", fact.text.length > 40)
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
                assertTrue("$key: body too short", it.text.length > 40)
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
    fun `quiz ids are unique`() {
        val ids = content.quizzes.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every interest has enough material to personalise a feed`() {
        val pool = content.facts.filter { it.kind != "history" }
        TOPICS.forEach { topic ->
            val count = pool.count { topic in it.topics }
            // Three personalised cards a day; ten entries keeps a week varied.
            assertTrue("topic $topic has only $count entries", count >= 10)
        }
    }
}
