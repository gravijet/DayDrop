package com.gravijet.daydrop.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gravijet.daydrop.data.model.Drop
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val Context.dataStore by preferencesDataStore(name = "daydrop")

data class Streak(val current: Int, val best: Int, val lastDay: Long)

/** One read of everything the feed screen needs to draw its first frame. */
data class FeedSnapshot(
    val interests: Set<String>,
    val seen: Set<String>,
    val streak: Streak,
    val cachedFeed: List<Drop>?
)

/**
 * Everything the app remembers about you: chosen topics, streak, favourites and
 * the reminder time. All of it stays on the device.
 */
class UserPrefs(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val interests = stringSetPreferencesKey("interests")
        val streakCurrent = intPreferencesKey("streak_current")
        val streakBest = intPreferencesKey("streak_best")
        val streakLastDay = longPreferencesKey("streak_last_day")
        val favourites = stringPreferencesKey("favourites")
        val notifyEnabled = booleanPreferencesKey("notify_enabled")
        val notifyHour = intPreferencesKey("notify_hour")
        val notifyMinute = intPreferencesKey("notify_minute")
        val seen = stringPreferencesKey("seen_ids")
        val feedDay = longPreferencesKey("feed_day")
        val feedJson = stringPreferencesKey("feed_json")
    }

    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[Keys.onboarded] ?: false }

    val interests: Flow<Set<String>> = context.dataStore.data.map { it[Keys.interests] ?: emptySet() }

    val streak: Flow<Streak> = context.dataStore.data.map {
        Streak(
            current = it[Keys.streakCurrent] ?: 0,
            best = it[Keys.streakBest] ?: 0,
            lastDay = it[Keys.streakLastDay] ?: 0L
        )
    }

    val favourites: Flow<List<Drop>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.favourites] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<Drop>>(raw) }.getOrDefault(emptyList())
    }

    val notifyEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.notifyEnabled] ?: true }

    val notifyTime: Flow<Pair<Int, Int>> = context.dataStore.data.map {
        (it[Keys.notifyHour] ?: 8) to (it[Keys.notifyMinute] ?: 0)
    }

    suspend fun setInterests(ids: Set<String>) {
        context.dataStore.edit { it[Keys.interests] = ids }
    }

    suspend fun completeOnboarding() {
        context.dataStore.edit { it[Keys.onboarded] = true }
    }

    suspend fun setNotify(enabled: Boolean) {
        context.dataStore.edit { it[Keys.notifyEnabled] = enabled }
    }

    suspend fun setNotifyTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[Keys.notifyHour] = hour
            it[Keys.notifyMinute] = minute
        }
    }

    /**
     * Counts today as opened. Consecutive days grow the streak, a skipped day
     * resets it to one, and opening twice on the same day changes nothing.
     */
    suspend fun registerOpen(today: LocalDate = LocalDate.now()): Streak {
        val epochDay = today.toEpochDay()
        var result = Streak(0, 0, epochDay)
        context.dataStore.edit { prefs ->
            val last = prefs[Keys.streakLastDay] ?: 0L
            val current = prefs[Keys.streakCurrent] ?: 0
            val best = prefs[Keys.streakBest] ?: 0
            val newCurrent = when {
                last == epochDay -> current.coerceAtLeast(1)
                last == epochDay - 1 -> current + 1
                else -> 1
            }
            val newBest = maxOf(best, newCurrent)
            prefs[Keys.streakCurrent] = newCurrent
            prefs[Keys.streakBest] = newBest
            prefs[Keys.streakLastDay] = epochDay
            result = Streak(newCurrent, newBest, epochDay)
        }
        return result
    }

    suspend fun toggleFavourite(drop: Drop): Boolean {
        var nowSaved = false
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.favourites]
                ?.let { runCatching { json.decodeFromString<List<Drop>>(it) }.getOrNull() }
                .orEmpty()
            val existing = current.firstOrNull { it.id == drop.id }
            val updated = if (existing != null) {
                current.filterNot { it.id == drop.id }
            } else {
                nowSaved = true
                listOf(drop.copy(savedAt = System.currentTimeMillis())) + current
            }
            prefs[Keys.favourites] = json.encodeToString(updated)
        }
        return nowSaved
    }

    suspend fun removeFavourite(id: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.favourites]
                ?.let { runCatching { json.decodeFromString<List<Drop>>(it) }.getOrNull() }
                .orEmpty()
            prefs[Keys.favourites] =
                json.encodeToString(current.filterNot { it.id == id })
        }
    }

    // ---- what has already been shown ---------------------------------------

    /**
     * Marks [ids] as delivered - they will not be picked again.
     *
     * The list is stored newline-joined rather than as a set so the oldest ids
     * can be dropped once it hits [SEEN_LIMIT], which at seven cards a day is
     * well past thirty years of use.
     */
    suspend fun markSeen(ids: Collection<String>) {
        if (ids.isEmpty()) return
        context.dataStore.edit { prefs ->
            val existing = prefs[Keys.seen]?.lineSequence()?.filter { it.isNotBlank() }?.toList()
                .orEmpty()
            val merged = (existing + ids).distinct()
            prefs[Keys.seen] = merged.takeLast(SEEN_LIMIT).joinToString("\n")
        }
    }

    // ---- today's feed, kept ready ------------------------------------------

    /**
     * Stores the feed of [day] so the next visit has nothing left to compute.
     * Written both by the feed screen and by the morning reminder.
     */
    suspend fun cacheFeed(day: LocalDate, drops: List<Drop>) {
        if (drops.isEmpty()) return
        context.dataStore.edit { prefs ->
            prefs[Keys.feedDay] = day.toEpochDay()
            prefs[Keys.feedJson] = json.encodeToString(drops)
        }
    }

    /**
     * Everything the feed needs, read in one go.
     *
     * DataStore reads the whole file per access, so asking for interests, the
     * seen list, the streak and the cached feed separately would parse it four
     * times before the first frame. This does it once.
     */
    suspend fun feedSnapshot(day: LocalDate): FeedSnapshot {
        val prefs = context.dataStore.data.first()
        val cached = if (prefs[Keys.feedDay] == day.toEpochDay()) {
            prefs[Keys.feedJson]
                ?.let { runCatching { json.decodeFromString<List<Drop>>(it) }.getOrNull() }
                ?.takeIf { it.isNotEmpty() }
        } else null
        return FeedSnapshot(
            interests = prefs[Keys.interests] ?: emptySet(),
            seen = prefs[Keys.seen]?.lineSequence()?.filter { it.isNotBlank() }?.toSet().orEmpty(),
            streak = Streak(
                current = prefs[Keys.streakCurrent] ?: 0,
                best = prefs[Keys.streakBest] ?: 0,
                lastDay = prefs[Keys.streakLastDay] ?: 0L
            ),
            cachedFeed = cached
        )
    }

    private companion object {
        const val SEEN_LIMIT = 80_000
    }
}
