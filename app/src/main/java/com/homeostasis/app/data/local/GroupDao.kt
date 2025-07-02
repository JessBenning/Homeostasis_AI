package com.homeostasis.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.homeostasis.app.data.model.Group
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: Group)

    @Update
    suspend fun update(group: Group)

    @Delete
    suspend fun delete(group: Group)

    @Upsert
    suspend fun upsert(group: Group)

    @Query("SELECT * FROM `groups` WHERE id = :groupId")
    fun getGroupById(groupId: String): Flow<Group?>

    @Query("SELECT * FROM `groups`")
    fun getAllGroups(): Flow<List<Group>>

    @Query("SELECT * FROM `groups` WHERE needsSync = 1")
    fun getGroupsRequiringSync(): Flow<List<Group>>

    @Query("DELETE FROM `groups` WHERE id = :groupId")
    suspend fun deleteGroupById(groupId: String)

    @Query("DELETE FROM `groups`")
    suspend fun deleteAllGroups()
}