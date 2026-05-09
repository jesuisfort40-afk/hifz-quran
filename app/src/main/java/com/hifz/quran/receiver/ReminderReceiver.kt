package com.hifz.quran.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.hifz.quran.MainActivity
import com.hifz.quran.R
import com.hifz.quran.util.ReminderManager

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "hifz_reminder_channel"
        const val EXTRA_LABEL = "reminder_label"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Révision Coran"
        showNotification(context, label)
    }

    private fun showNotification(context: Context, label: String) {
        createChannel(context)
        val mainIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quran)
            .setContentTitle("🕌 Hifz Quran")
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(mainIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, "Rappels Hifz", NotificationManager.IMPORTANCE_HIGH
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
