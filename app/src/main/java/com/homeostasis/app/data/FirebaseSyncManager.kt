package com.homeostasis.app.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.homeostasis.app.data.AppDatabase
import com.google.firebase.firestore.DocumentChange
import kotlinx.coroutines.flow.collect
import com.homeostasis.app.data.model.Task
import com.homeostasis.app.data.model.TaskHistory
import android.net.Uri
import com.homeostasis.app.data.remote.TaskHistoryRepository
import com.homeostasis.app.data.remote.TaskRepository
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import androidx.work.await
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

import javax.inject.Singleton


@Singleton
class FirebaseSyncManager @Inject constructor(
    private val db: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val taskRepository: TaskRepository,
    private val taskHistoryRepository: TaskHistoryRepository,
    @ApplicationContext private val context: Context
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        Log.d("FirebaseSyncManager", "Initialized")
        syncTasks()
        syncTaskHistory()
    }

    fun syncTasks() {
        scope.launch {
            // Part 2: Listen for remote changes FROM Firestore and sync TO Local DB
            // SETUP THE LISTENER FIRST.
            val initialSyncCompleter =
                CompletableDeferred<Unit>() // To signal initial fetch is done

            firestore.collection(Task.COLLECTION)
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        Log.w("FirebaseSyncManager", "Listen failed for tasks collection.", e)
                        if (!initialSyncCompleter.isCompleted) initialSyncCompleter.completeExceptionally(e) // Signal error if initial
                        return@addSnapshotListener
                    }

                    if (snapshots != null) {
                        Log.d("FirebaseSyncManager", "Received ${snapshots.documentChanges.size} task document changes from Firestore.")
                        scope.launch { // Launch a new coroutine for processing to not block the listener
                            for (dc in snapshots.documentChanges) {
                                val taskFromRemote = try {
                                    dc.document.toObject(Task::class.java)
                                } catch (deserializationError: Exception) {
                                    Log.e("FirebaseSyncManager", "Error deserializing task from Firestore: ${dc.document.id}", deserializationError)
                                    continue
                                }
                                if (taskFromRemote == null) {
                                    Log.e("FirebaseSyncManager", "Deserialized task from Firestore is null: ${dc.document.id}")
                                    continue
                                }

                                Log.d("FirebaseSyncManager", "Processing remote change: Type=${dc.type}, TaskID=${taskFromRemote.id}")
                                when (dc.type) {
                                    DocumentChange.Type.ADDED -> {
                                        Log.d("FirebaseSyncManager", "Remote ADDED: ${taskFromRemote.id}. Inserting locally.")
                                        db.taskDao().insertTask(taskFromRemote)
                                    }
                                    DocumentChange.Type.MODIFIED -> {
                                        Log.d("FirebaseSyncManager", "Remote MODIFIED: ${taskFromRemote.id}. Updating locally.")
                                        db.taskDao().updateTask(taskFromRemote)
                                    }
                                    DocumentChange.Type.REMOVED -> {
                                        Log.d("FirebaseSyncManager", "Remote REMOVED: ${taskFromRemote.id}. Deleting locally.")
                                        db.taskDao().deleteTask(taskFromRemote)
                                    }
                                }
                            }
                            // After the first snapshot is processed, complete the deferred.
                            // Subsequent snapshots won't try to complete it again.
                            if (!initialSyncCompleter.isCompleted) {
                                initialSyncCompleter.complete(Unit)
                                Log.d("FirebaseSyncManager", "Initial Firestore snapshot processed.")
                            }
                        }
                    } else if (!initialSyncCompleter.isCompleted) {
                        // Handle null snapshots during initial phase if necessary
                        initialSyncCompleter.complete(Unit) // Or complete exceptionally
                        Log.d("FirebaseSyncManager", "Initial Firestore snapshot was null or empty.")
                    }
                }

            // Wait for the initial snapshot data to be processed before syncing local to remote.
            // Add a timeout to prevent waiting indefinitely if Firestore is offline.
            try {
                withTimeout(15_000L) { // 15-second timeout
                    Log.d("FirebaseSyncManager", "Waiting for initial Firestore snapshot...")
                    initialSyncCompleter.await()
                    Log.d("FirebaseSyncManager", "Initial Firestore snapshot received. Proceeding with local to remote sync.")
                }
            } catch (timeout: TimeoutCancellationException) {
                Log.w("FirebaseSyncManager", "Timeout waiting for initial Firestore snapshot. Local data might not be perfectly reconciled if Firestore was offline.")
                // Decide if you still want to proceed with Part 1 or handle this error differently
            } catch (e: Exception) {
                Log.e("FirebaseSyncManager", "Error waiting for initial Firestore snapshot.", e)
                // Handle this error
            }
            val TAG = "task sync"
            // Part 1: Sync local changes TO Firestore (Now runs AFTER initial remote state is processed)
            // This Flow will collect current Room state, which should now reflect any deletions from Firestore.
            db.taskDao().getAllTasksIncludingDeleted().collect { tasksInRoom -> // Or .first() if it's a one-time sync pass
                for (localTask in tasksInRoom) {
                    if (localTask.isDeleted) {
                        // This task is marked for deletion locally
                        Log.d(TAG, "SyncManager: Found local task ${localTask.id} marked for deletion.")

                        // Attempt to soft-delete it in Firestore
                        val firestoreDeleteSuccess = taskRepository.softDeleteTaskInFirestore(localTask.id)

                        if (firestoreDeleteSuccess) {
                            Log.d(TAG, "SyncManager: Successfully soft-deleted ${localTask.id} in Firestore. Now hard-deleting from Room.")
                            // IMPORTANT: Now that Firestore confirms the delete, remove it from local DB
                            db.taskDao().deleteTask(localTask) // <--- THIS IS THE CRITICAL STEP
                        } else {
                            Log.e(TAG, "SyncManager: Failed to soft-delete ${localTask.id} in Firestore. Will retry on next sync.")
                            // Do nothing to the local task; it remains isDeleted=true and will be retried.
                        }
                    } else {
                        // This task is active locally, sync it to Firestore (create or update)
                        Log.d(TAG, "SyncManager: Syncing active local task ${localTask.id} to Firestore.")
                        taskRepository.createOrUpdateTask(localTask) // Ensure this uses SetOptions.merge()
                    }
                }
            }
        }
    }

    fun syncTaskHistory() {
        scope.launch {
           // val taskHistoryDao = db.taskHistoryDao()

            db.taskHistoryDao().getAllTaskHistory().collect { taskhistories ->
                for (taskhistory in taskhistories) {

                    val success = taskHistoryRepository.update(
                        taskhistory.id, mapOf(
                            "userId" to taskhistory.userId,
                            "completedAt" to taskhistory.completedAt.toDate(),
                            "pointValue" to taskhistory.pointValue,
                            "customCompletionDate" to (taskhistory.customCompletionDate?.toDate()
                                ?:taskhistory.completedAt.toDate()),
                            "isDeleted" to taskhistory.isDeleted,
                            "isArchived" to taskhistory.isArchived,
                            "archivedInResetId" to taskhistory.archivedInResetId,
                            "lastModifiedAt" to taskhistory.lastModifiedAt.toDate()
                        )
                    )
                    if (success) {
                        Log.d("FirebaseSyncManager", "Taskhistory synced successfully: ${taskhistory.id}")
                    } else {
                        Log.e("FirebaseSyncManager", "Error syncing taskhistory: ${taskhistory.id}")
                    }
                }

                firestore.collection(TaskHistory.COLLECTION)
                    .addSnapshotListener { snapshots, e ->
                        if (e != null) {
                            Log.w("FirebaseSyncManager", "Listen failed.", e)
                            return@addSnapshotListener
                        }

                        if (snapshots != null) {
                            for (dc in snapshots.documentChanges) {
                                when (dc.type) {
                                    DocumentChange.Type.ADDED -> {
                                        val taskHistory =
                                            dc.document.toObject(TaskHistory::class.java)
                                        scope.launch {
                                            db.taskHistoryDao().insert(taskHistory)
                                        }
                                        Log.d(
                                            "FirebaseSyncManager",
                                            "New TaskHistory: ${taskHistory.id}"
                                        )
                                    }

                                    DocumentChange.Type.MODIFIED -> {
                                        val taskHistory =
                                            dc.document.toObject(TaskHistory::class.java)
                                        scope.launch {
                                            db.taskHistoryDao().update(taskHistory)
                                        }
                                        Log.d(
                                            "FirebaseSyncManager",
                                            "Modified TaskHistory: ${taskHistory.id}"
                                        )
                                    }

                                    DocumentChange.Type.REMOVED -> {
                                        val taskHistory =
                                            dc.document.toObject(TaskHistory::class.java)
                                        scope.launch {
                                            db.taskHistoryDao().delete(taskHistory)
                                        }
                                        Log.d(
                                            "FirebaseSyncManager",
                                            "Removed TaskHistory: ${taskHistory.id}"
                                        )
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }

    fun syncHouseholdGroups() {
        scope.launch {
            // TODO: Implement household group synchronization logic
        }
    }

    fun syncInvitations() {
        scope.launch {
            // TODO: Implement invitation synchronization logic
        }
    }

    fun syncUsers() {
        scope.launch {
            // TODO: Implement user synchronization logic
        }
    }
}