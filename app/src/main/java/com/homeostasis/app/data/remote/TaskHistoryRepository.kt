package com.homeostasis.app.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.homeostasis.app.data.model.TaskHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Repository for task history-related operations.
 */
class TaskHistoryRepository : FirebaseRepository<TaskHistory>() {
    
    override val collectionName: String = TaskHistory.COLLECTION
    
    /**
     * Record a task completion.
     */
    suspend fun recordTaskCompletion(
        taskId: String,
        userId: String,
        pointValue: Int,
        customCompletionDate: Date? = null
    ): String? {
        return try {
            val taskHistory = TaskHistory(
                taskId = taskId,
                userId = userId,
                completedAt = Timestamp.now(),
                pointValue = pointValue,
                customCompletionDate = customCompletionDate,
                lastModifiedAt = Timestamp.now()
            )
            add(taskHistory)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get task history for a specific task.
     */
    suspend fun getTaskHistoryByTaskId(taskId: String): List<TaskHistory> {
        return try {
            collection
                .whereEqualTo("taskId", taskId)
                .whereEqualTo("isDeleted", false)
                .orderBy("completedAt", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(TaskHistory::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get task history for a specific task as a Flow.
     */
    fun getTaskHistoryByTaskIdAsFlow(taskId: String): Flow<List<TaskHistory>> = callbackFlow {
        val listenerRegistration = collection
            .whereEqualTo("taskId", taskId)
            .whereEqualTo("isDeleted", false)
            .orderBy("completedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val taskHistory = snapshot.toObjects(TaskHistory::class.java)
                    trySend(taskHistory)
                }
            }
        
        awaitClose { listenerRegistration.remove() }
    }
    
    /**
     * Get task history for a specific user.
     */
    suspend fun getTaskHistoryByUserId(userId: String): List<TaskHistory> {
        return try {
            collection
                .whereEqualTo("userId", userId)
                .whereEqualTo("isDeleted", false)
                .orderBy("completedAt", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(TaskHistory::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get task history for a specific user as a Flow.
     */
    fun getTaskHistoryByUserIdAsFlow(userId: String): Flow<List<TaskHistory>> = callbackFlow {
        val listenerRegistration = collection
            .whereEqualTo("userId", userId)
            .whereEqualTo("isDeleted", false)
            .orderBy("completedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val taskHistory = snapshot.toObjects(TaskHistory::class.java)
                    trySend(taskHistory)
                }
            }
        
        awaitClose { listenerRegistration.remove() }
    }
    
    /**
     * Get task history for a specific time period.
     */
    suspend fun getTaskHistoryByTimePeriod(startDate: Date, endDate: Date): List<TaskHistory> {
        return try {
            collection
                .whereEqualTo("isDeleted", false)
                .whereGreaterThanOrEqualTo("completedAt", Timestamp(startDate))
                .whereLessThanOrEqualTo("completedAt", Timestamp(endDate))
                .orderBy("completedAt", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(TaskHistory::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Archive task history entries.
     */
    suspend fun archiveTaskHistory(resetId: String): Boolean {
        return try {
            // Get all non-archived, non-deleted task history entries
            val taskHistoryToArchive = collection
                .whereEqualTo("isArchived", false)
                .whereEqualTo("isDeleted", false)
                .get()
                .await()
                .documents
            
            // Batch update to archive them
            val batch = firestore.batch()
            taskHistoryToArchive.forEach { document ->
                batch.update(
                    document.reference,
                    mapOf(
                        "isArchived" to true,
                        "archivedInResetId" to resetId,
                        "lastModifiedAt" to Timestamp.now()
                    )
                )
            }
            
            batch.commit().await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Soft delete a task history entry.
     */
    suspend fun softDeleteTaskHistory(taskHistoryId: String): Boolean {
        return try {
            val updates = mapOf(
                "isDeleted" to true,
                "lastModifiedAt" to Timestamp.now()
            )
            update(taskHistoryId, updates)
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getModelClass(): Class<TaskHistory> = TaskHistory::class.java
}