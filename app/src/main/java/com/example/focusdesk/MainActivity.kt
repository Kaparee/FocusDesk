package com.example.focusdesk

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.focusdesk.nfc.NfcDeskPresenceManager
import com.example.focusdesk.system.DndManager
import com.example.focusdesk.system.FocusAlarmScheduler
import com.example.focusdesk.ui.FocusStatus
import com.example.focusdesk.ui.FocusViewModel
import com.example.focusdesk.ui.theme.FocusDeskTheme

class MainActivity : ComponentActivity() {
    private val viewModel: FocusViewModel by viewModels()

    lateinit var nfcManager: NfcDeskPresenceManager
    lateinit var dndManager: DndManager
    lateinit var alarmScheduler: FocusAlarmScheduler

    var notificationPermissionPromptShown = false
    var dndSettingsPromptShown = false
    var exactAlarmSettingsPromptShown = false
    var nfcSettingsPromptShown = false

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            notificationPermissionPromptShown = true
        }

    private val sessionCompletedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action == "ACTION_SESSION_COMPLETED_SUCCESS") {
                nfcManager.clearActiveSession()
                viewModel.onSessionCompleted()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        dndManager = DndManager(applicationContext)
        alarmScheduler = FocusAlarmScheduler(applicationContext)
        nfcManager = NfcDeskPresenceManager(
            activity = this,
            onTagScanned = { uid ->
                viewModel.onTagScanned(uid) { sessionId ->
                    nfcManager.setActiveSessionId(sessionId)
                }
            },
            onPhoneLiftedFromDesk = { _ ->
                nfcManager.clearActiveSession()
                viewModel.onPhoneLifted()
            },
            onSessionStarted = null
        )

        setContent {
            FocusDeskTheme {
                val state by viewModel.uiState.collectAsState()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (state.status == FocusStatus.FOCUS_ACTIVE) Color.Black else MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("FocusDesk")
                        if (state.status == FocusStatus.IDLE) Text("Status: Połóż telefon na tagu NFC")
                        if (state.status == FocusStatus.FOCUS_ACTIVE) {
                            val minutes = state.remainingTimeSeconds / 60
                            val seconds = state.remainingTimeSeconds % 60
                            Text("Profil: ${state.profileName}", color = Color.LightGray)
                            Text(
                                "Pozostało: ${"%02d:%02d".format(minutes, seconds)}",
                                color = Color.White, style = MaterialTheme.typography.displayLarge
                            )
                            Text(
                                "Blok ${state.currentCycle} z ${state.maxCycles}",
                                color = Color.Gray
                            )
                            Button(
                                onClick = {
                                    nfcManager.clearActiveSession()
                                    viewModel.onManualAbort()
                                }
                            ) {
                                Text(
                                    "Przerwij sesję",
                                    color = Color.Black,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        if (state.status == FocusStatus.BREAK_ACTIVE) {
                            val minutes = state.remainingTimeSeconds / 60
                            val seconds = state.remainingTimeSeconds % 60
                            Text("Czas na przerwę! Odpocznij.")
                            if (state.remainingTimeSeconds >= 10) {
                                Text(
                                    "Czas na relaks: ${"%02d:%02d".format(minutes, seconds)}",
                                    color = Color.LightGray, style = MaterialTheme.typography.displayLarge
                                )
                            } else {
                                Text(
                                    "PORA WRACAĆ DO PRACY: ${"%02d:%02d".format(minutes, seconds)}",
                                    color = Color.Red, style = MaterialTheme.typography.displayLarge
                                )
                            }
                        }
                        if (state.status == FocusStatus.ABORTED) Text(
                            "SESJA PRZERWANA - Telefon został podniesiony.",
                            color = Color.Red
                        )
                        if (state.status == FocusStatus.WAITING_TO_RESUME) {
                            Text("Przerwa minęła! Odłóż telefon na biurko, aby zacząć kolejny blok.")
                            if (state.overtimeSeconds > 0) {
                                val minutes = state.overtimeSeconds / 60
                                val seconds = state.overtimeSeconds % 60
                                if (minutes > 0) { Text("Spóźniasz się o: ${"%02d:%02d".format(minutes, seconds)}",
                                    color = Color.Red) }
                                else { Text("Spóźniasz się o: ${"%02d".format(seconds)} sekund",
                                    color = Color.Red)}
                                Text("Kara zostanie dopisana do następnego bloku!", color = Color.Yellow)
                            }
                        }
                    }
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
        if (nfcAdapter != null && !nfcAdapter.isEnabled) {
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

            if (dndManager.hasDndPermission() && alarmScheduler.canScheduleExactAlarms()) {
                nfcManager.startScanning()
            }
        }

        ContextCompat.registerReceiver(
            this,
            sessionCompletedReceiver,
            IntentFilter("ACTION_SESSION_COMPLETED_SUCCESS"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        nfcManager.stopScanning()
        super.onPause()
        try {
            unregisterReceiver(sessionCompletedReceiver)
        } catch (_: Exception) {}
    }
}
