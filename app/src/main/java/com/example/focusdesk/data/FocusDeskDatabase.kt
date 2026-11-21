package com.example.focusdesk.data

import androidx.room.*
import com.example.focusdesk.data.model.TagProfile
import com.example.focusdesk.data.model.FocusSession
import com.example.focusdesk.data.dao.TagProfileDAO
import com.example.focusdesk.data.dao.FocusSessionDAO
import com.example.focusdesk.data.Converters

@Database(entities = [TagProfile::class, FocusSession::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class FocusDeskDatabase : RoomDatabase() {
    abstract fun tagProfileDao(): TagProfileDAO
    abstract fun focusSessionDao(): FocusSessionDAO
}