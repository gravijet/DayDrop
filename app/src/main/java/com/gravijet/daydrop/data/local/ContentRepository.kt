package com.gravijet.daydrop.data.local

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FactEntry(
    val id: String,
    val kind: String,
    val title: String,
    val text: String,
    val topics: List<String> = emptyList(),
    /** Optional MM-dd. Entries with a date are preferred on that calendar day. */
    val date: String? = null,
    /** Optional German Wikipedia article used to pull a fitting header image. */
    val wiki: String? = null
)

@Serializable
data class DayEntry(
    val title: String,
    val text: String,
    val topics: List<String> = emptyList(),
    val wiki: String? = null
)

@Serializable
data class QuizEntry(
    val id: String,
    val question: String,
    val options: List<String>,
    @SerialName("answerIndex") val answerIndex: Int,
    val explanation: String,
    val topics: List<String> = emptyList()
)

@Serializable
private data class FactsFile(val facts: List<FactEntry>)

@Serializable
private data class DaysFile(val days: Map<String, List<DayEntry>>)

@Serializable
private data class QuizFile(val quizzes: List<QuizEntry>)

/**
 * Reads the curated content that ships inside the APK. Everything here works
 * offline; the Wikipedia calls on top of it are a bonus, never a requirement.
 *
 * [read] is injectable so tests can load the same asset files from disk.
 */
class ContentRepository(private val read: (String) -> String) {

    constructor(context: Context) : this({ path ->
        context.assets.open(path).bufferedReader().use { it.readText() }
    })

    private val json = Json { ignoreUnknownKeys = true }

    val facts: List<FactEntry> by lazy {
        json.decodeFromString<FactsFile>(read("content/facts.json")).facts
    }

    val days: Map<String, List<DayEntry>> by lazy {
        json.decodeFromString<DaysFile>(read("content/days.json")).days
    }

    val quizzes: List<QuizEntry> by lazy {
        json.decodeFromString<QuizFile>(read("content/quiz.json")).quizzes
    }
}
