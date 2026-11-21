package com.example.focusdesk.data.dao

import androidx.room.*
import com.example.focusdesk.data.model.FocusSession
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDAO {
    @Insert
    suspend fun insertSession(session: FocusSession): Long

    @Query("SELECT * FROM focus_sessions ORDER BY startTimeMillis DESC")
    fun getAllSessions(): Flow<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions WHERE startTimeMillis >= :sinceMillis ORDER BY startTimeMillis DESC")
    fun getSessionsSince(sinceMillis: Long): Flow<List<FocusSession>>
}