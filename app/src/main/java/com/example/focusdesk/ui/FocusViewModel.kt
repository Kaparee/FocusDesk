package com.example.focusdesk.ui

import android.app.AlarmManager
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.focusdesk.data.FocusDeskDatabase
import com.example.focusdesk.data.FocusRepository
import com.example.focusdesk.data.model.DndRuleMode
import com.example.focusdesk.data.model.FocusSession
import com.example.focusdesk.data.model.SessionStatus
import com.example.focusdesk.data.model.TagProfile
import com.example.focusdesk.system.DndManager
import com.example.focusdesk.system.FocusAlarmScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.concurrent.timer

class FocusViewModel(application: Application) : AndroidViewModel(application) {
    private val context = getApplication<Application>().applicationContext
    private val database = FocusDeskDatabase.getDatabase(context)
    private val repository = FocusRepository(database.tagProfileDao(), database.focusSessionDao())
    private val dndManager = DndManager(context)
    private val alarmScheduler= FocusAlarmScheduler(context)
    private var activeSessionId: Long = -1L
    private var endTimeMillis: Long = -1L

    private var currentCycle: Int = 1
    private var activeProfile: TagProfile? = null
    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun onTagScanned(uid: String, onSessionCreated: (Long) -> Unit) {
        if (activeSessionId >0 || _uiState.value.status == FocusStatus.FOCUS_ACTIVE) return
        if (_uiState.value.status == FocusStatus.WAITING_TO_RESUME) {
            if (currentCycle < (activeProfile?.totalCycles ?: 4)) {
                val profile = activeProfile ?: return
                currentCycle++
                viewModelScope.launch { startWorkBlock(profile, onSessionCreated) }
                return
            }
            if (currentCycle >= (activeProfile?.totalCycles ?: 4)) {
                currentCycle = 1
                _uiState.value = _uiState.value.copy(status = FocusStatus.IDLE)
                return
            }
        }
        viewModelScope.launch {
            currentCycle = 1
            val existingProfile = repository.getProfile(uid)
            val profile = if (existingProfile == null) {
                val newProfile = TagProfile(
                    tagUid = uid,
                    name = "Biurko",
                    workDurationMinutes = 1,
                    breakDurationMinutes = 1,
                    dndMode = DndRuleMode.PRIORITY_ONLY,
                    muteMedia = true,
                    totalCycles = 2,
                )
                repository.saveProfile(newProfile)
                newProfile
            } else {
                existingProfile
            }

            activeProfile = profile
            startWorkBlock(profile, onSessionCreated)
        }
    }

    private suspend fun startWorkBlock(profile: TagProfile, onSessionCreated: (Long) -> Unit) {
        val now = System.currentTimeMillis()
        val penalty = _uiState.value.overtimeSeconds
        val durationSeconds = (profile.workDurationMinutes * 60L) + penalty
        endTimeMillis = now + (durationSeconds * 1000L)

        val session = FocusSession(
            tagUid = profile.tagUid,
            profileName = profile.name,
            startTimeMillis = now,
            endTimeMillis = endTimeMillis,
            plannedDurationSeconds = profile.workDurationMinutes * 60,
            actualDurationSeconds = 0,
            status = SessionStatus.ACTIVE
        )

        val sessionId = repository.recordSession(session)

        activeSessionId = sessionId
        activeProfile = profile

        dndManager.enableDnd(profile.dndMode, profile.muteMedia)
        alarmScheduler.scheduleSessionEnd(endTimeMillis, sessionId)

        _uiState.value = _uiState.value.copy(status = FocusStatus.FOCUS_ACTIVE, remainingTimeSeconds = durationSeconds, totalDurationSeconds = durationSeconds, profileName = profile.name, currentCycle = currentCycle, maxCycles = profile.totalCycles, overtimeSeconds = 0L)

        onSessionCreated(sessionId)

        startTimer()
    }

    private fun startTimer() {
        if (timerJob != null) timerJob?.cancel()

        timerJob = viewModelScope.launch {
            while(true){
                val remainingTime = maxOf(0L, (endTimeMillis - System.currentTimeMillis())/ 1000L)
                _uiState.value = _uiState.value.copy(remainingTimeSeconds = remainingTime)
                if (remainingTime <= 0L) {
                    onSessionCompleted()
                    break
                }
                delay(500)
            }
        }
    }

    fun onPhoneLifted() {
        if (_uiState.value.status != FocusStatus.FOCUS_ACTIVE) return
        timerJob?.cancel()
        alarmScheduler.cancelSessionAlarm(activeSessionId)
        dndManager.disableDnd()

        val fallbackSessionId = activeSessionId
        viewModelScope.launch {
            if (fallbackSessionId > 0) {
                repository.finishSession(fallbackSessionId, SessionStatus.ABORTED_PHONE_LIFTED)
            }
        }

        _uiState.value = _uiState.value.copy(status = FocusStatus.ABORTED, remainingTimeSeconds = 0L)
        activeSessionId = -1L
    }

    fun onSessionCompleted() {
        val breakSeconds = (activeProfile?.breakDurationMinutes ?: 5) * 60L
        activeSessionId = -1L
        dndManager.disableDnd()
        endTimeMillis = System.currentTimeMillis() + (breakSeconds * 1000L)
        _uiState.value = _uiState.value.copy(status = FocusStatus.BREAK_ACTIVE, remainingTimeSeconds = breakSeconds, totalDurationSeconds = breakSeconds)
        startBreakTimer()
    }

    private fun startBreakTimer() {
        if (timerJob != null) timerJob?.cancel()

        timerJob = viewModelScope.launch {
            while(true){
                val remainingTime = maxOf(0L, (endTimeMillis - System.currentTimeMillis())/ 1000L)
                _uiState.value = _uiState.value.copy(remainingTimeSeconds = remainingTime)
                if (remainingTime <= 0L) {
                    val breakEndMillis = System.currentTimeMillis()
                    _uiState.value = _uiState.value.copy(status = FocusStatus.WAITING_TO_RESUME)
                    while (_uiState.value.status == FocusStatus.WAITING_TO_RESUME) {
                        val overtime = (System.currentTimeMillis() - breakEndMillis) / 1000L
                        _uiState.value = _uiState.value.copy(overtimeSeconds = overtime)
                        delay(500)
                    }
                }
                delay(500)
            }
        }
    }

    fun onManualAbort() {
        if (_uiState.value.status != FocusStatus.FOCUS_ACTIVE) return
        timerJob?.cancel()
        alarmScheduler.cancelSessionAlarm(activeSessionId)
        dndManager.disableDnd()

        val fallbackSessionId = activeSessionId
        viewModelScope.launch {
            if (fallbackSessionId > 0) {
                repository.finishSession(fallbackSessionId, SessionStatus.ABORTED_MANUAL)
            }
        }

        _uiState.value = _uiState.value.copy(status = FocusStatus.ABORTED, remainingTimeSeconds = 0L)
        activeSessionId = -1L
    }
}