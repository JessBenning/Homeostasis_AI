package com.homeostasis.app.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.homeostasis.app.data.model.Task
import com.homeostasis.app.data.model.TaskHistory // Assuming TaskHistory has a COLLECTION constant
import com.homeostasis.app.data.remote.TaskHistoryRepository
import com.homeostasis.app.data.remote.TaskRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context // Keep if context is used for something else
//import androidx.compose.ui.input.key.type


@Singleton
class FirebaseSyncManager @Inject constructor(
    private val db: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val taskRepository: TaskRepository,
    private val taskHistoryRepository: TaskHistoryRepository,
    @ApplicationContext private val context: Context // Keep if needed
) {

    // A single scope for all sync operations, using SupervisorJob
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val firestoreListeners = mutableListOf<ListenerRegistration>()


    companion object {
        private const val TAG = "FirebaseSyncManager"
        private const val TAG_REMOTE_TO_LOCAL = "SyncR->L" // Shorter for readability
        private const val TAG_LOCAL_TO_REMOTE = "SyncL->R" // Shorter for readability
        private const val INITIAL_SYNC_TIMEOUT_MS = 20000L // 20 seconds
    }

    init {
        Log.i(TAG, "FirebaseSyncManager Initializing...")

        // --- Task Synchronization ---
        setupRemoteTaskListenerAndInitialFetch()
        setupLocalTaskObserverAndInitialPush()

        // --- TaskHistory Synchronization ---
        setupRemoteTaskHistoryListenerAndInitialFetch()
        setupLocalTaskHistoryObserverAndInitialPush()


        syncScope.launch {
            // Monitor the lifecycle of the syncScope to clean up listeners if it's cancelled
            // (e.g., if FirebaseSyncManager were to be tied to a shorter lifecycle than Singleton)
            syncScope.coroutineContext[Job]?.invokeOnCompletion {
                if (it is CancellationException) {
                    Log.i(TAG, "Sync scope cancelled. Cleaning up Firestore listeners.")
                    cleanupFirestoreListeners()
                }
            }
        }

        Log.i(TAG, "FirebaseSyncManager Initialized and listeners/observers active.")
    }

    private fun cleanupFirestoreListeners() {
        firestoreListeners.forEach { it.remove() }
        firestoreListeners.clear()
        Log.d(TAG, "All Firestore listeners removed.")
    }

    // --- TASKS: Remote (Firestore) -> Local (Room) ---
    private fun setupRemoteTaskListenerAndInitialFetch() {
        Log.d(TAG, "Setting up Remote Task Listener & Initial Fetch.")
        setupFirestoreListenerAndInitialFetch(
            collectionPath = Task.COLLECTION,
            modelClass = Task::class.java,
            entityName = "Task",
            localUpsertOrDelete = { changeType, task ->
                processRemoteTaskChange(changeType, task)
            }
        )
    }

    // --- TASKS: Local (Room) -> Remote (Firestore) ---
    private fun setupLocalTaskObserverAndInitialPush() {
        Log.d(TAG, "Setting up Local Task Observer & Initial Push.")
        // Combine flows for tasks needing sync and tasks needing local deletion sync
        // Ensure your DAO methods return Flow<List<Task>>
        val modifiedTasksFlow = db.taskDao().getModifiedTasksRequiringSync()
        val deletedLocallyTasksFlow = db.taskDao().getLocallyDeletedTasksRequiringSync()

        // It's often cleaner to handle distinct logical operations (create/update vs delete)
        // in separate observers or by checking the state within the pushToFirestore lambda.
        // For this example, we'll assume pushTaskToFirestore handles the isDeletedLocally flag.

        val combinedTasksNeedingSyncFlow = combine(modifiedTasksFlow, deletedLocallyTasksFlow) { modified, deleted ->
            // This simple combination might send duplicates if a task is both modified and marked deleted.
            // A more robust approach might be to ensure your DAO flows are distinct or filter here.
            // For now, let's assume `pushTaskToFirestore` and `updateLocalTaskAfterPush` can handle it.
            (modified + deleted).distinctBy { it.id } // Ensure uniqueness by ID
        }


        setupLocalEntityObserverAndInitialPush(
            entityName = "Task",
            localChangesFlow = combinedTasksNeedingSyncFlow,
            pushItemToFirestore = { task -> pushTaskToFirestore(task) },
            updateLocalAfterPush = { task, success -> updateLocalTaskAfterPush(task, success) },
            getAllLocalNeedingSyncSnapshot = {
                // Fetch both types and combine, ensuring uniqueness
                val modified = db.taskDao().getAllTasksFromRoomSnapshot().filter { it.needsSync && !it.isDeletedLocally }
                val deleted = db.taskDao().getAllTasksFromRoomSnapshot().filter { it.isDeletedLocally } // needsSync is implied for these
                (modified + deleted).distinctBy { it.id }
            }
        )
    }


    // --- TASK HISTORY: Remote (Firestore) -> Local (Room) ---
    private fun setupRemoteTaskHistoryListenerAndInitialFetch() {
        Log.d(TAG, "Setting up Remote TaskHistory Listener & Initial Fetch.")
        setupFirestoreListenerAndInitialFetch(
            collectionPath = TaskHistory.COLLECTION, // Make sure TaskHistory.COLLECTION exists
            modelClass = TaskHistory::class.java,
            entityName = "TaskHistory",
            localUpsertOrDelete = { changeType, history ->
                processRemoteTaskHistoryChange(changeType, history)
            }
        )
    }

    // --- TASK HISTORY: Local (Room) -> Remote (Firestore) ---
    private fun setupLocalTaskHistoryObserverAndInitialPush() {
        Log.d(TAG, "Setting up Local TaskHistory Observer & Initial Push.")
        // Assuming TaskHistory also has needsSync and potentially isDeletedLocally fields.
        // And DAO methods: getModifiedHistoryRequiringSync(): Flow<List<TaskHistory>>,
        // getLocallyDeletedHistoryRequiringSync(): Flow<List<TaskHistory>>,
        // getAllHistorySnapshot(): List<TaskHistory>

        // Placeholder: Replace with actual DAO methods for TaskHistory
        val modifiedHistoryFlow = db.taskHistoryDao().getModifiedTaskHistoryRequiringSync() // Create this if needed
        // val deletedLocallyHistoryFlow = db.taskHistoryDao().getLocallyDeletedTaskHistoryRequiringSync() // Create if needed

        setupLocalEntityObserverAndInitialPush(
            entityName = "TaskHistory",
            localChangesFlow = modifiedHistoryFlow, // Modify if you have deletedLocally for history
            pushItemToFirestore = { history -> pushTaskHistoryToFirestore(history) },
            updateLocalAfterPush = { history, success -> updateLocalTaskHistoryAfterPush(history, success) },
            getAllLocalNeedingSyncSnapshot = {
                db.taskHistoryDao().getAllTaskHistoryBlocking().filter { it.needsSync /* || it.isDeletedLocally */ } // Adjust if isDeletedLocally applies
            }
        )
    }


    // === GENERIC REUSABLE CORE FUNCTIONS ===

    private fun <T : Any> setupFirestoreListenerAndInitialFetch(
        collectionPath: String,
        modelClass: Class<T>,
        entityName: String,
        localUpsertOrDelete: suspend (changeType: DocumentChange.Type, item: T) -> Unit
    ) {
        syncScope.launch {
            val initialSyncCompleter = CompletableDeferred<Unit>()
            var initialSnapshotHandled = false
            val specificTag = "$TAG_REMOTE_TO_LOCAL-$entityName"

            Log.d(specificTag, "Attaching Firestore listener to collection: $collectionPath")

            val listener = firestore.collection(collectionPath)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e(specificTag, "Listen failed for $entityName collection.", error)
                        if (!initialSnapshotHandled) initialSyncCompleter.completeExceptionally(error)
                        return@addSnapshotListener
                    }

                    if (snapshots == null) {
                        Log.w(specificTag, "$entityName snapshot was null.")
                        if (!initialSnapshotHandled) initialSyncCompleter.complete(Unit) // Complete to not block indefinitely
                        return@addSnapshotListener
                    }

                    if (snapshots.isEmpty && !initialSnapshotHandled) {
                        Log.d(specificTag, "Initial $entityName snapshot was empty.")
                    } else if (snapshots.documentChanges.isEmpty() && !snapshots.isEmpty && !initialSnapshotHandled) {
                        // This case means the snapshot has documents, but no changes *in this specific snapshot*
                        // (e.g., initial fetch with existing data but no 'changes' yet).
                        Log.d(specificTag, "Initial $entityName snapshot received with ${snapshots.size()} documents, but no immediate changes in this snapshot delivery.")
                    } else {
                        Log.d(specificTag, "Received ${snapshots.documentChanges.size} $entityName document changes from Firestore.")
                    }


                    // Launch a new coroutine for processing to not block the listener
                    // Especially important if localUpsertOrDelete involves complex logic or further DB ops
                    this.launch { // Use this.launch to inherit from syncScope if desired, or a specific sub-scope
                        for (dc in snapshots.documentChanges) {
                            try {
                                val item = dc.document.toObject(modelClass)
                                if (item == null) {
                                    Log.e(specificTag, "Deserialized $entityName from Firestore is null: ${dc.document.id}")
                                    continue
                                }
                                Log.v(specificTag, "Processing remote change: Type=${dc.type}, ID=${dc.document.id}")
                                localUpsertOrDelete(dc.type, item)
                            } catch (deserializationError: Exception) {
                                Log.e(specificTag, "Error deserializing $entityName from Firestore: ${dc.document.id}", deserializationError)
                            } catch (processingError: Exception) {
                                Log.e(specificTag, "Error processing $entityName remote change for ID ${dc.document.id}", processingError)
                            }
                        }
                        if (!initialSnapshotHandled) {
                            Log.d(specificTag, "Initial Firestore snapshot for $entityName processed.")
                            initialSyncCompleter.complete(Unit)
                            initialSnapshotHandled = true
                        }
                    }
                    if (snapshots.documentChanges.isEmpty() && !initialSnapshotHandled) {
                        // If there were no document changes (e.g. empty collection or first fetch with no diffs)
                        // still mark initial sync as complete.
                        Log.d(specificTag, "Initial Firestore snapshot for $entityName had no document changes, completing.")
                        initialSyncCompleter.complete(Unit)
                        initialSnapshotHandled = true
                    }
                }
            firestoreListeners.add(listener) // Add to list for cleanup

            // Wait for the initial snapshot to be processed or timeout
            // This is primarily for the *initial* local-to-remote pass to ensure it runs after remote data is fetched.
            // Subsequent remote changes are handled by the listener as they come.
            try {
                withTimeout(INITIAL_SYNC_TIMEOUT_MS) {
                    Log.d(specificTag, "Waiting for initial $entityName Firestore snapshot...")
                    initialSyncCompleter.await()
                    Log.d(specificTag, "Initial $entityName Firestore snapshot received/processed.")
                }
            } catch (timeout: TimeoutCancellationException) {
                Log.w(specificTag, "Timeout waiting for initial $entityName Firestore snapshot. Reconciliation might be based on stale local data if Firestore was offline.")
            } catch (e: Exception) {
                Log.e(specificTag, "Error waiting for initial $entityName Firestore snapshot.", e)
            }
        }
    }


    private fun <T : Any> setupLocalEntityObserverAndInitialPush(
        entityName: String,
        localChangesFlow: Flow<List<T>>,
        pushItemToFirestore: suspend (item: T) -> Boolean,
        updateLocalAfterPush: suspend (item: T, success: Boolean) -> Unit,
        getAllLocalNeedingSyncSnapshot: suspend () -> List<T>
    ) {
        val specificTag = "$TAG_LOCAL_TO_REMOTE-$entityName"

        syncScope.launch {
            // --- Initial Push ---
            Log.d(specificTag, "Performing initial local-to-remote sync pass for $entityName.")
            val itemsForInitialPush = getAllLocalNeedingSyncSnapshot()
            if (itemsForInitialPush.isNotEmpty()) {
                Log.i(specificTag, "Found ${itemsForInitialPush.size} $entityName item(s) for initial push.")
                for (item in itemsForInitialPush) {
                    Log.d(specificTag, "Initial push attempt for $entityName ID: (extract ID if possible)") // TODO: Extract ID
                    val success = pushItemToFirestore(item)
                    updateLocalAfterPush(item, success)
                }
            } else {
                Log.d(specificTag, "No $entityName items found needing initial push.")
            }
            Log.d(specificTag, "Finished initial local-to-remote sync pass for $entityName.")

            // --- Ongoing Observation ---
            Log.d(specificTag, "Starting observer for local $entityName changes requiring sync.")
            localChangesFlow
                .distinctUntilChanged() // Only emit when the list content actually changes
                .collect { itemsToPush ->
                    if (itemsToPush.isNotEmpty()) {
                        Log.i(specificTag, "Detected ${itemsToPush.size} local $entityName(s) needing push to Firestore via observer.")
                        for (item in itemsToPush) {
                            Log.d(specificTag, "Observer push attempt for $entityName ID: (extract ID if possible)") // TODO: Extract ID
                            val success = pushItemToFirestore(item)
                            updateLocalAfterPush(item, success)
                        }
                    } else {
                        Log.v(specificTag, "Local $entityName observer received empty list or no changes requiring push.")
                    }
                }
        }
    }


    // === SPECIFIC PROCESSING HELPERS (WORKER FUNCTIONS) ===

    // --- Task Specific Helpers ---

    private suspend fun processRemoteTaskChange(changeType: DocumentChange.Type, remoteTaskSource: Task) {
        val specificTag = "$TAG_REMOTE_TO_LOCAL-Task"
        // Ensure local flags are reset for data coming from remote, unless merging with local changes.
        // The conflict resolution logic from your original syncTasks is crucial here.

        val existingLocalTask = db.taskDao().getTaskById(remoteTaskSource.id)
        val taskFromRemoteClean = remoteTaskSource.copy(needsSync = false, isDeletedLocally = false) // Base clean version


        when (changeType) {
            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                var taskToUpsert: Task
                if (existingLocalTask != null) {
                    if (existingLocalTask.needsSync || existingLocalTask.isDeletedLocally) { // Local has pending changes
                        val remoteTimestamp = taskFromRemoteClean.lastModifiedAt?.seconds ?: 0L
                        val localTimestamp = existingLocalTask.lastModifiedAt?.seconds ?: 0L

                        if (remoteTimestamp > localTimestamp) { // Remote is newer
                            Log.i(specificTag, "Conflict: Remote Task ${taskFromRemoteClean.id} (ts:$remoteTimestamp) is newer than local pending (ts:$localTimestamp). Remote wins, attempting merge.")
                            taskToUpsert = taskFromRemoteClean.copy(
                                // If remote says deleted, local delete intent is fulfilled.
                                // If remote NOT deleted, preserve local delete intent if it existed.
                                isDeletedLocally = if (taskFromRemoteClean.isDeleted) false else existingLocalTask.isDeletedLocally,
                                // If remote state aligns with local delete intent (e.g. both deleted), no need to sync back.
                                // Otherwise, if states differ, local change (e.g., undelete) might still be relevant.
                                needsSync = if (taskFromRemoteClean.isDeleted == existingLocalTask.isDeletedLocally && taskFromRemoteClean.isDeleted) false
                                else if (taskFromRemoteClean.isDeleted != existingLocalTask.isDeletedLocally) true // If remote isDeleted changed from what local delete intent was, needsSync=true
                                else existingLocalTask.needsSync // Preserve if no major misalignment or if remote is not deleted and local has other changes.
                            )
                            Log.d(specificTag,"Upserting Task from newer remote: ${taskToUpsert.id}, isDeleted=${taskToUpsert.isDeleted}, isDeletedLocally=${taskToUpsert.isDeletedLocally}, needsSync=${taskToUpsert.needsSync}")

                        } else { // Local is same or newer, or remote has no timestamp
                            Log.i(specificTag, "Conflict: Local Task ${existingLocalTask.id} (ts:$localTimestamp) has pending sync and is same/newer than remote (ts:$remoteTimestamp). Letting local sync attempt first.")
                            // Do not update from remote yet, let local sync go first.
                            // The local observer will pick it up.
                            return // Exit without changing local data
                        }
                    } else { // No local pending changes, remote data is fine to apply.
                        taskToUpsert = taskFromRemoteClean
                        Log.d(specificTag, "Remote Task ${changeType}: ${taskToUpsert.id}. Upserting locally. isDeleted=${taskToUpsert.isDeleted}")
                    }
                } else { // New task from remote, never seen locally.
                    taskToUpsert = taskFromRemoteClean
                    Log.d(specificTag, "Remote Task ADDED (new): ${taskToUpsert.id}. Inserting locally. isDeleted=${taskToUpsert.isDeleted}")
                }
                db.taskDao().upsertTask(taskToUpsert)
            }
            DocumentChange.Type.REMOVED -> {
                Log.d(specificTag, "Remote Task REMOVED: ${taskFromRemoteClean.id}. Hard-deleting locally.")
                db.taskDao().hardDeleteTaskFromRoom(taskFromRemoteClean) // Assumes hardDeleteTaskFromRoom exists
            }
        }
    }

    private suspend fun pushTaskToFirestore(task: Task): Boolean {
        val specificTag = "$TAG_LOCAL_TO_REMOTE-Task"
        return try {
            if (task.isDeletedLocally) {
                Log.d(specificTag, "Pushing Task ${task.id} to Firestore (Soft Delete).")
                taskRepository.softDeleteTaskInFirestore(task.id)
            } else {
                Log.d(specificTag, "Pushing Task ${task.id} to Firestore (Create/Update). isDeleted=${task.isDeleted}")
                taskRepository.createOrUpdateTaskInFirestore(task.copy(lastModifiedAt = Timestamp.now())) // Ensure lastModifiedAt is current for this push
            }
        } catch (e: Exception) {
            Log.e(specificTag, "Error pushing Task ${task.id} to Firestore.", e)
            false
        }
    }

    private suspend fun updateLocalTaskAfterPush(task: Task, firestoreSuccess: Boolean) {
        val specificTag = "$TAG_LOCAL_TO_REMOTE-Task"
        if (firestoreSuccess) {
            val updatedTask = if (task.isDeletedLocally) {
                task.copy(
                    isDeleted = true,          // Align with Firestore (now soft-deleted)
                    isDeletedLocally = false,  // Local delete intention processed
                    needsSync = false,         // Synced
                    lastModifiedAt = Timestamp.now() // Reflect the sync time
                )
            } else {
                task.copy(
                    needsSync = false,
                    lastModifiedAt = Timestamp.now() // Reflect the sync time (Firestore server ts will be master)
                )
            }
            db.taskDao().upsertTask(updatedTask)
            Log.d(specificTag, "Successfully updated local Task ${task.id} flags after Firestore push.")
        } else {
            Log.e(specificTag, "Firestore push failed for Task ${task.id}. Local flags (needsSync, isDeletedLocally) remain unchanged for retry.")
            // Task remains with needsSync = true (and isDeletedLocally if it was true)
        }
    }

    // --- TaskHistory Specific Helpers ---

    private suspend fun processRemoteTaskHistoryChange(changeType: DocumentChange.Type, remoteHistorySource: TaskHistory) {
        val specificTag = "$TAG_REMOTE_TO_LOCAL-TaskHistory"
        // Apply similar conflict resolution as for Tasks if TaskHistory can also have local pending changes.
        // For simplicity, this version assumes remote always wins or simple upsert/delete.
        val historyFromRemoteClean = remoteHistorySource.copy(needsSync = false /*, isDeletedLocally = false */) // Add if applicable

        Log.d(specificTag, "Processing remote TaskHistory change: Type=$changeType, ID=${historyFromRemoteClean.id}")
        when (changeType) {
            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                // TODO: Implement proper conflict resolution for TaskHistory if needed, similar to Tasks
                // For now, simple upsert:
                db.taskHistoryDao().insertOrUpdate(historyFromRemoteClean) // Ensure insertOrUpdate exists
                Log.d(specificTag, "Upserted TaskHistory ${historyFromRemoteClean.id} from remote.")
            }
            DocumentChange.Type.REMOVED -> {
                db.taskHistoryDao().delete(historyFromRemoteClean) // Ensure delete takes TaskHistory object or ID
                Log.d(specificTag, "Deleted TaskHistory ${historyFromRemoteClean.id} locally as per remote.")
            }
        }
    }

    private suspend fun pushTaskHistoryToFirestore(history: TaskHistory): Boolean {
        val specificTag = "$TAG_LOCAL_TO_REMOTE-TaskHistory"
        return try {
            // Assuming TaskHistory doesn't have 'isDeletedLocally' logic for now.
            // If it does, add similar if/else as pushTaskToFirestore.
            Log.d(specificTag, "Pushing TaskHistory ${history.id} to Firestore.")
            // Ensure your repository method sets a server timestamp for lastModifiedAt.
            taskHistoryRepository.createOrUpdateFirestoreTaskHistory(history)
        } catch (e: Exception) {
            Log.e(specificTag, "Error pushing TaskHistory ${history.id} to Firestore.", e)
            false
        }
    }

    private suspend fun updateLocalTaskHistoryAfterPush(history: TaskHistory, firestoreSuccess: Boolean) {
        val specificTag = "$TAG_LOCAL_TO_REMOTE-TaskHistory"
        if (firestoreSuccess) {
            // Assuming TaskHistoryDao has an update method that takes the object
            db.taskHistoryDao().update(history.copy(needsSync = false, lastModifiedAt = Timestamp.now()))
            Log.d(specificTag, "Successfully updated local TaskHistory ${history.id} flags after Firestore push.")
        } else {
            Log.e(specificTag, "Firestore push failed for TaskHistory ${history.id}. Local 'needsSync' flag remains true for retry.")
        }
    }


    // --- Other Sync Functions (Stubs) ---
    fun syncHouseholdGroups() {
        syncScope.launch {
            // TODO: Implement household group synchronization logic
            Log.d(TAG, "syncHouseholdGroups called - (Implementation Pending)")
        }
    }

    fun syncInvitations() {
        syncScope.launch {
            // TODO: Implement invitation synchronization logic
            Log.d(TAG, "syncInvitations called - (Implementation Pending)")
        }
    }

    fun syncUsers() {
        syncScope.launch {
            // TODO: Implement user synchronization logic
            Log.d(TAG, "syncUsers called - (Implementation Pending)")
        }
    }
}