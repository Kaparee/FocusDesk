package com.example.focusdesk.data.model
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tag_profiles")
data class TagProfile(
    @PrimaryKey val tagUid: String,
    val name: String,
    val workDurationMinutes: Int,
    val breakDurationMinutes: Int,
    val dndMode: DndRuleMode,
    val muteMedia: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
