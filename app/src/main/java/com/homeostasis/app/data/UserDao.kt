package com.homeostasis.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
// Removed: import com.homeostasis.app.data.model.Task
import com.homeostasis.app.data.model.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM User WHERE id = :userId AND householdGroupId = :householdGroupId")
    fun getUserByIdFlow(userId: String, householdGroupId: String): Flow<User?> // Changed to Flow<User?>

    @Query("SELECT * FROM User WHERE id = :userId AND householdGroupId = :householdGroupId")
    suspend fun getUserById(userId: String, householdGroupId: String): User?

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

    @Query("SELECT * FROM User WHERE householdGroupId = :householdGroupId")
    fun getAllUsersFlow(householdGroupId: String): Flow<List<User>>

    // The updateUserProfileLocal default method has been removed.
    // This logic will be handled by UserRepository.
}