package com.homeostasis.app.data

import androidx.room.*
import com.homeostasis.app.data.model.Task
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    @Query("SELECT * FROM ${Task.COLLECTION} WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getActiveTasks(): Flow<List<Task>>

    @Update
    suspend fun updateTask(task: Task)

    // Used by TaskRepository to fetch the task before updating its isDeleted flag
    @Query("SELECT * FROM ${Task.COLLECTION} WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): Task?

    // Used by FirebaseSyncManager for Part 1 (local to remote sync)
    @Query("SELECT * FROM ${Task.COLLECTION} ORDER BY createdAt DESC") // Gets ALL, including those marked isDeleted=true
    fun getAllTasksIncludingDeleted(): Flow<List<Task>>

    // Used by FirebaseSyncManager AFTER successful Firestore deletion
    @Delete
    suspend fun deleteTask(task: Task) // The actual hard delete

    @Query("SELECT * FROM ${Task.COLLECTION}")
    fun getAllTasks(): Flow<List<Task>>

}