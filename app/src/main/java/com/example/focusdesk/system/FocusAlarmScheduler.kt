package com.example.focusdesk.system

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.os.Build
import android.provider.Settings
import com.example.focusdesk.system.receiver.FocusAlarmReceiver

class FocusAlarmScheduler(private val context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
    }

    fun openExactAlarmSettings() {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun scheduleSessionEnd(endTimeMillis: Long, sessionId: Long): Boolean {
        if (!canScheduleExactAlarms()) return false

        val intent = Intent(context, FocusAlarmReceiver::class.java)
        intent.action = "ACTION_SESSION_FINISHED"
        intent.putExtra("EXTRA_SESSION_ID", sessionId)
        val pendingIntent = PendingIntent.getBroadcast(context,
            sessionId.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTimeMillis, pendingIntent)
            return true
        } catch (e: SecurityException) {
            return false
        }
    }

    fun cancelSessionAlarm(sessionId: Long) {
        val intent = Intent(context, FocusAlarmReceiver::class.java)
        intent.action = "ACTION_SESSION_FINISHED"
        intent.putExtra("EXTRA_SESSION_ID", sessionId)
        val pendingIntent = PendingIntent.getBroadcast(context,
            sessionId.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(pendingIntent)
    }
}