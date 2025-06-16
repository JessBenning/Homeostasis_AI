package com.homeostasis.app.data.remote

import com.homeostasis.app.data.Constants
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.homeostasis.app.data.model.TaskHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.first // Import first
import java.util.Date
import com.homeostasis.app.data.AppDatabase
import javax.inject.Inject

/**
 * Repository for task history-related operations.
 */
class TaskHistoryRepository @Inject constructor(
    private val taskHistoryDao: com.homeostasis.app.data.TaskHistoryDao, // Inject TaskHistoryDao
) : FirebaseRepository<TaskHistory>() {
    
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
    ): Boolean { // Return Boolean for success (local save success)
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
                needsSync = true // Mark for sync
            )

            // Use the injected DAO to write locally only
            taskHistoryDao.insertOrUpdate(newHistory)
            Log.d(TAG_REPO_RECORD, "TaskHistory ${newHistory.id} inserted into local Room DB and marked for sync.")

            true // Local save succeeded
        } catch (e: Exception) {
            Log.e(TAG_REPO_RECORD, "Error in recordTaskCompletion (local save) for $taskId (ID: $taskHistoryId)", e)
            false // Local save failed
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

    /**
     * Permanently deletes all task history entries for the current household group from the local database.
     * The sync manager is expected to handle the corresponding remote deletion.
     */
    /**
     * Permanently deletes all task history entries for the specified household group from the local database.
     * The sync manager is expected to handle the corresponding remote deletion.
     */
    suspend fun deleteAllTaskHistory(householdGroupId: String) {
        if (householdGroupId.isNotEmpty()) {
            taskHistoryDao.deleteAllTaskHistory(householdGroupId)
            Log.d("TaskHistoryRepo", "All task history deleted locally for household group: $householdGroupId")
        } else {
            Log.w("TaskHistoryRepo", "Cannot delete task history, provided householdGroupId is empty.")
        }
    }

    override fun getModelClass(): Class<TaskHistory> = TaskHistory::class.java
}

