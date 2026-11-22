package com.example.focusdesk.data

import com.example.focusdesk.data.dao.FocusSessionDAO
import com.example.focusdesk.data.dao.TagProfileDAO
import com.example.focusdesk.data.model.FocusSession
import com.example.focusdesk.data.model.SessionStatus
import com.example.focusdesk.data.model.TagProfile
import kotlinx.coroutines.flow.Flow

class FocusRepository(private val tagProfileDAO: TagProfileDAO, private val focusSessionDAO: FocusSessionDAO) {
    val allProfiles: Flow<List<TagProfile>> = tagProfileDAO.getAllProfiles()
    val allSessions: Flow<List<FocusSession>> = focusSessionDAO.getAllSessions()

    suspend fun getProfile(uid: String): TagProfile? = tagProfileDAO.getProfileByUid(uid)
    suspend fun saveProfile(profile: TagProfile) = tagProfileDAO.insertProfile(profile)
    suspend fun updateProfile(profile: TagProfile) = tagProfileDAO.updateProfile(profile)
    suspend fun deleteProfile(profile: TagProfile) = tagProfileDAO.deleteProfile(profile)

    suspend fun recordSession(session: FocusSession) = focusSessionDAO.insertSession(session)
    fun getSessionSince(fromMillis: Long, toMillis: Long): Flow<List<FocusSession>> = focusSessionDAO.getSessionsBetween(fromMillis, toMillis)

    suspend fun finishSession(sessionId: Long, status: SessionStatus) {
        val session = focusSessionDAO.getSessionById(sessionId) ?: return
        val nowMillis = System.currentTimeMillis()
        val elapsedMillis = nowMillis - session.startTimeMillis
        val elapsedSeconds = elapsedMillis / 1000L
        val actualDurationSeconds = elapsedSeconds
            .coerceAtLeast(0L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        focusSessionDAO.finishSession(sessionId, nowMillis, actualDurationSeconds, status)
    }
}