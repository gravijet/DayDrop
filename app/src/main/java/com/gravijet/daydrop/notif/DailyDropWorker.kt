package com.gravijet.daydrop.notif

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gravijet.daydrop.MainActivity
import com.gravijet.daydrop.R
import com.gravijet.daydrop.data.local.ContentRepository
import com.gravijet.daydrop.data.model.Drop
import com.gravijet.daydrop.data.model.DropType
import com.gravijet.daydrop.data.prefs.UserPrefs
import com.gravijet.daydrop.domain.DropGenerator
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private const val CHANNEL_ID = "daily_drop"
private const val NOTIFICATION_ID = 1001
const val DAILY_WORK_NAME = "daily_drop_reminder"

/**
 * Builds the morning nudge from the drop the user is actually about to get -
 * enough to make them curious, never enough to spoil the card.
 */
class DailyDropWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = UserPrefs(applicationContext)
        if (!prefs.notifyEnabled.first()) return Result.success()

        val drops = runCatching {
            DropGenerator(ContentRepository(applicationContext))
                .buildFeed(LocalDate.now(), prefs.currentInterests())
        }.getOrDefault(emptyList())

        notify(teaserTitle(drops), teaserBody(drops))
        return Result.success()
    }

    private fun teaserTitle(drops: List<Drop>): String {
        val today = drops.firstOrNull { it.type == DropType.TODAY_IS }
        return when {
            today != null -> "Heute ist ${today.title} 👀"
            else -> "Dein Drop für heute ist da 👀"
        }
    }

    private fun teaserBody(drops: List<Drop>): String {
        val parts = mutableListOf<String>()
        drops.firstOrNull { it.type == DropType.HISTORY }?.let { history ->
            val years = LocalDate.now().year - (history.title.toIntOrNull() ?: return@let)
            if (years > 0) parts += "Und was ist heute vor $years Jahren passiert? " +
                "Steht auf Karte zwei."
        }
        drops.firstOrNull { it.type == DropType.QUIZ }?.let {
            parts += "Und eine Frage wartet, bei der die meisten falsch liegen."
        }
        return parts.firstOrNull() ?: "Acht Karten, ein bis zwei Minuten."
    }

    private fun notify(title: String, body: String) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.channel_daily),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = applicationContext.getString(R.string.channel_daily_desc)
            }
        )

        val granted = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU
        if (!granted) return

        val intent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()

        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIFICATION_ID, notification)
        }
    }
}

object DailyDropScheduler {

    /** Schedules (or reschedules) the reminder for the next [hour]:[minute]. */
    fun schedule(context: Context, hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        var next = now.with(LocalTime.of(hour, minute))
        if (!next.isAfter(now)) next = next.plusDays(1)

        val request = PeriodicWorkRequestBuilder<DailyDropWorker>(Duration.ofDays(1))
            .setInitialDelay(Duration.between(now, next))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_WORK_NAME)
    }
}
