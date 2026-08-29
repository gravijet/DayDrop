package com.gravijet.daydrop.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gravijet.daydrop.data.local.ContentRepository
import com.gravijet.daydrop.data.model.Drop
import com.gravijet.daydrop.data.prefs.Streak
import com.gravijet.daydrop.data.prefs.UserPrefs
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
    val loading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val drops: List<Drop> = emptyList(),
    val streak: Streak = Streak(0, 0, 0)
)

class FeedViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = UserPrefs(app)
    private val generator = DropGenerator(ContentRepository(app))

    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()

    val favouriteIds: StateFlow<Set<String>> = prefs.favourites
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var loadedFor: LocalDate? = null

    /** Safe to call on every resume - it only does work when the day changed. */
    fun load(force: Boolean = false) {
        val today = LocalDate.now()
        if (!force && loadedFor == today) return
        loadedFor = today
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, date = today)
            val streak = prefs.registerOpen(today)
            val drops = generator.buildFeed(today, prefs.currentInterests())
            _state.value = FeedState(
                loading = false,
                date = today,
                drops = drops,
                streak = streak
            )
        }
    }

    fun toggleFavourite(drop: Drop) {
        viewModelScope.launch { prefs.toggleFavourite(drop) }
    }
}
