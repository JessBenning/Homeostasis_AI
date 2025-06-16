package com.homeostasis.app.data.remote

import android.util.Log
import androidx.core.util.remove
import com.google.firebase.Timestamp // Keep for existing methods if needed, but prefer FieldValue for updates
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue // NEW: For server-side timestamps
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
// import com.homeostasis.app.data.TaskDao // REMOVED: TaskRepository will focus on Firestore
import com.homeostasis.app.data.model.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await

// Assuming FirebaseRepository is a base class providing 'collection' and 'firestore' instances
// and an 'add' method. If 'add' isn't from a base class, 'createTask' will need adjustment.
//abstract class FirebaseRepository<T : Any> { // Simplified mock for this example
//    protected val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
//    abstract val collectionName: String
//    abstract fun getModelClass(): Class<T>
//    // Example: suspend fun add(item: T): String? { /* ... */ return documentId }
//    // Example: suspend fun update(id: String, updates: Map<String, Any>): Boolean { /* ... */ return success }
//}


/**
 * Repository for task-related operations with Firestore.
 * This repository NO LONGER directly interacts with TaskDao for task operations.
 * ViewModel and FirebaseSyncManager will interact with TaskDao directly.
 */
class TaskRepository(/* private val taskDao: TaskDao */) : FirebaseRepository<Task>() { // TaskDao removed from constructor

    override val collectionName: String = Task.COLLECTION

    private val taskCollection: CollectionReference = firestore.collection(collectionName)

    // --- READ Operations from Firestore ---
    // These methods remain largely the same as they read data.
    // Firestore won't return needsSync or isDeletedLocally as we don't store them there.

    /**
     * Get tasks by category ID.
     */
    suspend fun getTasksByCategory(categoryId: String): List<Task> { // UNCHANGED
        return try {
            taskCollection
                .whereEqualTo("categoryId", categoryId)
                .whereEqualTo("isDeleted", false)
                .get()
                .await()
                .toObjects(Task::class.java)
        } catch (e: Exception) {
            Log.e("TaskRepository", "Error getting tasks by category $categoryId", e)
            emptyList()
        }
    }

    /**
     * Get tasks by category ID as a Flow.
     */
    fun getTasksByCategoryAsFlow(categoryId: String): Flow<List<Task>> = callbackFlow { // UNCHANGED
        val listenerRegistration = taskCollection
            .whereEqualTo("categoryId", categoryId)
            .whereEqualTo("isDeleted", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val tasks = snapshot.toObjects(Task::class.java)
                    trySend(tasks).isSuccess // Use isSuccess to handle potential send failures on a closed channel
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    /**
     * Get all active (non-deleted) tasks.
     */
    suspend fun getActiveTasks(): List<Task> { // UNCHANGED
        return try {
            taskCollection
                .whereEqualTo("isDeleted", false)
                .get()
                .await()
                .toObjects(Task::class.java)
        } catch (e: Exception) {
            Log.e("TaskRepository", "Error getting active tasks", e)
            emptyList()
        }
    }

    /**
     * Get all active tasks as a Flow.
     */
    fun getActiveTasksAsFlow(): Flow<List<Task>> = callbackFlow { // UNCHANGED
        val listenerRegistration = taskCollection
            .whereEqualTo("isDeleted", false)
            // Consider adding .orderBy("lastModifiedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val tasks = snapshot.toObjects(Task::class.java)
                    trySend(tasks).isSuccess
                }
            }
        awaitClose { listenerRegistration.remove() }
    }


    // --- WRITE Operations to Firestore (Mainly for FirebaseSyncManager) ---

    /**
     * Old createTask - This was likely called by ViewModel directly.
     * The ViewModel will now interact with TaskDao to create a task locally (which sets needsSync=true),
     * and FirebaseSyncManager will later call createOrUpdateTaskInFirestore.
     * If you still need a direct "create immediately in Firestore" function outside the sync flow,
     * it should also be modified to exclude local flags and use server timestamps.
     * For now, I'll assume this specific version of 'createTask' might be deprecated
     * in favor of the local-first approach. If not, it needs the same treatment as createOrUpdateTaskInFirestore.
     */
    // suspend fun createTask(task: Task): String? { ... } // Potential candidate for removal or refactoring


    /**
     * MODIFIED: Creates a new task in Firestore or updates an existing one.
     * This method is "smart" about timestamps for lastModifiedAt (uses server timestamp)
     * and ensures local-only flags are NOT persisted to Firestore.
     * The Task object's createdAt timestamp is preserved.
     * The 'isDeleted' field from the task object IS persisted.
     * Called by FirebaseSyncManager.
     */
    suspend fun createOrUpdateTaskInFirestore(task: Task): Boolean { // RENAMED and MODIFIED from your 'createOrUpdateTask'
        return try {
            // Data to be sent to Firestore, EXCLUDING local-only flags
            val firestoreTaskData = mapOf(
//                "id" to task.id,
                "title" to task.title,
                "description" to task.description, // Keep even if null to clear field
                "points" to task.points,
                "categoryId" to task.categoryId,
                "ownerId" to task.ownerId, // Use ownerId instead of createdBy
                "createdAt" to task.createdAt, // Preserve original creation time
                "lastModifiedAt" to FieldValue.serverTimestamp(), // Use server timestamp for modification
                "isCompleted" to task.isCompleted, // Persist completion status
                "isDeleted" to task.isDeleted,// Persist soft-delete status (usually false for create/update)
                "householdGroupId" to task.householdGroupId
            // "needsSync" and "isDeletedLocally" are intentionally omitted
            ).filterValues { it != null } // Firestore typically ignores nulls, but explicit filtering can be clearer

            firestore.collection(collectionName) // Use collectionName from FirebaseRepository
                .document(task.id)
                .set(firestoreTaskData, SetOptions.merge()) // Set with merge to handle create or update
                .await()
            Log.d("TaskRepository", "Task ${task.id} created/updated in Firestore.")
            true
        } catch (e: Exception) {
            Log.e("TaskRepository", "Error in createOrUpdateTaskInFirestore for task ${task.id}", e)
            false
        }
    }


    /**
     * Your existing updateTask. This is generic. If used by FirebaseSyncManager,
     * ensure 'updates' map doesn't contain local-only flags and includes server timestamp for lastModifiedAt.
     * If this is only called with specific field updates from ViewModel directly (bypassing full sync),
     * it might be okay, but ensure 'lastModifiedAt' is handled, perhaps by requiring it in the map.
     *
     * For FirebaseSyncManager, createOrUpdateTaskInFirestore (which takes a full Task object) is usually preferred.
     */
    suspend fun updateTask(taskId: String, updates: Map<String, Any>): Boolean { // Mostly UNCHANGED, but with caveats
        return try {
            // IMPORTANT: Ensure 'updates' map includes "lastModifiedAt" to FieldValue.serverTimestamp()
            // if this method is intended to signify a full update.
            // And ensure it does NOT contain needsSync or isDeletedLocally.
            val finalUpdates = updates.toMutableMap()
            if (!finalUpdates.containsKey("lastModifiedAt")) {
                finalUpdates["lastModifiedAt"] = FieldValue.serverTimestamp() // Good practice to always update this
            }

            firestore.collection(collectionName)
                .document(taskId)
                .update(finalUpdates)
                .await()
            Log.d("TaskRepository", "Task $taskId updated in Firestore with specific fields.")
            true
        } catch (e: Exception) {
            Log.e("TaskRepository", "Error in updateTask for $taskId", e)
            false
        }
    }

    /**
     * REMOVED: Marks a task as deleted in the local Room database.
     * This logic will now be in the ViewModel (calling TaskDao) or FirebaseSyncManager (calling TaskDao).
     * The repository only handles Firestore operations.
     */
    // suspend fun markTaskAsDeletedLocally(taskId: String) { ... }


    /**
     * MODIFIED: Performs a soft delete operation on a task document in Firestore.
     * Sets isDeleted = true and updates lastModifiedAt using server timestamp.
     * This will be called by FirebaseSyncManager.
     */
    suspend fun softDeleteTaskInFirestore(taskId: String): Boolean { // MODIFIED
        return try {
            val updates = mapOf(
                "isDeleted" to true,
                "lastModifiedAt" to FieldValue.serverTimestamp() // Use server timestamp
            )
            firestore.collection(collectionName).document(taskId).update(updates).await()
            // Your base 'update' method might not exist or might not use server timestamps.
            // Direct call to firestore.collection.document.update is safer here for clarity.
            // update(taskId, updates) // If your base 'update' is suitable, you can use it.
            Log.d("TaskRepository", "Task $taskId soft-deleted in Firestore.")
            true
        } catch (e: Exception) {
            Log.e("TaskRepository", "Error soft-deleting task $taskId in Firestore", e)
            false
        }
    }

    /**
     * Method to actually delete a task from Firestore.
     * This is a HARD delete. Use with caution.
     * FirebaseSyncManager might call this AFTER a successful soft delete if you want to eventually purge.
     * Or it might be an admin function.
     */
    suspend fun hardDeleteTaskFromFirestore(taskId: String): Boolean { // UNCHANGED but be mindful of its use
        return try {
            firestore.collection(collectionName).document(taskId).delete().await()
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
    suspend fun getTasksByUser(userId: String): List<Task> { // UNCHANGED
        return try {
            collection
                .whereEqualTo("ownerId", userId) // Query by ownerId
                .whereEqualTo("isDeleted", false)
                .get()
                .await()
                .toObjects(Task::class.java)
        } catch (e: Exception) {
            Log.e("TaskRepository", "Error getting tasks by user $userId", e)
            emptyList()
        }
    }

    override fun getModelClass(): Class<Task> = Task::class.java

    // Your old 'createTask' method that used base 'add':
    // If your FirebaseRepository's 'add' method can be modified to accept a Map<String, Any>
    // and handle server timestamps, you could adapt it. Otherwise, using the direct
    // .document().set() as in createOrUpdateTaskInFirestore is clearer.
    /**
     * Old 'createTask'. If keeping, it needs to be updated to exclude local flags
     * and handle timestamps similarly to createOrUpdateTaskInFirestore.
     * For now, FirebaseSyncManager will use createOrUpdateTaskInFirestore.
     */
    // suspend fun createTask(task: Task): String? {
    //     return try {
    //         // This needs to be a map excluding local flags, with server timestamp for lastModifiedAt
    //         val firestoreTaskData = task.toFirestoreMap() // You'd need a helper extension function
    //         // And your base 'add' method needs to handle this map correctly.
    //         // add(firestoreTaskData) // This is conceptual if 'add' takes a map
    //         // OR, if 'add' takes a Task object, the Task object itself needs to be "clean"
    //         val cleanTask = task.copy(lastModifiedAt = Timestamp.now()) // This would be client time
    //         // add(cleanTask)
    //     } catch (e: Exception) {
    //         null
    //     }
    // }
}