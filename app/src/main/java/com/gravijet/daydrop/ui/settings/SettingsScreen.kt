package com.gravijet.daydrop.ui.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gravijet.daydrop.data.model.Interest
import com.gravijet.daydrop.data.prefs.UserPrefs
import com.gravijet.daydrop.notif.DailyDropScheduler
import com.gravijet.daydrop.ui.onboarding.InterestChip
import com.gravijet.daydrop.ui.theme.Accent
import com.gravijet.daydrop.ui.theme.Chalk
import com.gravijet.daydrop.ui.theme.ChalkDim
import com.gravijet.daydrop.ui.theme.Ink
import com.gravijet.daydrop.ui.theme.InkBorder
import com.gravijet.daydrop.ui.theme.InkElevated
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = UserPrefs(app)

    val interests: StateFlow<Set<String>> = prefs.interests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val notifyEnabled: StateFlow<Boolean> = prefs.notifyEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val notifyTime: StateFlow<Pair<Int, Int>> = prefs.notifyTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 8 to 0)

    val streak = prefs.streak
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            com.gravijet.daydrop.data.prefs.Streak(0, 0, 0)
        )

    fun toggleInterest(id: String) {
        viewModelScope.launch {
            val current = interests.value
            prefs.setInterests(if (id in current) current - id else current + id)
        }
    }

    fun setNotify(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setNotify(enabled)
            val (h, m) = notifyTime.value
            if (enabled) DailyDropScheduler.schedule(app, h, m) else DailyDropScheduler.cancel(app)
        }
    }

    fun setHour(hour: Int) {
        viewModelScope.launch {
            prefs.setNotifyTime(hour, 0)
            if (notifyEnabled.value) DailyDropScheduler.schedule(app, hour, 0)
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val interests by viewModel.interests.collectAsState()
    val notify by viewModel.notifyEnabled.collectAsState()
    val (hour, _) = viewModel.notifyTime.collectAsState().value
    val streak by viewModel.streak.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 14.dp, end = 22.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(InkElevated)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack, "Zurück",
                    tint = ChalkDim, modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                "Einstellungen",
                style = MaterialTheme.typography.headlineMedium,
                color = Chalk
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(14.dp))
            StreakPanel(streak.current, streak.best)

            SectionTitle("Deine Themen")
            Text(
                "Die Karte \"Für dich\" richtet sich danach.",
                style = MaterialTheme.typography.bodyMedium,
                color = ChalkDim
            )
            Spacer(Modifier.height(14.dp))
            // Fixed height so the grid can live inside the scrolling column.
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                userScrollEnabled = false,
                modifier = Modifier.height(((Interest.ALL.size + 1) / 2 * 68).dp)
            ) {
                items(Interest.ALL, key = { it.id }) { interest ->
                    InterestChip(
                        interest = interest,
                        selected = interest.id in interests,
                        onClick = { viewModel.toggleInterest(interest.id) }
                    )
                }
            }

            SectionTitle("Tägliche Erinnerung")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(InkElevated)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Push am Morgen",
                        style = MaterialTheme.typography.titleMedium,
                        color = Chalk
                    )
                    Text(
                        "Ein Hinweis pro Tag, ohne zu verraten worum es geht.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChalkDim
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = notify,
                    onCheckedChange = viewModel::setNotify,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Ink,
                        checkedTrackColor = Accent,
                        uncheckedTrackColor = InkBorder
                    )
                )
            }

            if (notify) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Uhrzeit",
                    style = MaterialTheme.typography.labelMedium,
                    color = ChalkDim
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(6, 7, 8, 9, 12, 18).forEach { option ->
                        val active = option == hour
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(if (active) Chalk else InkElevated)
                                .clickable { viewModel.setHour(option) }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "%02d:00".format(option),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Ink else ChalkDim
                            )
                        }
                    }
                }
            }

            SectionTitle("Über DayDrop")
            Text(
                text = "Alle Daten bleiben auf diesem Gerät. Historische Ereignisse " +
                    "und Bilder kommen live von Wikipedia, alles andere ist fest in " +
                    "der App hinterlegt und funktioniert auch offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = ChalkDim
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(30.dp))
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = ChalkDim.copy(alpha = 0.7f)
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun StreakPanel(current: Int, best: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(InkElevated)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🔥", fontSize = 34.sp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (current == 1) "1 Tag in Folge" else "$current Tage in Folge",
                style = MaterialTheme.typography.titleMedium,
                color = Chalk
            )
            Text(
                text = "Rekord: $best",
                style = MaterialTheme.typography.bodyMedium,
                color = ChalkDim
            )
        }
    }
}
