package com.example.focusdesk.data.dao

import androidx.room.*
import com.example.focusdesk.data.model.FocusSession
import com.example.focusdesk.data.model.SessionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDAO {
    @Insert
    suspend fun insertSession(session: FocusSession): Long

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): FocusSession?

    @Update
    suspend fun updateSession(session: FocusSession)

    @Query("SELECT * FROM focus_sessions ORDER BY startTimeMillis DESC")
    fun getAllSessions(): Flow<List<FocusSession>>

    @Query("SELECT * FROM focus_sessions WHERE startTimeMillis BETWEEN :fromMillis AND :toMillis ORDER BY startTimeMillis DESC")
    fun getSessionsBetween(fromMillis: Long, toMillis: Long): Flow<List<FocusSession>>

    @Query("UPDATE focus_sessions SET endTimeMillis = :endTimeMillis, actualDurationSeconds = :actualDurationSeconds, status = :status WHERE id = :sessionId ")
    suspend fun finishSession(
        sessionId: Long,
        endTimeMillis: Long,
        actualDurationSeconds: Int,
        status: SessionStatus
    )
}