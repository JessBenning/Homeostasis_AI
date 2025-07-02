package com.homeostasis.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.homeostasis.app.data.model.User // Ensure this is your User data class
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM User WHERE id = :userId AND householdGroupId = :householdGroupId")
    fun getUserByIdFlow(userId: String, householdGroupId: String): Flow<User?>

    @Query("SELECT * FROM User WHERE id = :userId AND householdGroupId = :householdGroupId")
    suspend fun getUserById(userId: String, householdGroupId: String): User?

    @Query("SELECT * FROM User WHERE id = :userId")
    suspend fun getUserById(userId: String): User?

    // This one seems more general, ensure it's distinct enough or consolidate if they overlap too much.
    // Assuming 'User' is the correct table name based on your other queries.
    @Query("SELECT * FROM User WHERE id = :userId LIMIT 1")
    suspend fun getUserByIdSingle(userId: String): User? // Renamed slightly to avoid overload confusion if parameters were same

    @Query("DELETE FROM User WHERE id = :userId")
    suspend fun deleteUserById(userId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: User) // This is good, can be used for inserting and updating

    @Delete
    suspend fun deleteUser(user: User)

    /**
     * Gets a flow of users that need to be synchronized to the remote database.
     * Changed to return List<User> directly as it's a one-off fetch for sync, not continuous.
     * If you prefer a Flow, you can keep your original Flow<List<User>>.
     */
    @Query("SELECT * FROM User WHERE needsSync = 1")
    suspend fun getUsersRequiringSync(): List<User> // Changed to suspend fun List<User>

    @Query("SELECT * FROM User WHERE householdGroupId = :householdGroupId")
    fun getAllUsersFlow(householdGroupId: String): Flow<List<User>>

    /**
     * Gets a list of users belonging to a specific household group.
     * Intended for snapshot queries, not for observing changes.
     */
    @Query("SELECT * FROM User WHERE householdGroupId = :householdGroupId")
    suspend fun getUsersByHouseholdGroupIdSnapshot(householdGroupId: String): List<User>

    /**
     * Gets a flow of a single user by their ID, without filtering by household group ID.
     * This is intended for scenarios where the household group ID is not yet known or is changing.
     */
    @Query("SELECT * FROM User WHERE id = :userId")
    fun getUserByIdWithoutHouseholdIdFlow(userId: String): Flow<User?>


    // --- NEW METHOD for Option 3 user update ---
    /**
     * Updates the householdGroupId for a given user and marks them as needing sync.
     * Assumes your table is named 'User'.
     *
     * @param userId The ID of the user to update.
     * @param householdId The new householdGroupId to set. Can be null if the user leaves a group.
     * @return The number of rows updated (should be 1 if user exists, 0 otherwise).
     */
    @Query("UPDATE User SET householdGroupId = :householdId, needsSync = 1 WHERE id = :userId")
    suspend fun updateUserHouseholdIdAndMarkForSync(userId: String, householdId: String?): Int


    // The updateUserProfileLocal default method has been removed. Logic handled by UserRepository.
    // Consider if any other methods like updateUserStreaks from the previous example are needed.
}