package com.dallet.app.notify

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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dallet.app.AppStrings
import com.dallet.app.DalletApplication
import com.dallet.app.MainActivity
import com.dallet.app.R
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object DueReminders {
    private const val CHANNEL_ID = "due_reminders"
    private const val WORK_NAME = "due_reminders"
    private const val NOTIFICATION_ID = 1001
    private val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

    fun createChannel(context: Context, strings: AppStrings) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            strings.get(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** Checks for due operations once a day at about 09:00. */
    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var first = now.toLocalDate().atTime(REMINDER_TIME)
        if (!first.isAfter(now)) first = first.plusDays(1)
        val delay = Duration.between(now, first)
        val request = PeriodicWorkRequestBuilder<DueReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun notifyDue(context: Context, strings: AppStrings, count: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(strings.get(R.string.notif_title))
            .setContentText(strings.get(R.string.notif_text, count))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

class DueReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? DalletApplication ?: return Result.success()
        return try {
            val count = app.ledger.dueItems(LocalDate.now()).size
            if (count > 0) {
                DueReminders.notifyDue(applicationContext, app.strings, count)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
