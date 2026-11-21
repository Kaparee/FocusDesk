package com.example.focusdesk.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tagUid: String,
    val profileName: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val plannedDurationSeconds: Int,
    val actualDurationSeconds: Int,
    val status: SessionStatus
)
