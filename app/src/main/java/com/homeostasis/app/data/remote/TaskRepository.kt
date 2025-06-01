package com.homeostasis.app.data.remote

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.homeostasis.app.data.TaskDao
import com.homeostasis.app.data.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await

/**
 * Repository for task-related operations.
 */
class TaskRepository(private val taskDao: TaskDao) : FirebaseRepository<Task>() {
    
    override val collectionName: String = Task.COLLECTION
    
    /**
     * Get tasks by category ID.
     */
    suspend fun getTasksByCategory(categoryId: String): List<Task> {
        return try {
            collection
                .whereEqualTo("categoryId", categoryId)
                .whereEqualTo("isDeleted", false)
                .get()
                .await()
                .toObjects(Task::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get tasks by category ID as a Flow.
     */
    fun getTasksByCategoryAsFlow(categoryId: String): Flow<List<Task>> = callbackFlow {
        val listenerRegistration = collection
            .whereEqualTo("categoryId", categoryId)
            .whereEqualTo("isDeleted", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val tasks = snapshot.toObjects(Task::class.java)
                    trySend(tasks)
                }
            }
        
        awaitClose { listenerRegistration.remove() }
    }
    
    /**
     * Get all active (non-deleted) tasks.
     */
    suspend fun getActiveTasks(): List<Task> {
        return try {
            collection
                .whereEqualTo("isDeleted", false)
                .get()
                .await()
                .toObjects(Task::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get all active tasks as a Flow.
     */
    fun getActiveTasksAsFlow(): Flow<List<Task>> = callbackFlow {
        val listenerRegistration = collection
            .whereEqualTo("isDeleted", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val tasks = snapshot.toObjects(Task::class.java)
                    trySend(tasks)
                }
            }
        
        awaitClose { listenerRegistration.remove() }
    }
    
    /**
     * Create a new task.
     */
    suspend fun createTask(task: Task): String? {
        return try {
            val taskWithTimestamp = task.copy(
                createdAt = Timestamp.now(),
                lastModifiedAt = Timestamp.now()
            )
            add(taskWithTimestamp)
        } catch (e: Exception) {
            null
        }
    }
    /**
     * Creates a new task in Firestore if it doesn't exist, or updates it if it does.
     * This method is "dumb" regarding timestamps; it persists the Task object as-is.
     * The Task object should have its createdAt and lastModifiedAt timestamps
     * already correctly set by the caller (e.g., ViewModel or FirebaseSyncManager
     * relaying ViewModel-set timestamps).
     */
    suspend fun createOrUpdateTask(task: Task): Boolean {
        return try {
            firestore.collection(Task.COLLECTION)
                .document(task.id)
                .set(task, SetOptions.merge()) // Key: set with merge for upsert
                .await()
            true
        } catch (e: Exception) {
            val taskIdForLog = task?.id ?: "UNKNOWN_OR_NULL_ID_IN_REPO"
            Log.e("TaskRepository", "Error in createOrUpdateTask for task. Task ID: $taskIdForLog. Full task object: $task", e)
            false
        }
    }
    
    /**
     * Update a task.
     */
    suspend fun updateTask(taskId: String, updates: Map<String, Any>): Boolean {
        return try {
            firestore.collection(Task.COLLECTION)
                .document(taskId)
                .update(updates) // Or .set(updates, SetOptions.merge()) if you want this to be an upsert too
                .await()
            true
        } catch (e: Exception) {
            Log.e("TaskRepository", "Error in updateTask for $taskId", e)
            false
        }
    }

    /**
     * Marks a task as deleted in the local Room database.
     * This is the method the ViewModel will call.
     */
    suspend fun markTaskAsDeletedLocally(taskId: String) {
        val task = taskDao.getTaskById(taskId)
        if (task != null) {
            val updatedTask = task.copy(
                isDeleted = true,
                lastModifiedAt = Timestamp.now() // Update lastModifiedAt
            )
            taskDao.updateTask(updatedTask)
            Log.d("TaskRepository", "Task $taskId marked as deleted in Room.")
        } else {
            Log.w("TaskRepository", "Task $taskId not found in Room to mark as deleted.")
        }
    }

    /**
     * Performs a soft delete operation on a task document in Firestore.
     * Sets isDeleted = true and updates lastModifiedAt.
     * This will be called by FirebaseSyncManager.
     */
    suspend fun softDeleteTaskInFirestore(taskId: String): Boolean {
        return try {
            val updates = mapOf(
                "isDeleted" to true,
                "lastModifiedAt" to Timestamp.now()
            )
            // Assuming your FirebaseRepository's update method handles this:
            update(taskId, updates) // This calls firestore.collection.document.update()
            // OR if 'update' is not suitable/available, directly:
            // firestore.collection(Task.COLLECTION).document(taskId).update(updates).await()
            // true
            Log.d("TaskRepository", "Task $taskId soft-deleted in Firestore.")
            true
        } catch (e: Exception) {
            Log.e("TaskRepository", "Error soft-deleting task $taskId in Firestore", e)
            false
        }
    }

    // Method to actually delete a task from Firestore (used if needed, but soft delete is primary)
    suspend fun hardDeleteTaskFromFirestore(taskId: String): Boolean {
        return try {
            firestore.collection(Task.COLLECTION).document(taskId).delete().await()
            Log.d("TaskRepository", "Task $taskId hard-deleted from Firestore.")
            true
        } catch (e: Exception) {
            Log.e("TaskRepository", "Error hard-deleting task $taskId from Firestore", e)
            false
        }
    }
    
    /**
     * Get tasks created by a specific user.
     */
    suspend fun getTasksByUser(userId: String): List<Task> {
        return try {
            collection
                .whereEqualTo("createdBy", userId)
                .whereEqualTo("isDeleted", false)
                .get()
                .await()
                .toObjects(Task::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override fun getModelClass(): Class<Task> = Task::class.java
}