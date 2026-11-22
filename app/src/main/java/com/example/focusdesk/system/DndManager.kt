package com.example.focusdesk.system

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import com.example.focusdesk.data.model.DndRuleMode

class DndManager(private val context: Context) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val prefsName = "focus_desk_dnd_state"
    private val keyDndActive = "dnd_active"
    private val keyPreviousInterruptionFilter = "previous_interruption_filter"
    private val keyPreviousMediaVolume = "previous_media_volume"
    private val keyMediaMuted = "media_muted"

    private val preferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun hasDndPermission(): Boolean { return notificationManager.isNotificationPolicyAccessGranted }

    fun openDndSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        context.startActivity(intent)
    }

    fun enableDnd(dndMode: DndRuleMode, muteMedia: Boolean) {
        if (hasDndPermission()) {
            val dndActive = preferences.getBoolean(keyDndActive, false)

            if (!dndActive) {
                preferences.edit()
                    .putBoolean(keyDndActive, true)
                    .putInt(keyPreviousInterruptionFilter, notificationManager.currentInterruptionFilter)
                    .putBoolean(keyMediaMuted, muteMedia)
                    .apply()
            }

            if (muteMedia) {
                if (!preferences.contains(keyPreviousMediaVolume)) {
                    preferences.edit()
                        .putInt(keyPreviousMediaVolume, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
                        .putBoolean(keyMediaMuted, true)
                        .apply()
                }
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            }

            if (dndMode == DndRuleMode.PRIORITY_ONLY) notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            if (dndMode == DndRuleMode.TOTAL_SILENCE) notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        } else {
            return
        }
    }

    fun disableDnd() {
        if (!hasDndPermission()) return

        val dndActive = preferences.getBoolean(keyDndActive, false)
        val filter = preferences.getInt(
            keyPreviousInterruptionFilter,
            NotificationManager.INTERRUPTION_FILTER_ALL
        )
        val mediaMuted = preferences.getBoolean(keyMediaMuted, false)

        notificationManager.setInterruptionFilter(filter)

        if (mediaMuted && preferences.contains(keyPreviousMediaVolume)) {
            val previousVolume = preferences.getInt(keyPreviousMediaVolume, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                previousVolume, AudioManager.FLAG_SHOW_UI)
        }

        if (dndActive) {
            preferences.edit()
                .remove(keyDndActive)
                .remove(keyPreviousInterruptionFilter)
                .remove(keyPreviousMediaVolume)
                .remove(keyMediaMuted)
                .apply()
        }
    }
}