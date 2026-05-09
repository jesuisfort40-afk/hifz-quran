package com.hifz.quran.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hifz.quran.model.Reminder
import com.hifz.quran.receiver.ReminderReceiver
import java.util.Calendar

object ReminderManager {

    private const val PREFS_KEY = "hifz_reminders"
    private const val KEY_REMINDERS = "reminders_json"
    private val gson = Gson()

    fun getReminders(context: Context): List<Reminder> {
        val prefs = context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_REMINDERS, null) ?: return defaultReminders()
        val type = object : TypeToken<List<Reminder>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { defaultReminders() }
    }

    fun saveReminders(context: Context, reminders: List<Reminder>) {
        val prefs = context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_REMINDERS, gson.toJson(reminders)).apply()
    }

    fun scheduleReminder(context: Context, reminder: Reminder) {
        if (!reminder.isEnabled) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        reminder.days.forEach { dayOfWeek ->
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, reminder.hour)
                set(Calendar.MINUTE, reminder.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.DAY_OF_WEEK, dayOfWeek)
                if (before(Calendar.getInstance())) add(Calendar.WEEK_OF_YEAR, 1)
            }
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra(ReminderReceiver.EXTRA_LABEL, reminder.label)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                reminder.id * 10 + dayOfWeek,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY * 7,
                pi
            )
        }
    }

    fun cancelReminder(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        reminder.days.forEach { dayOfWeek ->
            val intent = Intent(context, ReminderReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                reminder.id * 10 + dayOfWeek,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pi)
        }
    }

    fun rescheduleAll(context: Context) {
        getReminders(context).filter { it.isEnabled }.forEach { scheduleReminder(context, it) }
    }

    private fun defaultReminders() = listOf(
        Reminder(1, 6, 0, "🌅 Révision du matin", false),
        Reminder(2, 20, 0, "🌙 Révision du soir", false)
    )
}
