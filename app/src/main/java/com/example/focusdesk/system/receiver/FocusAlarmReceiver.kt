package com.example.focusdesk.system.receiver

import android.Manifest
import android.R
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.focusdesk.data.FocusRepository
import com.example.focusdesk.data.FocusDeskDatabase
import com.example.focusdesk.data.model.SessionStatus
import com.example.focusdesk.system.DndManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "ACTION_SESSION_FINISHED") return
        val pendingResult = goAsync()
        DndManager(context).disableDnd()
        triggerVibration(context)
        showBreakNotification(context)

        val sessionId = intent.getLongExtra("EXTRA_SESSION_ID", -1L)
        if (sessionId < 1) {
            pendingResult.finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FocusDeskDatabase.getDatabase(context)
                val repository = FocusRepository(
                    tagProfileDAO = db.tagProfileDao(),
                    focusSessionDAO = db.focusSessionDao()
                )
                repository.finishSession(sessionId, SessionStatus.COMPLETED)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun triggerVibration(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0L, 700L, 250L, 700L, 250L, 700L)
            val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0L, 700L, 250L, 700L, 250L, 700L), -1)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showBreakNotification(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "focusdesk_break"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Przerwa w sesji",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentTitle("Czas na przerwę")
            .setContentText("FocusDesk kończy blok pracy")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify("focusdesk_break".hashCode(), notification)
    }
}