package com.homeostasis.app.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.homeostasis.app.data.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await

/**
 * Repository for task-related operations.
 */
class TaskRepository : FirebaseRepository<Task>() {
    
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
     * Update a task.
     */
    suspend fun updateTask(taskId: String, updates: Map<String, Any>): Boolean {
        return try {
            val updatesWithTimestamp = updates.toMutableMap().apply {
                put("lastModifiedAt", Timestamp.now())
            }
            update(taskId, updatesWithTimestamp)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Soft delete a task.
     */
    suspend fun softDeleteTask(taskId: String): Boolean {
        return try {
            val updates = mapOf(
                "isDeleted" to true,
                "lastModifiedAt" to Timestamp.now()
            )
            update(taskId, updates)
        } catch (e: Exception) {
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