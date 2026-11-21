package com.example.focusdesk.data.dao

import androidx.room.*
import com.example.focusdesk.data.model.TagProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface TagProfileDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: TagProfile)

    @Update
    suspend fun updateProfile(profile: TagProfile)

    @Query("SELECT * FROM tag_profiles WHERE tagUid = :uid")
    suspend fun getProfileByUid(uid: String): TagProfile?

    @Query("SELECT * FROM tag_profiles ORDER BY name ASC")
    fun getAllProfiles(): Flow<List<TagProfile>>

    @Delete
    suspend fun deleteProfile(profile: TagProfile)
}