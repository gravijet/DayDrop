package com.gravijet.daydrop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gravijet.daydrop.data.prefs.UserPrefs
import com.gravijet.daydrop.notif.DailyDropScheduler
import com.gravijet.daydrop.ui.favorites.FavouritesScreen
import com.gravijet.daydrop.ui.feed.FeedScreen
import com.gravijet.daydrop.ui.onboarding.OnboardingScreen
import com.gravijet.daydrop.ui.settings.SettingsScreen
import com.gravijet.daydrop.ui.theme.DayDropTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private object Routes {
    const val ONBOARDING = "onboarding"
    const val FEED = "feed"
    const val FAVOURITES = "favourites"
    const val SETTINGS = "settings"
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hold the splash until we know whether to open onboarding or the feed,
        // so the app never flashes the wrong screen for a frame.
        var ready = false
        splash.setKeepOnScreenCondition { !ready }

        val prefs = UserPrefs(this)

        setContent {
            DayDropTheme {
                val onboarded by prefs.onboarded.collectAsState(initial = null)
                LaunchedEffect(onboarded) { if (onboarded != null) ready = true }
                onboarded?.let { DayDropNav(startOnboarding = !it) }
            }
        }
    }
}

@Composable
private fun DayDropNav(startOnboarding: Boolean) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = UserPrefs(context)

    // Re-arm the reminder for anyone who already finished onboarding, e.g. after
    // a reboot or an app update cleared the queued work.
    LaunchedEffect(startOnboarding) {
        if (!startOnboarding && prefs.notifyEnabled.first()) {
            val (hour, minute) = prefs.notifyTime.first()
            DailyDropScheduler.schedule(context, hour, minute)
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (startOnboarding) Routes.ONBOARDING else Routes.FEED,
        enterTransition = { slideInHorizontally(tween(280)) { it / 8 } + fadeIn(tween(280)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { slideOutHorizontally(tween(260)) { it / 8 } + fadeOut(tween(200)) }
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onDone = { interests ->
                scope.launch {
                    prefs.setInterests(interests)
                    prefs.completeOnboarding()
                    val (hour, minute) = prefs.notifyTime.first()
                    DailyDropScheduler.schedule(context, hour, minute)
                    navController.navigate(Routes.FEED) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            })
        }
        composable(Routes.FEED) {
            FeedScreen(
                onOpenFavourites = { navController.navigate(Routes.FAVOURITES) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.FAVOURITES) {
            FavouritesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
