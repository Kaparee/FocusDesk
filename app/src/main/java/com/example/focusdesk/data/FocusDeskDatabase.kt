package com.example.focusdesk.data

import androidx.room.*
import com.example.focusdesk.data.model.TagProfile
import com.example.focusdesk.data.model.FocusSession
import com.example.focusdesk.data.dao.TagProfileDAO
import com.example.focusdesk.data.dao.FocusSessionDAO
import com.example.focusdesk.data.Converters
import android.content.Context


@Database(entities = [TagProfile::class, FocusSession::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class FocusDeskDatabase : RoomDatabase() {
    abstract fun tagProfileDao(): TagProfileDAO
    abstract fun focusSessionDao(): FocusSessionDAO

    companion object {
        @Volatile
        private var INSTANCE: FocusDeskDatabase? = null

        fun getDatabase(context: Context): FocusDeskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FocusDeskDatabase::class.java,
                    "focus_desk_database"
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                return instance
            }
        }
    }
}