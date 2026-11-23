package com.example.focusdesk.ui

enum class FocusStatus {
    IDLE,
    FOCUS_ACTIVE,
    BREAK_ACTIVE,
    WAITING_TO_RESUME,
    ABORTED
}

data class FocusUiState(
    val status: FocusStatus = FocusStatus.IDLE,
    val remainingTimeSeconds: Long = 0L,
    val totalDurationSeconds: Long = 0L,
    val profileName: String = "",
    val currentCycle: Int = 1,
    val maxCycles: Int = 1,
    val isEmergencyBreak: Boolean = false,
    val overtimeSeconds: Long = 0L) {

}