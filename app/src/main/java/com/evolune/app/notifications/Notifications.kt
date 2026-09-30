package com.evolune.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.evolune.app.MainActivity
import com.evolune.app.domain.ScheduleRules
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object NotificationChannels {
    const val DAILY = "daily_guidance"
    const val QUESTS = "quest_reminders"
    const val REVIEWS = "reviews"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(listOf(
            NotificationChannel(DAILY, "Daily guidance", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Morning briefing and daily mission reminders" },
            NotificationChannel(QUESTS, "Quest reminders", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Quest, ritual and milestone reminders" },
            NotificationChannel(REVIEWS, "Reviews", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Evening and weekly review reminders" }
        ))
    }
}

class ReminderWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        val title = inputData.getString("title") ?: "Evolune"
        val body = inputData.getString("body") ?: "Your next step is ready."
        val channel = inputData.getString("channel") ?: NotificationChannels.DAILY
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val intent = PendingIntent.getActivity(
            applicationContext, title.hashCode(), Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(applicationContext).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        return Result.success()
    }
}

object NotificationScheduler {
    fun scheduleDaily(context: Context, key: String, hour: Int, title: String, body: String, channel: String) {
        val now = ZonedDateTime.now()
        val next = ScheduleRules.nextDaily(now, hour)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(ScheduleRules.delayMillis(now, next), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("title" to title, "body" to body, "channel" to channel))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(key, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun scheduleWeekly(context: Context, hour: Int = 18) {
        val now = ZonedDateTime.now()
        val next = ScheduleRules.nextWeekly(now, hour = hour)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(ScheduleRules.delayMillis(now, next), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("title" to "Weekly Review", "body" to "Look back, learn, and shape the next week.", "channel" to NotificationChannels.REVIEWS))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("weekly_review", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun scheduleQuest(context: Context, questId: Long, title: String, triggerAt: Long) {
        val delay = (triggerAt - System.currentTimeMillis()).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("title" to "Quest reminder", "body" to title, "channel" to NotificationChannels.QUESTS))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("quest_$questId", ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelDaily(context: Context, key: String) = WorkManager.getInstance(context).cancelUniqueWork(key)
    fun cancelWeekly(context: Context) = WorkManager.getInstance(context).cancelUniqueWork("weekly_review")
}
