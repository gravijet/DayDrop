package com.gravijet.daydrop.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gravijet.daydrop.data.model.Interest
import com.gravijet.daydrop.R
import com.gravijet.daydrop.ui.theme.Chalk
import com.gravijet.daydrop.ui.theme.ChalkDim
import com.gravijet.daydrop.ui.theme.Ink
import com.gravijet.daydrop.ui.theme.InkBorder
import com.gravijet.daydrop.ui.theme.InkElevated

private const val MIN_INTERESTS = 3

/**
 * Two beats: what the app is, then what you care about. The picked topics feed
 * the three personalised cards in every daily drop.
 */
@Composable
fun OnboardingScreen(onDone: (Set<String>) -> Unit) {
    var step by rememberSaveable { mutableStateOf(0) }
    val selected = remember { mutableStateOf(setOf<String>()) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* The daily reminder is nice to have, not required. */ }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        // A real editorial image gives the first launch a sense of discovery;
        // the dark lower third was composed as quiet space for this exact copy.
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.onboarding_atlas),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Ink.copy(alpha = 0.10f),
                    0.42f to Ink.copy(alpha = 0.24f),
                    0.72f to Ink.copy(alpha = 0.84f),
                    1f to Ink
                )
            )
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 26.dp)
                .widthIn(max = 620.dp)
                .align(Alignment.Center)
        ) {
            if (step == 0) WelcomeStep(Modifier.weight(1f)) else InterestStep(
                selected = selected.value,
                onToggle = { id ->
                    selected.value = if (id in selected.value) selected.value - id
                    else selected.value + id
                },
                modifier = Modifier.weight(1f)
            )

            val enabled = step == 0 || selected.value.size >= MIN_INTERESTS
            PrimaryButton(
                label = when {
                    step == 0 -> "Los geht's"
                    selected.value.size < MIN_INTERESTS ->
                        "Noch ${MIN_INTERESTS - selected.value.size} wählen"
                    else -> "Fertig · ${selected.value.size} Themen"
                },
                enabled = enabled,
                onClick = {
                    if (step == 0) {
                        step = 1
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        onDone(selected.value)
                    }
                }
            )
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun WelcomeStep(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(Chalk.copy(alpha = 0.12f))
                .border(1.dp, Chalk.copy(alpha = 0.24f), CircleShape)
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(
                "DEIN TÄGLICHER WISSENSMOMENT",
                style = MaterialTheme.typography.labelLarge,
                color = Chalk.copy(alpha = 0.9f)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text("💧", fontSize = 68.sp)
        Spacer(Modifier.height(22.dp))
        Text(
            text = "Jeden Tag\nein Drop.",
            style = MaterialTheme.typography.displayLarge,
            fontSize = 52.sp,
            color = Chalk
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Sieben Karten, ein paar Minuten: kuriose Tage, " +
                "Ereignisse von heute vor X Jahren, Fakten, die hängen bleiben, " +
                "und eine Frage, bei der du erst raten musst.",
            style = MaterialTheme.typography.bodyLarge,
            color = ChalkDim
        )
        Spacer(Modifier.height(30.dp))
        listOf(
            "📅" to "Was ist heute für ein Tag?",
            "⏳" to "Was ist heute vor X Jahren passiert?",
            "🤯" to "Fakten, die du weitererzählen willst",
            "🔥" to "Eine Streak, die du nicht reißen willst"
        ).forEach { (emoji, line) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                Text(emoji, fontSize = 20.sp)
                Spacer(Modifier.width(14.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium, color = Chalk)
            }
        }
    }
}

@Composable
private fun InterestStep(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(34.dp))
        Text(
            text = "Was interessiert dich?",
            style = MaterialTheme.typography.displayMedium,
            color = Chalk
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Eine der sieben Karten pro Tag richtet sich nach deiner Auswahl. " +
                "Du kannst das jederzeit ändern.",
            style = MaterialTheme.typography.bodyMedium,
            color = ChalkDim
        )
        Spacer(Modifier.height(22.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(Interest.ALL, key = { it.id }) { interest ->
                InterestChip(
                    interest = interest,
                    selected = interest.id in selected,
                    onClick = { onToggle(interest.id) }
                )
            }
        }
    }
}

@Composable
fun InterestChip(interest: Interest, selected: Boolean, onClick: () -> Unit) {
    val borderAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.35f,
        animationSpec = tween(180),
        label = "chipBorder"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Chalk.copy(alpha = 0.10f) else InkElevated)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) Chalk.copy(alpha = borderAlpha)
                else InkBorder.copy(alpha = borderAlpha),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 16.dp)
    ) {
        Text(interest.emoji, fontSize = 19.sp)
        Spacer(Modifier.width(11.dp))
        Text(
            text = interest.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Chalk else ChalkDim
        )
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.35f,
        animationSpec = tween(180),
        label = "buttonAlpha"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Chalk.copy(alpha = alpha))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
    }
}
