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
    /** Optional MM-dd. Entries with a date are only ever shown on that day. */
    val date: String? = null,
    /** Optional German Wikipedia article, used for the "read on" link. */
    val wiki: String? = null,
    /** A primary or editorial source may be preferable to an encyclopedia link. */
    val sourceUrl: String? = null,
    val sourceLabel: String? = null
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
data class ImageEntry(val url: String, val file: String = "")

@Serializable
private data class FactsFile(val facts: List<FactEntry>)

@Serializable
private data class DaysFile(val days: Map<String, List<DayEntry>>)

@Serializable
private data class QuizFile(val quizzes: List<QuizEntry>)

@Serializable
private data class ImagesFile(val images: Map<String, List<ImageEntry>>)

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

    /**
     * Facts live in small, independently editable editions. Keeping the long
     * established library separate from new editorial batches makes additions
     * reviewable and avoids a single unwieldy asset file.
     */
    val facts: List<FactEntry> by lazy {
        listOf("content/facts.json", "content/facts_atlas.json")
            .flatMap { path -> json.decodeFromString<FactsFile>(read(path)).facts }
    }

    val days: Map<String, List<DayEntry>> by lazy {
        json.decodeFromString<DaysFile>(read("content/days.json")).days
    }

    val quizzes: List<QuizEntry> by lazy {
        json.decodeFromString<QuizFile>(read("content/quiz.json")).quizzes
    }

    /** Topic id -> photos that fit that topic. `_default` catches the rest. */
    val images: Map<String, List<ImageEntry>> by lazy {
        json.decodeFromString<ImagesFile>(read("content/images.json")).images
    }

    /**
     * Parses every file now instead of on first touch. Called off the main
     * thread while the app starts, so the feed screen finds everything ready
     * and can render its first frame without waiting for I/O.
     */
    fun warmUp() {
        facts; days; quizzes; images
    }

    companion object {
        @Volatile private var instance: ContentRepository? = null

        /**
         * One parsed copy per process. The JSON is a few hundred kilobytes and
         * completely immutable, so re-reading it per screen would be pure cost.
         */
        fun get(context: Context): ContentRepository =
            instance ?: synchronized(this) {
                instance ?: ContentRepository(context.applicationContext).also { instance = it }
            }
    }
}
