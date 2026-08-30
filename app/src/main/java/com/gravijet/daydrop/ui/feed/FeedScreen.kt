package com.gravijet.daydrop.ui.feed

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gravijet.daydrop.data.model.Drop
import com.gravijet.daydrop.share.StoryCardRenderer
import com.gravijet.daydrop.ui.common.openUrl
import com.gravijet.daydrop.ui.theme.Chalk
import com.gravijet.daydrop.ui.theme.ChalkDim
import com.gravijet.daydrop.ui.theme.Ink
import com.gravijet.daydrop.ui.theme.InkBorder
import com.gravijet.daydrop.ui.theme.InkElevated
import com.gravijet.daydrop.ui.theme.paletteFor
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun FeedScreen(
    onOpenFavourites: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: FeedViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val favouriteIds by viewModel.favouriteIds.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.load() }

    // One page per drop, plus a closing page that wraps the day up.
    val pageCount = if (state.drops.isEmpty()) 0 else state.drops.size + 1
    val pagerState = rememberPagerState(pageCount = { pageCount })

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                dateLabel = state.date.format(
                    DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
                ),
                streak = state.streak.current,
                enriching = state.enriching,
                onOpenFavourites = onOpenFavourites,
                onOpenSettings = onOpenSettings
            )

            if (pageCount > 0) {
                StoryProgress(
                    total = state.drops.size,
                    current = pagerState.currentPage,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }

            when {
                // Nothing at all for the few frames before the feed is built -
                // an empty-state flash would be a lie.
                !state.ready -> Spacer(Modifier.weight(1f))
                state.drops.isEmpty() -> EmptyState(Modifier.weight(1f))
                else -> VerticalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    pageSpacing = 12.dp,
                    // A short flick is enough to turn the page - the default
                    // wants half a screen of travel before it commits.
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapPositionalThreshold = 0.15f
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 14.dp, vertical = 6.dp
                    )
                ) { page ->
                    // Cards behind the current one shrink back slightly.
                    val offset = ((pagerState.currentPage - page) +
                        pagerState.currentPageOffsetFraction).let { abs(it) }
                    val scale = 1f - (offset.coerceIn(0f, 1f) * 0.06f)

                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                alpha = 1f - offset.coerceIn(0f, 1f) * 0.35f
                            }
                    ) {
                        if (page < state.drops.size) {
                            DropCard(
                                drop = state.drops[page],
                                modifier = Modifier.fillMaxSize(),
                                onOpenSource = { openUrl(context, it) }
                            )
                        } else {
                            OutroCard(
                                streak = state.streak.current,
                                best = state.streak.best,
                                count = state.drops.size,
                                onOpenFavourites = onOpenFavourites
                            )
                        }
                    }
                }
            }

            if (pageCount > 0 && pagerState.currentPage < state.drops.size) {
                val drop = state.drops[pagerState.currentPage]
                ActionBar(
                    drop = drop,
                    saved = drop.id in favouriteIds,
                    onSave = { viewModel.toggleFavourite(drop) },
                    onShare = { scope.launch { StoryCardRenderer.share(context, drop) } },
                    onNext = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                )
            } else {
                Spacer(Modifier.height(74.dp).navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun TopBar(
    dateLabel: String,
    streak: Int,
    enriching: Boolean,
    onOpenFavourites: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 22.dp, end = 14.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "DayDrop",
                style = MaterialTheme.typography.headlineMedium,
                color = Chalk
            )
            Text(
                // The feed is complete and readable either way; this only says
                // that two of the cards may still swap themselves out.
                text = if (enriching) "$dateLabel · lädt noch" else dateLabel,
                style = MaterialTheme.typography.labelMedium,
                color = ChalkDim
            )
        }
        if (streak > 0) {
            StreakChip(streak)
            Spacer(Modifier.width(6.dp))
        }
        IconBubble(Icons.Rounded.Bookmark, "Gespeicherte Drops", onOpenFavourites)
        IconBubble(Icons.Rounded.Settings, "Einstellungen", onOpenSettings)
    }
}

@Composable
private fun StreakChip(streak: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color(0xFFFF6A3D).copy(alpha = 0.16f))
            .border(1.dp, Color(0xFFFF6A3D).copy(alpha = 0.45f), CircleShape)
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text("🔥", fontSize = 13.sp)
        Spacer(Modifier.width(5.dp))
        Text(
            text = "$streak",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFB08A)
        )
    }
}

@Composable
private fun IconBubble(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(InkElevated)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = ChalkDim, modifier = Modifier.size(19.dp))
    }
}

/** Segmented bar in the spirit of a story tray - shows how much of today is left. */
@Composable
private fun StoryProgress(total: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(total) { index ->
            val active = index <= current
            val alpha by animateFloatAsState(
                targetValue = if (active) 1f else 0.22f,
                animationSpec = tween(260),
                label = "segment"
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .alpha(alpha)
                    .background(Chalk)
            )
        }
    }
}

@Composable
private fun ActionBar(
    drop: Drop,
    saved: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val palette = paletteFor(drop.type)
        ActionButton(
            icon = if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
            label = if (saved) "Gespeichert" else "Speichern",
            tint = if (saved) palette.glow else ChalkDim,
            modifier = Modifier.weight(1f),
            onClick = onSave
        )
        ActionButton(
            icon = Icons.Rounded.IosShare,
            label = "Teilen",
            tint = ChalkDim,
            modifier = Modifier.weight(1f),
            onClick = onShare
        )
        // Turning the page without swiping at all.
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Chalk)
                .clickable(onClick = onNext),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Nächste Karte",
                tint = Ink,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(InkElevated)
            .border(1.dp, InkBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
private fun OutroCard(streak: Int, best: Int, count: Int, onOpenFavourites: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(32.dp))
            .background(InkElevated)
            .border(1.dp, InkBorder, RoundedCornerShape(32.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("🔥", fontSize = 62.sp)
            Spacer(Modifier.height(18.dp))
            Text(
                text = if (streak == 1) "Tag 1" else "$streak Tage in Folge",
                style = MaterialTheme.typography.displayMedium,
                color = Chalk
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Das war dein Drop mit $count Karten. " +
                    if (best > streak) "Dein Rekord steht bei $best Tagen."
                    else "Das ist dein bisheriger Rekord.",
                style = MaterialTheme.typography.bodyMedium,
                color = ChalkDim
            )
            Spacer(Modifier.height(26.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Chalk)
                    .clickable(onClick = onOpenFavourites)
                    .padding(horizontal = 22.dp, vertical = 13.dp)
            ) {
                Icon(
                    Icons.Rounded.FavoriteBorder, null,
                    tint = Ink, modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    "Gespeicherte Drops",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Morgen früh wartet der nächste Drop.",
                style = MaterialTheme.typography.labelMedium,
                color = ChalkDim.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(36.dp)
        ) {
            Icon(
                Icons.Rounded.KeyboardArrowDown, null,
                tint = ChalkDim, modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Für heute konnte kein Drop gebaut werden.",
                style = MaterialTheme.typography.bodyMedium,
                color = ChalkDim
            )
        }
    }
}
