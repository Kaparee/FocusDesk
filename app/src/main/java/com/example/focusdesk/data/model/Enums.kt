package com.example.focusdesk.data.model

enum class SessionStatus {
    ACTIVE,
    COMPLETED,
    ABORTED_PHONE_LIFTED,
    ABORTED_MANUAL
}

enum class DndRuleMode {
    PRIORITY_ONLY,
    TOTAL_SILENCE
}