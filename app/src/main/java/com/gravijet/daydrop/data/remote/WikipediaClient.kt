package com.gravijet.daydrop.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/** What the generator needs from the outside world - swappable in tests. */
interface HistorySource {
    suspend fun onThisDay(month: Int, day: Int): List<HistoryEvent>
    suspend fun holidays(month: Int, day: Int): List<String>

    /**
     * The German Wikipedia "Artikel des Tages" for a date. One human-written
     * teaser per day that is never reused, which is what lets the feed keep
     * going long after the bundled pools have been walked through once.
     */
    suspend fun articleOfTheDay(year: Int, month: Int, day: Int): WikiArticle?
}

/** Offline stand-in: the app falls back to bundled content with this. */
object NoRemote : HistorySource {
    override suspend fun onThisDay(month: Int, day: Int) = emptyList<HistoryEvent>()
    override suspend fun holidays(month: Int, day: Int) = emptyList<String>()
    override suspend fun articleOfTheDay(year: Int, month: Int, day: Int): WikiArticle? = null
}

data class HistoryEvent(
    val year: Int,
    val text: String,
    val imageUrl: String?,
    val articleUrl: String?,
    val articleTitle: String?
)

data class WikiArticle(
    val title: String,
    val extract: String,
    val articleUrl: String?
)

/**
 * Thin client for the public Wikimedia REST endpoints. No key, no SDK - the
 * calls we need are plain GETs, so a hand-rolled client keeps the APK small.
 */
object WikipediaClient : HistorySource {

    private const val UA = "DayDrop/1.1 (personal Android app; contact: via GitHub gravijet/daydrop)"
    private val json = Json { ignoreUnknownKeys = true }

    /** Real events that happened on this calendar day, newest first. */
    override suspend fun onThisDay(month: Int, day: Int): List<HistoryEvent> = withContext(Dispatchers.IO) {
        val url = "https://de.wikipedia.org/api/rest_v1/feed/onthisday/events/" +
            "%02d/%02d".format(month, day)
        val body = get(url) ?: return@withContext emptyList()
        runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            root["events"]?.jsonArray.orEmpty().mapNotNull { element ->
                val event = element.jsonObject
                val year = event["year"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
                val text = event["text"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val page = event["pages"]?.jsonArray?.firstOrNull()?.jsonObject
                HistoryEvent(
                    year = year,
                    text = text.trim(),
                    imageUrl = page?.thumbnail(),
                    articleUrl = page?.articleUrl(),
                    articleTitle = page?.get("titles")?.jsonObject
                        ?.get("normalized")?.jsonPrimitive?.content
                )
            }.sortedByDescending { it.year }
        }.getOrDefault(emptyList())
    }

    /**
     * Observances Wikipedia lists for this date. Used only when the bundled
     * calendar has no curated entry, so no day is left without a "Heute ist".
     */
    override suspend fun holidays(month: Int, day: Int): List<String> = withContext(Dispatchers.IO) {
        val url = "https://de.wikipedia.org/api/rest_v1/feed/onthisday/holidays/" +
            "%02d/%02d".format(month, day)
        val body = get(url) ?: return@withContext emptyList()
        runCatching {
            json.parseToJsonElement(body).jsonObject["holidays"]?.jsonArray.orEmpty()
                .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content?.trim() }
                .filter { it.length in 8..220 }
        }.getOrDefault(emptyList())
    }

    override suspend fun articleOfTheDay(year: Int, month: Int, day: Int): WikiArticle? =
        withContext(Dispatchers.IO) {
            val url = "https://de.wikipedia.org/api/rest_v1/feed/featured/" +
                "%04d/%02d/%02d".format(year, month, day)
            val body = get(url) ?: return@withContext null
            runCatching {
                val tfa = json.parseToJsonElement(body).jsonObject["tfa"]?.jsonObject
                    ?: return@runCatching null
                val title = tfa["titles"]?.jsonObject?.get("normalized")?.jsonPrimitive?.content
                    ?: tfa["title"]?.jsonPrimitive?.content
                    ?: return@runCatching null
                val extract = tfa["extract"]?.jsonPrimitive?.content?.trim()
                    ?.takeIf { it.length > 60 }
                    ?: return@runCatching null
                WikiArticle(
                    title = title.replace('_', ' '),
                    // Two sentences are a card; the whole lead section is an essay.
                    extract = extract.shortenToSentences(2, 420),
                    articleUrl = tfa.articleUrl()
                )
            }.getOrNull()
        }

    /** Cuts after at most [sentences] full sentences, or [limit] characters. */
    private fun String.shortenToSentences(sentences: Int, limit: Int): String {
        var cut = 0
        var found = 0
        while (found < sentences) {
            val next = indexOf(". ", cut).takeIf { it >= 0 } ?: break
            cut = next + 1
            found++
        }
        val text = if (cut in 80..limit) substring(0, cut).trim() else take(limit).trim()
        return if (text.length < length && !text.endsWith(".")) "$text …" else text
    }

    private fun JsonObject.thumbnail(): String? =
        (this["thumbnail"] ?: this["originalimage"])?.jsonObject
            ?.get("source")?.jsonPrimitive?.content
            // Ask the thumbnailer for something big enough for a full-bleed card.
            ?.replace(Regex("/\\d+px-"), "/1000px-")

    private fun JsonObject.articleUrl(): String? =
        this["content_urls"]?.jsonObject?.get("desktop")?.jsonObject
            ?.get("page")?.jsonPrimitive?.content

    private fun get(url: String): String? = runCatching {
        (URL(url).openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json")
            try {
                if (responseCode !in 200..299) null
                else inputStream.bufferedReader().use { it.readText() }
            } finally {
                disconnect()
            }
        }
    }.getOrNull()
}
