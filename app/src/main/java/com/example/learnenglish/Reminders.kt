package com.example.learnenglish

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.learnenglish.data.AppDatabase
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class ReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val today = LocalDate.now().toEpochDay()
        val due = AppDatabase.get(applicationContext).noteDao().countDue(today)
        Reminders.show(applicationContext, due)
        return Result.success()
    }
}

object Reminders {
    private const val CHANNEL_ID = "daily_review"
    private const val WORK_NAME = "daily_reminder"
    private const val NOTIFICATION_ID = 1001
    private const val PREFS = "reminder_prefs"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
    private const val DEFAULT_HOUR = 19

    fun getHour(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_HOUR, DEFAULT_HOUR)

    fun getMinute(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_MINUTE, 0)

    fun setTime(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_MINUTE, minute.coerceIn(0, 59))
            .apply()
        schedule(context, replace = true)
    }

    fun setHour(context: Context, hour: Int) {
        setTime(context, hour, getMinute(context))
    }

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pengingat belajar",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = "Pengingat latihan harian dan review catatan"
        manager.createNotificationChannel(channel)
    }

    fun schedule(context: Context, replace: Boolean = false) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(getHour(context), getMinute(context))
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        val delayMinutes = Duration.between(now, next).toMinutes()

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()

        val policy = if (replace) {
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
        } else {
            ExistingPeriodicWorkPolicy.KEEP
        }

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    fun showNow(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ReminderWorker>().build())
    }

    fun show(context: Context, dueCount: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (dueCount > 0) {
            "$dueCount catatan perlu direview hari ini."
        } else {
            "Waktunya latihan 10 menit. Satu langkah kecil sudah cukup."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Learn English Practice")
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }
}
