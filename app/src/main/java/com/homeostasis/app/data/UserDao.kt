package com.homeostasis.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.homeostasis.app.data.model.Task
import com.homeostasis.app.data.model.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM User WHERE id = :userId AND householdGroupId = :householdGroupId")
    fun getUserByIdFlow(userId: String, householdGroupId: String): Flow<User>

    @Query("SELECT * FROM User WHERE id = :userId AND householdGroupId = :householdGroupId")
    suspend fun getUserById(userId: String, householdGroupId: String): User? // Change to suspend fun and User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: User)

    @Delete
    suspend fun deleteUser(user: User)

    /**
     * Gets a flow of users that need to be synchronized to the remote database.
     *
     * @param householdGroupId The ID of the household group.
     * @return A Flow emitting a list of users needing sync.
     */
    @Query("SELECT * FROM User WHERE needsSync = 1 AND householdGroupId = :householdGroupId")
    fun getUsersRequiringSync(householdGroupId: String): Flow<List<User>>

    /**
     * Updates the user's profile information in the local database and marks it for sync.
     *
     * @param userId The ID of the user to update.
     * @param name The new name for the user.
     * @param profileImageUrl The new local profile image path for the user.
     */
    suspend fun updateUserProfileLocal(userId: String, name: String, profileImageUrl: String) {
        // Retrieve the existing user to preserve other fields
        val existingUser = getUserById(userId, "") // TODO: Need householdGroupId here

        existingUser?.let {
            val updatedUser = it.copy(
                name = name,
                profileImageUrl = profileImageUrl,
                needsSync = true // Mark for sync
            )
            upsertUser(updatedUser)
        }
        // TODO: Handle case where user is not found locally?
    }
}