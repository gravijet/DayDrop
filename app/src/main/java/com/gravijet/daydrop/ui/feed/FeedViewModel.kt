package com.gravijet.daydrop.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gravijet.daydrop.data.local.ContentRepository
import com.gravijet.daydrop.data.model.Drop
import com.gravijet.daydrop.data.model.Slot
import com.gravijet.daydrop.data.prefs.Streak
import com.gravijet.daydrop.data.prefs.UserPrefs
import com.gravijet.daydrop.domain.CARDS_PER_DAY
import com.gravijet.daydrop.domain.DropGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class FeedState(
    /** False only for the handful of frames before the first feed is ready. */
    val ready: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val drops: List<Drop> = emptyList(),
    val streak: Streak = Streak(0, 0, 0),
    /** True while Wikipedia is still being asked for the live parts. */
    val enriching: Boolean = false
)

class FeedViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = UserPrefs(app)
    private val content = ContentRepository.get(app)
    private val generator = DropGenerator(content)

    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()

    val favouriteIds: StateFlow<Set<String>> = prefs.favourites
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var loadedFor: LocalDate? = null

    /**
     * Puts a full feed on screen straight away and improves it afterwards.
     *
     * Three steps, and only the first one is on the critical path:
     *  1. today's feed, if it was already built (last visit or this morning's
     *     reminder) - nothing to compute, nothing to fetch;
     *  2. otherwise the offline feed from the bundled JSON, which is a few list
     *     operations over data the app already has in memory;
     *  3. Wikipedia in the background, swapped in slot by slot as it arrives.
     *
     * Safe to call on every resume: it only does work when the day changed.
     */
    fun load(force: Boolean = false) {
        val today = LocalDate.now()
        if (!force && loadedFor == today) return
        loadedFor = today

        viewModelScope.launch {
            val snapshot = prefs.feedSnapshot(today)
            val cached = snapshot.cachedFeed
            val shown = cached ?: generator.buildOffline(today, snapshot.interests, snapshot.seen)

            // On screen before anything is written back to disk.
            _state.value = FeedState(
                ready = true,
                date = today,
                drops = shown,
                streak = snapshot.streak,
                enriching = cached == null || shown.size < CARDS_PER_DAY
            )

            // Two of the five cards are provisional: enrich() replaces the
            // bundled stand-ins with the real event of the day and Wikipedia's
            // Artikel des Tages. They are not counted as shown until we know
            // they survived - otherwise every day would burn two curated cards
            // even though only one of them was ever read.
            if (cached == null) {
                prefs.markSeen(shown.filterNot { it.slot in Slot.PROVISIONAL }.map { it.id })
                prefs.cacheFeed(today, shown)
            } else {
                // A cached feed was on screen for real, provisional slots and
                // all - so everything in it counts as shown, even if yesterday's
                // enrichment never got to finish.
                prefs.markSeen(shown.map { it.id })
            }
            _state.value = _state.value.copy(streak = prefs.registerOpen(today))

            if (cached != null && shown.size >= CARDS_PER_DAY) return@launch

            val enriched = runCatching { generator.enrich(today, shown, snapshot.seen) }
                .getOrDefault(shown)
                .ifEmpty { shown }
            prefs.markSeen(enriched.map { it.id })
            prefs.cacheFeed(today, enriched)
            _state.value = _state.value.copy(drops = enriched, enriching = false)
        }
    }

    fun toggleFavourite(drop: Drop) {
        viewModelScope.launch { prefs.toggleFavourite(drop) }
    }
}
