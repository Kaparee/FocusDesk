package com.example.focusdesk

import android.os.Bundle
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.focusdesk.ui.theme.FocusDeskTheme
import com.example.focusdesk.data.FocusDeskDatabase
import com.example.focusdesk.data.FocusRepository
import com.example.focusdesk.nfc.NfcDeskPresenceManager
import com.example.focusdesk.system.DndManager
import com.example.focusdesk.system.FocusAlarmScheduler
import androidx.lifecycle.lifecycleScope
import com.example.focusdesk.data.model.TagProfile
import com.example.focusdesk.data.model.FocusSession
import com.example.focusdesk.data.model.DndRuleMode
import com.example.focusdesk.data.model.SessionStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    lateinit var repository: FocusRepository
    lateinit var nfcManager: NfcDeskPresenceManager
    lateinit var dndManager: DndManager
    lateinit var alarmScheduler: FocusAlarmScheduler
    var activeSessionId: Long = -1L
    var activeProfile: TagProfile? = null
    var activeSessionEndMillis: Long = 0L
    var activeSessionAlarmScheduled: Boolean = false
    var isStartingSession: Boolean = false
    var scanStatus = mutableStateOf("Czekam na przyłożenie taga NFC")
    var notificationPermissionPromptShown = false
    var dndSettingsPromptShown = false
    var exactAlarmSettingsPromptShown = false
    var nfcSettingsPromptShown = false

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationPermissionPromptShown = true
            scanStatus.value = if (granted) {
                "Powiadomienia: przyznano zgodę"
            } else {
                "Powiadomienia: nie przyznano. Aplikacja nadal może działać bez alertów."
            }
        }

    private val sessionCompletedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action == "ACTION_SESSION_COMPLETED_SUCCESS") {
                scanStatus.value = "Sesja zakończona sukcesem! Czas na przerwę."
                activeSessionId = -1L
                activeProfile = null
                activeSessionAlarmScheduled = false
                activeSessionEndMillis = 0L
                nfcManager.clearActiveSession()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = FocusDeskDatabase.getDatabase(applicationContext)
        val tagDAO = database.tagProfileDao()
        val sessionDAO = database.focusSessionDao()
        repository = FocusRepository(tagDAO, sessionDAO)
        dndManager = DndManager(applicationContext)
        alarmScheduler = FocusAlarmScheduler(applicationContext)
        nfcManager = NfcDeskPresenceManager(
            activity = this,
            onTagScanned = { uid ->
                if (!isStartingSession && activeSessionId < 1) {
                    isStartingSession = true
                    lifecycleScope.launch {
                    scanStatus.value = "Zeskanowano tag NFC\nUID: $uid"
                    val existingProfile = repository.getProfile(uid)
                    val profile = if (existingProfile == null) {
                        val newProfile = TagProfile(
                            tagUid = uid,
                            name = "Test",
                            workDurationMinutes = 1,
                            breakDurationMinutes = 1,
                            dndMode = DndRuleMode.PRIORITY_ONLY,
                            muteMedia = true,
                        )
                        repository.saveProfile(newProfile)
                        newProfile
                    } else {
                        existingProfile
                    }
                    activeProfile = profile

                    val nowMillis = System.currentTimeMillis()

                    val session = FocusSession(
                        tagUid = uid,
                        profileName = profile.name,
                        startTimeMillis = nowMillis,
                        endTimeMillis = nowMillis,
                        plannedDurationSeconds = profile.workDurationMinutes * 60,
                        actualDurationSeconds = 0,
                        status = SessionStatus.ACTIVE
                    )

                    val sessionId = repository.recordSession(session)

                    if (sessionId <= 0L) {
                        throw IllegalStateException("Nie udało się utworzyć sesji")
                    }

                    activeSessionId = sessionId
                    nfcManager.setActiveSessionId(sessionId)
                    activeSessionEndMillis =
                        nowMillis + profile.workDurationMinutes * 60L * 1000L

                    if (!alarmScheduler.canScheduleExactAlarms()) {
                        scanStatus.value =
                            "Zeskanowano tag NFC\nUID: $uid\nOtwieram ustawienia dokładnych alarmów"
                        alarmScheduler.openExactAlarmSettings()
                        isStartingSession = false
                        return@launch
                    }

                    dndManager.enableDnd(profile.dndMode, profile.muteMedia)
                    val alarmScheduled =
                        alarmScheduler.scheduleSessionEnd(activeSessionEndMillis, sessionId)
                    activeSessionAlarmScheduled = alarmScheduled
                    val parsedEndTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(activeSessionEndMillis)
                    scanStatus.value = if (alarmScheduled) {
                        "Zeskanowano tag NFC\nUID: $uid\nSesja rozpoczęta: ${profile.workDurationMinutes} min\nSesja zakończy się $parsedEndTime"
                    } else {
                        "Zeskanowano tag NFC\nUID: $uid\nNie udało się zaplanować alarmu"
                    }
                    isStartingSession = false
                    }
                }
            },
            onPhoneLiftedFromDesk = { sessionId ->
                try {
                    if (sessionId >0){
                        lifecycleScope.launch {
                            repository.finishSession(sessionId, SessionStatus.ABORTED_PHONE_LIFTED)
                            dndManager.disableDnd()
                            alarmScheduler.cancelSessionAlarm(sessionId)
                            nfcManager.clearActiveSession()
                            activeSessionEndMillis = 0L
                            activeSessionId = -1L
                            activeProfile = null
                            activeSessionAlarmScheduled = false
                            scanStatus.value = "Telefon został podniesiony z taga\nSesja przerwana"
                        }
                    }
                } catch (e: Exception) {
                    Log.e("FocusDesk", "Error", e)
                }
            },
            onSessionStarted = { sessionId ->
                activeSessionId = sessionId
                nfcManager.setActiveSessionId(sessionId)
                val profile = activeProfile
                if (!alarmScheduler.canScheduleExactAlarms()) {
                    alarmScheduler.openExactAlarmSettings()
                } else if (profile != null) {
                    dndManager.enableDnd(profile.dndMode, profile.muteMedia)
                }
            }
        )
        setContent {
            FocusDeskTheme {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("FocusDesk")
                    Text(scanStatus.value)
                }
            }
        }

        requestNotificationPermissionIfNeeded()
        openMissingPermissionSettingsIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (notificationPermissionPromptShown) return

        notificationPermissionPromptShown = true
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun openMissingPermissionSettingsIfNeeded() {
        if (!dndManager.hasDndPermission() && !dndSettingsPromptShown) {
            dndSettingsPromptShown = true
            dndManager.openDndSettings()
        } else if (
            !alarmScheduler.canScheduleExactAlarms() &&
            !exactAlarmSettingsPromptShown
        ) {
            exactAlarmSettingsPromptShown = true
            alarmScheduler.openExactAlarmSettings()
        }
    }

    override fun onResume() {
        super.onResume()

        val nfcAdapter = nfcManager.nfcAdapter
        if (nfcAdapter == null) {
            scanStatus.value = "Telefon nie posiada modułu NFC"
        } else if (!nfcAdapter.isEnabled) {
            scanStatus.value = "NFC jest wyłączone"
            if (!nfcSettingsPromptShown) {
                nfcSettingsPromptShown = true
                startActivity(
                    Intent(Settings.ACTION_NFC_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            }
        } else {
            nfcSettingsPromptShown = false
            openMissingPermissionSettingsIfNeeded()

            if (dndManager.hasDndPermission() &&
                alarmScheduler.canScheduleExactAlarms()
            ) {
                nfcManager.startScanning()
                scanStatus.value = "Czekam na przyłożenie taga NFC"
            }
        }

        val profile = activeProfile
        if (
            profile != null &&
            activeSessionId > 0 &&
            activeSessionEndMillis > 0 &&
            !activeSessionAlarmScheduled &&
            alarmScheduler.canScheduleExactAlarms()
        ) {
            dndManager.enableDnd(profile.dndMode, profile.muteMedia)
            activeSessionAlarmScheduled =
                alarmScheduler.scheduleSessionEnd(activeSessionEndMillis, activeSessionId)

            scanStatus.value = if (activeSessionAlarmScheduled) {
                "Zeskanowano tag NFC\nSesja rozpoczęta: ${profile.workDurationMinutes} min"
            } else {
                "Zeskanowano tag NFC\nNie udało się zaplanować alarmu"
            }
        }

        if (activeSessionAlarmScheduled == true && System.currentTimeMillis() >= activeSessionEndMillis) {
            val completedSessionId = activeSessionId
            activeSessionAlarmScheduled = false
            lifecycleScope.launch {
                repository.finishSession(completedSessionId, SessionStatus.COMPLETED)
                dndManager.disableDnd()
                alarmScheduler.cancelSessionAlarm(completedSessionId)
                nfcManager.clearActiveSession()
                activeSessionEndMillis = 0L
                activeSessionId = -1L
                activeProfile = null
                isStartingSession = false
                scanStatus.value = "Sesja zakończona. Czas na przerwę."
            }
        }

        ContextCompat.registerReceiver(this, sessionCompletedReceiver, IntentFilter("ACTION_SESSION_COMPLETED_SUCCESS"),
            ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        nfcManager.stopScanning()
        super.onPause()
        try { unregisterReceiver(sessionCompletedReceiver) } catch (e: Exception) {}
    }
}
