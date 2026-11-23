package com.example.focusdesk.system.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.app.NotificationChannel
import android.app.NotificationManager
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
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "ACTION_SESSION_FINISHED") return
        val pendingResult = goAsync()
        DndManager(context).disableDnd()
        val success = Intent("ACTION_SESSION_COMPLETED_SUCCESS")
        success.`package` = context.packageName
        context.sendBroadcast(success)

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
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (!vibrator.hasVibrator()) return

//        CoroutineScope(Dispatchers.IO).launch {
//            for (i in 1..4) {
//                val effect = VibrationEffect.createOneShot(800L, 255)
//                vibrator.vibrate(effect)
//                delay(400)
//            }
//        }
            val timings = longArrayOf(800, 300, 240, 200, 240, 300, 2000)

            val amplitudes = intArrayOf(255, 0, 200, 0, 200, 0, 255)

            val effect =
                if (vibrator.hasAmplitudeControl()) {

                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                } else {

                    VibrationEffect.createWaveform(timings, -1)
                }
            if (effect != null) {
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
        }catch (e: Exception) {}

    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showBreakNotification(context: Context) {
        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "focusdesk_break_v2"

            val channel = NotificationChannel(
                channelId,
                "Przerwa w sesji",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.enableVibration(false)
            notificationManager.createNotificationChannel(channel)

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Czas na przerwę")
                .setContentText("FocusDesk kończy blok pracy")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context)
                .notify("focusdesk_break".hashCode(), notification)
        } catch (e: Exception) {

        }
    }
}