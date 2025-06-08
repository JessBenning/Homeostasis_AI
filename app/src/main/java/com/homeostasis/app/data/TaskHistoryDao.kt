package com.homeostasis.app.data

import androidx.annotation.Nullable
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.homeostasis.app.data.model.TaskHistory
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.NotNull


@Dao
interface TaskHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(taskHistory: TaskHistory)



//    @Upsert
//    suspend fun upsert(taskHistory: TaskHistory)


    @Update
    suspend fun update(taskHistory: TaskHistory)

    @Delete
    suspend fun delete(taskHistory: TaskHistory)

    @Query("SELECT * FROM task_history WHERE householdGroupId = :householdGroupId")
    fun getAllTaskHistory(householdGroupId: String): Flow<List<TaskHistory>>

    @Query("SELECT * FROM task_history WHERE taskId = :taskId AND householdGroupId = :householdGroupId")
    suspend fun getTaskHistoryByTaskId(taskId: String, householdGroupId: String): List<TaskHistory>

    @Query("SELECT * FROM task_history WHERE id = :id AND householdGroupId = :householdGroupId")
    suspend fun getTaskHistoryById(id: String, householdGroupId: String): TaskHistory?


    @Upsert // Or @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(taskHistory: TaskHistory)

    @Query("DELETE FROM task_history WHERE householdGroupId =:householdGroupId")
    suspend fun deleteAllTaskHistory(householdGroupId: String)

    // For the sync manager to get all local items to check against Firestore
    @Query("SELECT * FROM task_history WHERE householdGroupId = :householdGroupId")
    fun getAllTaskHistoryBlocking(householdGroupId: String): List<TaskHistory> // Or return Flow<List<TaskHistory>>

    @Query("SELECT * FROM task_history WHERE needsSync = 1 AND householdGroupId = :householdGroupId")
    fun getModifiedTaskHistoryRequiringSync(householdGroupId: String): Flow<List<TaskHistory>>

    @Query("SELECT * FROM task_history WHERE householdGroupId = :householdGroupId ORDER BY completedAt DESC" ) // Or your actual table name
    fun getAllTaskHistoryFlow(householdGroupId: String): Flow<List<TaskHistory>>
}