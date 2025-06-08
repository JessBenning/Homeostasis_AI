package com.homeostasis.app.data

import androidx.room.*
import com.homeostasis.app.data.model.Task
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: Task) // Consolidated insert/update

    @Update // Keep if you have specific use cases where you ONLY want to update existing
    // and fail if the task doesn't exist, otherwise upsertTask covers it.
    suspend fun updateTask(task: Task) // If keeping, ensure its use is distinct from upsert

    @Delete
    suspend fun hardDeleteTaskFromRoom(task: Task) // Consolidated hard delete

    @Query("SELECT * FROM tasks WHERE id = :taskId AND householdGroupId = :householdGroupId")
    suspend fun getTaskById(taskId: String, householdGroupId: String): Task? // Non-Flow access

    @Query("SELECT * FROM tasks WHERE id = :taskId AND householdGroupId = :householdGroupId")
    fun getTaskByIdAsFlow(taskId: String, householdGroupId: String): Flow<Task?> // Flow access for a single task

    // In TaskDao.kt
    @Query("SELECT * FROM tasks WHERE householdGroupId = :householdGroupId")
    fun getAllTasksFlow(householdGroupId: String): Flow<List<Task>>

    /**
     * Gets tasks that should be visible in the UI.
     */
    @Query("SELECT * FROM tasks WHERE isDeletedLocally = 0 AND isDeleted = 0 AND householdGroupId = :householdGroupId ORDER BY lastModifiedAt DESC")
    fun getActiveVisibleTasks(householdGroupId: String): Flow<List<Task>>

    /**
     * For FirebaseSyncManager: Gets tasks marked for local deletion that need this status synced to Firestore.
     */
    @Query("SELECT * FROM tasks WHERE isDeletedLocally = 1 AND needsSync = 1 AND householdGroupId = :householdGroupId ORDER BY lastModifiedAt ASC") // ASC to process older ones first
    fun getLocallyDeletedTasksRequiringSync(householdGroupId: String): Flow<List<Task>>

    /**
     * For FirebaseSyncManager: Gets tasks (not marked for local deletion) that have local
     * modifications and need to be created/updated in Firestore.
     */
    @Query("SELECT * FROM tasks WHERE isDeletedLocally = 0 AND needsSync = 1 AND householdGroupId = :householdGroupId ORDER BY lastModifiedAt ASC") // ASC to process older ones first
    fun getModifiedTasksRequiringSync(householdGroupId: String): Flow<List<Task>>

    /**
     * For FirebaseSyncManager: Gets all tasks from Room. Used during the remote-to-local sync
     * to compare with what's in Firestore. It's a one-time fetch, not a Flow.
     */
    @Query("SELECT * FROM tasks WHERE householdGroupId = :householdGroupId") // No specific order needed here usually, as it's for a snapshot comparison
    suspend fun getAllTasksFromRoomSnapshot(householdGroupId: String): List<Task>

    // Optional: If you still need a Flow of all tasks for some other purpose.
    // @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    // fun getAllTasksFlow(): Flow<List<Task>>

}