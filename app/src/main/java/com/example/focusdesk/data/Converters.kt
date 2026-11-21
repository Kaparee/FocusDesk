package com.example.focusdesk.data

import androidx.room.TypeConverter
import com.example.focusdesk.data.model.SessionStatus
import com.example.focusdesk.data.model.DndRuleMode

class Converters {
    @TypeConverter
    fun fromSessionStatus(status: SessionStatus): String { return status.name }

    @TypeConverter
    fun toSessionStatus(value: String): SessionStatus { return SessionStatus.valueOf(value) }

    @TypeConverter
    fun fromDndRuleMode(mode: DndRuleMode): String { return mode.name }

    @TypeConverter
    fun toDndRuleMode(value: String): DndRuleMode { return DndRuleMode.valueOf(value) }
}