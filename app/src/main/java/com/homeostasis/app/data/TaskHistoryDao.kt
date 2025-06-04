package com.homeostasis.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.homeostasis.app.data.model.TaskHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(taskHistory: TaskHistory)

    @Update
    suspend fun update(taskHistory: TaskHistory)

    @Delete
    suspend fun delete(taskHistory: TaskHistory)

    @Query("SELECT * FROM task_history")
    fun getAllTaskHistory(): Flow<List<TaskHistory>>

    @Query("SELECT * FROM task_history WHERE taskId = :taskId")
    suspend fun getTaskHistoryByTaskId(taskId: String): List<TaskHistory>

    @Query("SELECT * FROM task_history WHERE id = :id")
    suspend fun getTaskHistoryById(id: String): TaskHistory?

    @Upsert // Or @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(taskHistory: TaskHistory)

    @Query("DELETE FROM task_history")
    suspend fun deleteAllTaskHistory()

    // For the sync manager to get all local items to check against Firestore
    @Query("SELECT * FROM task_history")
    fun getAllTaskHistoryBlocking(): List<TaskHistory> // Or return Flow<List<TaskHistory>>

    @Query("SELECT * FROM task_history WHERE needsSync = 1")
    fun getModifiedTaskHistoryRequiringSync(): Flow<List<TaskHistory>>
}