package com.homeostasis.app.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.homeostasis.app.data.model.TaskHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await
import java.util.Date
import com.homeostasis.app.data.AppDatabase

/**
 * Repository for task history-related operations.
 */
class TaskHistoryRepository(private val context: android.content.Context) : FirebaseRepository<TaskHistory>() {
    
    override val collectionName: String = TaskHistory.COLLECTION
    
    /**
     * Record a task completion.
     */
    suspend fun recordTaskCompletion(
        taskId: String,
        userId: String,
        pointValue: Int,
        customCompletionDate: Date? = null,
        // context: android.content.Context // No longer needed if DAO is injected or handled by ViewModel
        taskHistoryId: String // It's better to generate ID in ViewModel/before calling repo
    ): Boolean { // Return Boolean for success
        val TAG_REPO_RECORD = "TaskHistoryRepo_Record"
        return try {
            val newHistory = TaskHistory(
               // id = taskHistoryId, // Use pre-generated ID
                taskId = taskId,
                userId = userId,
                completedAt = Timestamp.now(),
                pointValue = pointValue,
                customCompletionDate = customCompletionDate?.let { Timestamp(it) }, // Convert Date to Timestamp
                isDeleted = false,
                isArchived = false,
                archivedInResetId = null,
                lastModifiedAt = Timestamp.now(),
                // needsSync = true // <<<< ADD THIS if you adopt a 'needsSync' flag model
            )


            val localTaskHistoryDao = AppDatabase.getDatabase(this.context).taskHistoryDao() // Use class context
            localTaskHistoryDao.insertOrUpdate(newHistory) // Still write locally first
            Log.d(TAG_REPO_RECORD, "TaskHistory ${newHistory.id} inserted into local Room DB.")

            // Now attempt to write to Firestore as well
            collection.document(newHistory.id).set(newHistory).await() // Use set with ID for consistency
            Log.d(TAG_REPO_RECORD, "TaskHistory ${newHistory.id} also attempted to write to Firestore.")
            true // Firestore write succeeded
        } catch (e: Exception) {
            Log.e(TAG_REPO_RECORD, "Error in recordTaskCompletion for $taskId (ID: $taskHistoryId)", e)
            // If Firestore write failed, it's still in Room and SyncManager should pick it up.
            false // Indicate Firestore write failed
        }
    }

    /**
     * Creates a new TaskHistory document in Firestore or updates it if it already exists.
     * This method is intended to be called by the FirebaseSyncManager to push local changes to remote.
     */
    suspend fun createOrUpdateFirestoreTaskHistory(taskHistory: TaskHistory): Boolean {
        val TAG_REPO = "TaskHistoryRepo_Sync" // Specific tag
        return try {
            if (taskHistory.id.isBlank()) {
                Log.e(TAG_REPO, "TaskHistory ID is blank. Cannot sync to Firestore. Data: $taskHistory")
                return false // Firestore document IDs cannot be blank
            }
            Log.d(TAG_REPO, "Attempting to write/update TaskHistory to Firestore: ID=${taskHistory.id}")
            // Using collection.document(id).set(data, SetOptions.merge()) is robust for create or update
            collection.document(taskHistory.id).set(taskHistory, com.google.firebase.firestore.SetOptions.merge()).await()
            Log.d(TAG_REPO, "Successfully wrote/updated TaskHistory ${taskHistory.id} to Firestore.")
            true
        } catch (e: Exception) {
            // Log the specific error, which might be the UnknownHostException
            Log.e(TAG_REPO, "Error writing/updating TaskHistory ${taskHistory.id} to Firestore. Collection: $collectionName", e)
            false
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
//    suspend fun clearTaskHistory() {
//        taskHistoryDao.clearTable()
//    }
    
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
    suspend fun getTaskHistoryByTimePeriod(startDate: Timestamp, endDate: Timestamp): List<TaskHistory> {
        return try {
            collection
                .whereEqualTo("isDeleted", false)
                .whereGreaterThanOrEqualTo("completedAt", startDate)
                .whereLessThanOrEqualTo("completedAt", endDate)
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
    /**
     * Deletes a task history entry from Firestore.
     * This is intended to be called by the sync manager after a local deletion is synced.
     */
    suspend fun deleteFirestoreTaskHistory(taskHistoryId: String): Boolean {
        val TAG_REPO = "TaskHistoryRepo_Delete" // Specific tag
        return try {
            if (taskHistoryId.isBlank()) {
                Log.e(TAG_REPO, "TaskHistory ID is blank. Cannot delete from Firestore.")
                return false // Firestore document IDs cannot be blank
            }
            Log.d(TAG_REPO, "Attempting to delete TaskHistory from Firestore: ID=$taskHistoryId")
            collection.document(taskHistoryId).delete().await()
            Log.d(TAG_REPO, "Successfully deleted TaskHistory $taskHistoryId from Firestore.")
            true
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Error deleting TaskHistory $taskHistoryId from Firestore. Collection: $collectionName", e)
            false
        }
    }

    override fun getModelClass(): Class<TaskHistory> = TaskHistory::class.java
}

