package com.gravijet.daydrop.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gravijet.daydrop.data.model.Drop
import com.gravijet.daydrop.data.model.DropType
import com.gravijet.daydrop.data.model.Interest
import com.gravijet.daydrop.ui.theme.Chalk
import com.gravijet.daydrop.ui.theme.paletteFor

/**
 * One full-bleed story card. Image cards tint the photo with the card's own
 * gradient; text-only cards lean on an oversized glyph instead, so the feed
 * never looks like it is missing something.
 */
@Composable
fun DropCard(
    drop: Drop,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onOpenSource: (String) -> Unit = {}
) {
    val palette = paletteFor(drop.type)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(palette.brush)
    ) {
        drop.imageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.62f)
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to palette.bottom.copy(alpha = 0.15f),
                            0.35f to palette.bottom.copy(alpha = 0.55f),
                            1f to palette.bottom.copy(alpha = 0.97f)
                        )
                    )
            )
        } ?: Text(
            text = drop.type.emoji,
            fontSize = 260.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 46.dp, y = 18.dp)
                .alpha(0.10f)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp, vertical = 30.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Bottom
        ) {
            Eyebrow(drop)
            Spacer(Modifier.height(if (compact) 12.dp else 18.dp))

            if (drop.type == DropType.HISTORY) {
                Text(
                    text = drop.title,
                    style = MaterialTheme.typography.displayLarge,
                    fontSize = if (compact) 48.sp else 64.sp,
                    color = Chalk
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = drop.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Chalk.copy(alpha = 0.92f)
                )
            } else if (drop.quiz != null) {
                QuizBody(drop, compact)
            } else {
                Text(
                    text = drop.title,
                    style = if (compact) MaterialTheme.typography.headlineMedium
                    else MaterialTheme.typography.displayMedium,
                    color = Chalk
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = drop.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Chalk.copy(alpha = 0.90f)
                )
            }

            if (drop.topics.isNotEmpty() && !compact) {
                Spacer(Modifier.height(20.dp))
                TopicRow(drop.topics)
            }

            drop.sourceUrl?.let { url ->
                Spacer(Modifier.height(18.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenSource(url) }
                        .padding(vertical = 6.dp, horizontal = 2.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = Chalk.copy(alpha = 0.6f),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = drop.sourceLabel ?: "Quelle",
                        style = MaterialTheme.typography.labelMedium,
                        color = Chalk.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun Eyebrow(drop: Drop) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.28f))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = "${drop.type.emoji}  ${drop.eyebrow.uppercase()}",
                style = MaterialTheme.typography.labelLarge,
                color = Chalk
            )
        }
        if (drop.personalised) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.28f))
                    .padding(horizontal = 11.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "FÜR DICH",
                    style = MaterialTheme.typography.labelLarge,
                    color = Chalk.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun TopicRow(topics: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        topics.take(3).forEach { id ->
            Box(
                Modifier
                    .clip(CircleShape)
                    .border(1.dp, Chalk.copy(alpha = 0.28f), CircleShape)
                    .padding(horizontal = 11.dp, vertical = 5.dp)
            ) {
                Text(
                    text = Interest.labelFor(id),
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 12.sp,
                    color = Chalk.copy(alpha = 0.75f)
                )
            }
        }
    }
}

/** Answer first, explanation after - the card refuses to spoil itself. */
@Composable
private fun QuizBody(drop: Drop, compact: Boolean) {
    val quiz = drop.quiz ?: return
    var picked by rememberSaveable(drop.id) { mutableIntStateOf(-1) }

    Text(
        text = quiz.question,
        style = if (compact) MaterialTheme.typography.headlineMedium
        else MaterialTheme.typography.displayMedium,
        color = Chalk
    )
    Spacer(Modifier.height(22.dp))

    quiz.options.forEachIndexed { index, option ->
        val revealed = picked >= 0
        val isAnswer = index == quiz.answerIndex
        val alpha by animateFloatAsState(
            targetValue = if (!revealed || isAnswer || index == picked) 1f else 0.45f,
            animationSpec = tween(280),
            label = "optionAlpha"
        )
        val fill = when {
            !revealed -> Color.Black.copy(alpha = 0.24f)
            isAnswer -> Color(0xFF1FBF7A).copy(alpha = 0.85f)
            index == picked -> Color(0xFFE0483F).copy(alpha = 0.75f)
            else -> Color.Black.copy(alpha = 0.24f)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .alpha(alpha)
                .clip(RoundedCornerShape(16.dp))
                .background(fill)
                .border(1.dp, Chalk.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                .clickable(enabled = !revealed) { picked = index }
                .padding(horizontal = 16.dp, vertical = 15.dp)
        ) {
            Text(
                text = option,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Chalk,
                modifier = Modifier.weight(1f)
            )
            if (revealed && isAnswer) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = "Richtige Antwort",
                    tint = Chalk,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    AnimatedVisibility(
        visible = picked >= 0,
        enter = fadeIn(tween(320)) + expandVertically(tween(320))
    ) {
        Column {
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(PaddingValues(16.dp))
            ) {
                Column {
                    Text(
                        text = if (picked == quiz.answerIndex) "Richtig 🎉" else "Knapp daneben",
                        style = MaterialTheme.typography.labelLarge,
                        color = Chalk.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = quiz.explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Chalk.copy(alpha = 0.92f)
                    )
                }
            }
        }
    }
}
