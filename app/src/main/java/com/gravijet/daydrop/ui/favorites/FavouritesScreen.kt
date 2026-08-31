package com.gravijet.daydrop.ui.favorites

import android.app.Application
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gravijet.daydrop.data.model.Drop
import com.gravijet.daydrop.data.prefs.UserPrefs
import com.gravijet.daydrop.share.StoryCardRenderer
import com.gravijet.daydrop.ui.common.openUrl
import com.gravijet.daydrop.ui.feed.DropCard
import com.gravijet.daydrop.ui.theme.Chalk
import com.gravijet.daydrop.ui.theme.ChalkDim
import com.gravijet.daydrop.ui.theme.Ink
import com.gravijet.daydrop.ui.theme.InkElevated
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FavouritesViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = UserPrefs(app)

    val favourites: StateFlow<List<Drop>> = prefs.favourites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remove(id: String) {
        viewModelScope.launch { prefs.removeFavourite(id) }
    }
}

@Composable
fun FavouritesScreen(
    onBack: () -> Unit,
    viewModel: FavouritesViewModel = viewModel()
) {
    val favourites by viewModel.favourites.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 14.dp, end = 22.dp, top = 14.dp, bottom = 6.dp),
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
            Column {
                Text(
                    "Gespeichert",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Chalk
                )
                Text(
                    text = when (favourites.size) {
                        0 -> "Noch nichts hier"
                        1 -> "1 Drop"
                        else -> "${favourites.size} Drops"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = ChalkDim
                )
            }
        }

        if (favourites.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(40.dp)
                ) {
                    Text("🔖", fontSize = 46.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Tippe im Feed auf Speichern, und der Drop landet hier.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChalkDim
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.navigationBarsPadding()
            ) {
                items(favourites, key = { it.id }) { drop ->
                    Column {
                        DropCard(
                            drop = drop,
                            compact = true,
                            saved = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                            onOpenSource = { openUrl(context, it) }
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, start = 4.dp, end = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            SmallAction(Icons.Rounded.IosShare, "Teilen") {
                                scope.launch { StoryCardRenderer.share(context, drop) }
                            }
                            SmallAction(Icons.Rounded.DeleteOutline, "Entfernen") {
                                viewModel.remove(drop.id)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 6.dp)
    ) {
        Icon(icon, label, tint = ChalkDim, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = ChalkDim)
    }
}
