package com.homeostasis.app.data

import androidx.room.Dao
import androidx.room.Query
import com.homeostasis.app.data.model.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM User WHERE id = :userId AND householdGroupId = :householdGroupId")
    fun getUserByIdFlow(userId: String, householdGroupId: String): Flow<User>

    @Query("SELECT * FROM User WHERE id = :userId AND householdGroupId = :householdGroupId")
    suspend fun getUserById(userId: String, householdGroupId: String): User? // Change to suspend fun and User?

}