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
// import kotlinx.coroutines.launch // kotlinx.coroutines.flow.launchIn can be an alternative for some scenarios
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context // Keep if context is used for something else
import com.homeostasis.app.data.remote.UserRepository
import kotlinx.coroutines.tasks.await
import java.io.File

//import androidx.compose.ui.input.key.type


@Singleton
class FirebaseSyncManager @Inject constructor(
    private val db: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val taskRepository: TaskRepository,
    private val taskHistoryRepository: TaskHistoryRepository,
    private val userRepository: UserRepository,
    private val householdGroupIdProvider: HouseholdGroupIdProvider,
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
        syncScope.launch {
            setupRemoteTaskListenerAndInitialFetch()
        }
        syncScope.launch {
            setupLocalTaskObserverAndInitialPush()
        }

        // --- TaskHistory Synchronization ---
        syncScope.launch {
            setupRemoteTaskHistoryListenerAndInitialFetch()
        }
        syncScope.launch {
            setupLocalTaskHistoryObserverAndInitialPush()
        }


        // --- User Synchronization ---
        syncScope.launch {
            syncUsers() // Start user synchronization
        }

        // --- User Synchronization (Local -> Remote) ---
        syncScope.launch {
            setupLocalUserObserverAndInitialPush() // Start local-to-remote user sync
        }

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
    private suspend fun setupRemoteTaskListenerAndInitialFetch() { // Marked suspend
        Log.d(TAG, "Setting up Remote Task Listener & Initial Fetch.")
        householdGroupIdProvider.getHouseholdGroupId().collect { householdGroupId -> // collect is a suspend function
            householdGroupId?.let {
                // Assuming setupFirestoreListenerAndInitialFetch is NOT a suspend fun itself,
                // but its lambdas might call suspend funs or it might be a flow collector.
                // If it internally collects flows, it should also be suspend.
                setupFirestoreListenerAndInitialFetch(
                    collectionPath = Task.COLLECTION,
                    modelClass = Task::class.java,
                    entityName = "Task",
                    localUpsertOrDelete = { changeType, task ->
                        // processRemoteTaskChange is suspend, so this lambda must be called from a coroutine
                        // This implies setupFirestoreListenerAndInitialFetch should handle launching a coroutine
                        // for this lambda or be a suspend function itself. For now, assuming it handles it.
                        processRemoteTaskChange(changeType, task, householdGroupId)
                    }
                )
            }
        }
    }

    // --- TASKS: Local (Room) -> Remote (Firestore) ---
    private suspend fun setupLocalTaskObserverAndInitialPush() { // Marked suspend
        Log.d(TAG, "Setting up Local Task Observer & Initial Push.")
        // flatMapLatest and combine are flow operators, the final collect will be in setupLocalEntityObserverAndInitialPush
        val modifiedTasksFlow = householdGroupIdProvider.getHouseholdGroupId().flatMapLatest { householdGroupId ->
            householdGroupId?.let {
                db.taskDao().getModifiedTasksRequiringSync(it)
            } ?: flowOf(emptyList())
        }
        val deletedLocallyTasksFlow = householdGroupIdProvider.getHouseholdGroupId().flatMapLatest { householdGroupId ->
            householdGroupId?.let {
                db.taskDao().getLocallyDeletedTasksRequiringSync(it)
            } ?: flowOf(emptyList())
        }

        val combinedTasksNeedingSyncFlow = combine(modifiedTasksFlow, deletedLocallyTasksFlow) { modified, deleted ->
            (modified + deleted).distinctBy { it.id }
        }

        // If setupLocalEntityObserverAndInitialPush internally collects localChangesFlow,
        // then it MUST be a suspend function.
        // The lambda for getAllLocalNeedingSyncSnapshot calls .first() which IS a suspend function.
        // So, setupLocalEntityObserverAndInitialPush MUST handle invoking this lambda from a coroutine
        // or be a suspend function itself.
        setupLocalEntityObserverAndInitialPush(
            entityName = "Task",
            localChangesFlow = combinedTasksNeedingSyncFlow,
            pushItemToFirestore = { task -> pushTaskToFirestore(task) }, // pushTaskToFirestore is suspend
            updateLocalAfterPush = { task, success -> updateLocalTaskAfterPush(task, success) }, // updateLocalTaskAfterPush is suspend
            getAllLocalNeedingSyncSnapshot = { // This lambda calls .first() which is suspend
                // This lambda will be called by setupLocalEntityObserverAndInitialPush.
                // It needs to be called from a coroutine scope.
                runBlocking { // Or ensure setupLocalEntityObserverAndInitialPush calls this from a coroutine
                    householdGroupIdProvider.getHouseholdGroupId().first()?.let { householdGroupId ->
                        val modified = db.taskDao().getAllTasksFromRoomSnapshot(householdGroupId).filter { it.needsSync && !it.isDeletedLocally }
                        val deleted = db.taskDao().getAllTasksFromRoomSnapshot(householdGroupId).filter { it.isDeletedLocally }
                        (modified + deleted).distinctBy { it.id }
                    } ?: emptyList()
                }
            }
        )
    }


    // --- TASK HISTORY: Remote (Firestore) -> Local (Room) ---
    private suspend fun setupRemoteTaskHistoryListenerAndInitialFetch() { // Marked suspend
        Log.d(TAG, "Setting up Remote TaskHistory Listener & Initial Fetch.")
        householdGroupIdProvider.getHouseholdGroupId().collect { householdGroupId -> // collect is suspend
            householdGroupId?.let {
                // See comments for setupRemoteTaskListenerAndInitialFetch regarding setupFirestoreListenerAndInitialFetch
                setupFirestoreListenerAndInitialFetch(
                    collectionPath = TaskHistory.COLLECTION,
                    modelClass = TaskHistory::class.java,
                    entityName = "TaskHistory",
                    localUpsertOrDelete = { changeType, history ->
                        // processRemoteTaskHistoryChange is suspend
                        processRemoteTaskHistoryChange(changeType, history, householdGroupId)
                    }
                )
            }
        }
    }

    // --- TASK HISTORY: Local (Room) -> Remote (Firestore) ---
    private suspend fun setupLocalTaskHistoryObserverAndInitialPush() { // Marked suspend
        Log.d(TAG, "Setting up Local TaskHistory Observer & Initial Push.")
        val modifiedHistoryFlow = householdGroupIdProvider.getHouseholdGroupId().flatMapLatest { householdGroupId ->
            householdGroupId?.let {
                db.taskHistoryDao().getModifiedTaskHistoryRequiringSync(it)
            } ?: flowOf(emptyList())
        }

        // Add flow for locally deleted task history
        val deletedLocallyHistoryFlow = householdGroupIdProvider.getHouseholdGroupId().flatMapLatest { householdGroupId ->
            householdGroupId?.let {
                db.taskHistoryDao().getLocallyDeletedTaskHistoryRequiringSync(it)
            } ?: flowOf(emptyList())
        }

        // Combine modified and locally deleted history flows
        val combinedHistoryNeedingSyncFlow = combine(modifiedHistoryFlow, deletedLocallyHistoryFlow) { modified, deleted ->
            (modified + deleted).distinctBy { it.id }
        }

        // See comments for setupLocalTaskObserverAndInitialPush regarding setupLocalEntityObserverAndInitialPush
        setupLocalEntityObserverAndInitialPush(
            entityName = "TaskHistory",
            localChangesFlow = combinedHistoryNeedingSyncFlow, // Use the combined flow
            pushItemToFirestore = { history -> pushTaskHistoryToFirestore(history) }, // pushTaskHistoryToFirestore is suspend
            updateLocalAfterPush = { history, success -> updateLocalTaskHistoryAfterPush(history, success) }, // updateLocalTaskHistoryAfterPush is suspend
            getAllLocalNeedingSyncSnapshot =  { // This lambda calls .first() which is suspend
                runBlocking { // Or ensure setupLocalEntityObserverAndInitialPush calls this from a coroutine
                    householdGroupIdProvider.getHouseholdGroupId().first()?.let { householdGroupId ->
                        // Include both modified and locally deleted in the snapshot
                        val modified = db.taskHistoryDao().getAllTaskHistoryBlocking(householdGroupId).filter { it.needsSync && !it.isDeletedLocally }
                        val deleted = db.taskHistoryDao().getAllTaskHistoryBlocking(householdGroupId).filter { it.isDeletedLocally }
                        (modified + deleted).distinctBy { it.id }
                    } ?: emptyList()
                }
            }
        )
    }


    // === SPECIFIC PROCESSING HELPERS (WORKER FUNCTIONS) ===

    // --- Task Specific Helpers ---

    private suspend fun processRemoteTaskChange(changeType: DocumentChange.Type, remoteTaskSource: Task, householdGroupId: String) {
        val specificTag = "$TAG_REMOTE_TO_LOCAL-Task"
        val existingLocalTask = db.taskDao().getTaskById(remoteTaskSource.id, householdGroupId)
        val taskFromRemoteClean = remoteTaskSource.copy(needsSync = false, isDeletedLocally = false)


        when (changeType) {
            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                var taskToUpsert: Task
                if (existingLocalTask != null) {
                    if (existingLocalTask.needsSync || existingLocalTask.isDeletedLocally) {
                        val remoteTimestamp = taskFromRemoteClean.lastModifiedAt?.seconds ?: 0L
                        val localTimestamp = existingLocalTask.lastModifiedAt?.seconds ?: 0L

                        if (remoteTimestamp > localTimestamp) {
                            Log.i(specificTag, "Conflict: Remote Task ${taskFromRemoteClean.id} (ts:$remoteTimestamp) is newer than local pending (ts:$localTimestamp). Remote wins, attempting merge.")
                            taskToUpsert = existingLocalTask.copy(
                                title = taskFromRemoteClean.title,
                                description = taskFromRemoteClean.description,
                                isCompleted = taskFromRemoteClean.isCompleted,
                                isDeleted = taskFromRemoteClean.isDeleted,
                                lastModifiedAt = taskFromRemoteClean.lastModifiedAt,
                                needsSync = if (taskFromRemoteClean.isDeleted && existingLocalTask.isDeletedLocally) false else existingLocalTask.needsSync,
                                isDeletedLocally = if (taskFromRemoteClean.isDeleted) false else existingLocalTask.isDeletedLocally
                            )
                            Log.d(specificTag,"Upserting Task from newer remote: ${taskToUpsert.id}, isDeleted=${taskToUpsert.isDeleted}, isDeletedLocally=${taskToUpsert.isDeletedLocally}, needsSync=${taskToUpsert.needsSync}")

                        } else {
                            Log.i(specificTag, "Conflict: Local Task ${existingLocalTask.id} (ts:$localTimestamp) has pending sync and is same/newer than remote (ts:$remoteTimestamp). Letting local sync attempt first.")
                            return
                        }
                    } else {
                        taskToUpsert = taskFromRemoteClean
                        Log.d(specificTag, "Remote Task ${changeType}: ${taskToUpsert.id}. Upserting locally. isDeleted=${taskToUpsert.isDeleted}")
                    }
                } else {
                    taskToUpsert = taskFromRemoteClean
                    Log.d(specificTag, "Remote Task ADDED (new): ${taskToUpsert.id}. Inserting locally. isDeleted=${taskToUpsert.isDeleted}")
                }
                db.taskDao().upsertTask(taskToUpsert)
            }
            DocumentChange.Type.REMOVED -> {
                Log.d(specificTag, "Remote Task REMOVED: ${taskFromRemoteClean.id}. Hard-deleting locally.")
                db.taskDao().hardDeleteTaskFromRoom(taskFromRemoteClean)
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
                taskRepository.createOrUpdateTaskInFirestore(task.copy(lastModifiedAt = Timestamp.now()))
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
                    isDeleted = true,
                    isDeletedLocally = false,
                    needsSync = false,
                    lastModifiedAt = Timestamp.now()
                )
            } else {
                task.copy(
                    needsSync = false,
                    lastModifiedAt = Timestamp.now()
                )
            }
            db.taskDao().upsertTask(updatedTask)
            Log.d(specificTag, "Successfully updated local Task ${task.id} flags after Firestore push.")
        } else {
            Log.e(specificTag, "Firestore push failed for Task ${task.id}. Local flags (needsSync, isDeletedLocally) remain unchanged for retry.")
        }
    }

    // --- User Specific Helpers ---

    private suspend fun processRemoteUserChange(changeType: DocumentChange.Type, remoteUserSource: com.homeostasis.app.data.model.User, householdGroupId: String) {
        val specificTag = "$TAG_REMOTE_TO_LOCAL-User"
        val userFromRemoteClean = remoteUserSource.copy(needsSync = false, isDeletedLocally = false)

        Log.d(specificTag, "Processing remote User change: Type=$changeType, ID=${userFromRemoteClean.id}")
        when (changeType) {
            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                // Upsert the user into the local database
                db.userDao().upsertUser(userFromRemoteClean)
                Log.d(specificTag, "Upserted User ${userFromRemoteClean.id} from remote.")

                // After upserting, check if there's a profile image URL and initiate download
                if (!userFromRemoteClean.profileImageUrl.isNullOrBlank()) {
                    // Launch a separate coroutine for the download to avoid blocking the sync listener
                    syncScope.launch {
                        downloadAndSaveProfilePicture(userFromRemoteClean.id, userFromRemoteClean.profileImageUrl!!)
                    }
                } else {
                    // If no profile image URL, ensure any existing local file is removed (optional, depending on desired behavior)
                    // For now, we'll leave existing local files if the URL is removed remotely.
                }
            }
            DocumentChange.Type.REMOVED -> {
                // If a user is removed from Firestore, remove them from the local Room DB
                db.userDao().deleteUser(userFromRemoteClean)
                Log.d(specificTag, "Deleted User ${userFromRemoteClean.id} locally as per remote.")
                // Also delete the local profile picture file
                deleteLocalProfilePicture(userFromRemoteClean.id)
            }
        }
    }

    // --- TaskHistory Specific Helpers ---

    private suspend fun processRemoteTaskHistoryChange(changeType: DocumentChange.Type, remoteHistorySource: TaskHistory, householdGroupId: String) {
        val specificTag = "$TAG_REMOTE_TO_LOCAL-TaskHistory"
        val historyFromRemoteClean = remoteHistorySource.copy(needsSync = false)

        Log.d(specificTag, "Processing remote TaskHistory change: Type=$changeType, ID=${historyFromRemoteClean.id}")
        when (changeType) {
            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                // TODO: Implement proper conflict resolution for TaskHistory if needed, similar to Tasks
                db.taskHistoryDao().insertOrUpdate(historyFromRemoteClean);
                Log.d(specificTag, "Upserted TaskHistory ${historyFromRemoteClean.id} from remote.")
            }
            DocumentChange.Type.REMOVED -> {
                db.taskHistoryDao().delete(historyFromRemoteClean)
                Log.d(specificTag, "Deleted TaskHistory ${historyFromRemoteClean.id} locally as per remote.")
            }
        }
    }

    private suspend fun pushTaskHistoryToFirestore(history: TaskHistory): Boolean {
        val specificTag = "$TAG_LOCAL_TO_REMOTE-TaskHistory"
        return try {
            if (history.isDeletedLocally) {
                Log.d(specificTag, "Pushing TaskHistory ${history.id} to Firestore (Delete).")
                // Assuming taskHistoryRepository has a delete function
                taskHistoryRepository.deleteFirestoreTaskHistory(history.id) // Assuming delete function takes ID
            } else {
                Log.d(specificTag, "Pushing TaskHistory ${history.id} to Firestore (Create/Update).")
                taskHistoryRepository.createOrUpdateFirestoreTaskHistory(history)
            }
        } catch (e: Exception) {
            Log.e(specificTag, "Error pushing TaskHistory ${history.id} to Firestore.", e)
            false
        }
    }

    private suspend fun updateLocalTaskHistoryAfterPush(history: TaskHistory, firestoreSuccess: Boolean) {
        val specificTag = "$TAG_LOCAL_TO_REMOTE-TaskHistory"
        if (firestoreSuccess) {
            if (history.isDeletedLocally) {
                // If it was locally deleted and remote push was successful, hard delete locally
                db.taskHistoryDao().delete(history) // Assuming TaskHistoryDao has a delete(TaskHistory) function
                Log.d(specificTag, "Successfully hard-deleted local TaskHistory ${history.id} after successful remote deletion.")
            } else {
                // If it was modified (not deleted) and remote push was successful, just update needsSync flag
                db.taskHistoryDao().insertOrUpdate(history.copy(needsSync = false, lastModifiedAt = Timestamp.now())) // Assuming TaskHistory has householdGroupId
                Log.d(specificTag, "Successfully updated local TaskHistory ${history.id} flags after Firestore push.")
            }
        } else {
            Log.e(specificTag, "Firestore push failed for TaskHistory ${history.id}. Local 'needsSync' flag remains true for retry.")
        }
    }


    // --- Generic Setup Functions (Placeholder - review their internal logic for suspend calls) ---

    // IMPORTANT: Review the internal implementation of these generic functions.
    // If they call 'collect' on a flow, or if their lambda parameters call suspend functions,
    // they need to be 'suspend' functions themselves or manage coroutine scopes internally.

    private suspend fun <T : Any> setupFirestoreListenerAndInitialFetch( // Marked suspend
        collectionPath: String,
        modelClass: Class<T>,
        entityName: String,
        localUpsertOrDelete: suspend (DocumentChange.Type, T) -> Unit // Made suspend
    ) {
        // This function sets up a Firestore listener. The listener's callback will receive data.
        // The 'localUpsertOrDelete' lambda is suspend, so it must be called from a coroutine.
        // This implies that the Firestore listener callback should launch a coroutine.
        Log.d(TAG, "Setting up Firestore listener for $entityName on path $collectionPath")

        // Get the current household group ID
        val householdGroupId = householdGroupIdProvider.getHouseholdGroupId().first() // Use first() to get the current value

        if (householdGroupId == null) {
            Log.w(TAG, "Cannot set up Firestore listener for $entityName: Household Group ID is null.")
            return // Cannot set up listener without a household group ID
        }

        val listener = firestore.collection(collectionPath)
            .whereEqualTo("householdGroupId", householdGroupId) // Filter by householdGroupId
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Listen failed for $entityName.", e)
                    return@addSnapshotListener
                }

                syncScope.launch { // Launch coroutine to handle snapshots and call suspend lambda
                    for (dc in snapshots!!.documentChanges) {
                        val item = dc.document.toObject(modelClass)
                        // It's possible toObject fails if modelClass doesn't match Firestore doc
                        if (item != null) {
                            Log.d(TAG, "$entityName: Remote change: ${dc.type} for doc ${dc.document.id}")
                            try {
                                localUpsertOrDelete(dc.type, item)
                            } catch (ex: Exception) {
                                Log.e(TAG, "Error processing $entityName remote change for ${dc.document.id}", ex)
                            }
                        } else {
                            Log.w(TAG, "$entityName: Failed to convert document ${dc.document.id} to $modelClass")
                        }
                    }
                }
            }
        firestoreListeners.add(listener)

        // Initial fetch logic might be needed here if the listener doesn't immediately provide all data
        // For example, a one-time get() call. Ensure it's also within a coroutine if it's a suspend call.
        // For now, assuming addSnapshotListener provides initial data.
    }


    private suspend fun <T : Any> setupLocalEntityObserverAndInitialPush( // Marked suspend
        entityName: String,
        localChangesFlow: Flow<List<T>>,
        pushItemToFirestore: suspend (T) -> Boolean, // Made suspend
        updateLocalAfterPush: suspend (T, Boolean) -> Unit, // Made suspend
        getAllLocalNeedingSyncSnapshot: suspend () -> List<T> // Made suspend as it calls .first()
    ) {
        Log.d(TAG, "Setting up Local Entity Observer for $entityName.")

        // Initial push for any items that were modified while offline or before listener was active
        // This call to getAllLocalNeedingSyncSnapshot is now fine as this function is suspend
        val initialItemsToSync = getAllLocalNeedingSyncSnapshot()
        if (initialItemsToSync.isNotEmpty()) {
            Log.i(TAG, "$entityName: Found ${initialItemsToSync.size} items needing initial sync.")
            initialItemsToSync.forEach { item ->
                // This is inside a suspend function, so direct calls to suspend functions are okay.
                val success = pushItemToFirestore(item)
                updateLocalAfterPush(item, success)
            }
        } else {
            Log.d(TAG, "$entityName: No items found needing initial sync.")
        }

        // Observe ongoing local changes
        // The collect itself is a suspend function call
        localChangesFlow
            .conflate() // process only the latest if processing is slow
            .collect { items ->
                if (items.isNotEmpty()) {
                    Log.d(TAG, "$entityName: Detected ${items.size} local changes to sync.")
                    for (item in items) {
                        val success = pushItemToFirestore(item)
                        updateLocalAfterPush(item, success)
                    }
                }
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

    /**
     * Sets up the local observer for User entities and pushes changes to Firestore.
     */
    private suspend fun setupLocalUserObserverAndInitialPush() { // Marked suspend
        Log.d(TAG, "Setting up Local User Observer & Initial Push.")
        val modifiedUsersFlow = householdGroupIdProvider.getHouseholdGroupId().flatMapLatest { householdGroupId ->
            householdGroupId?.let {
                db.userDao().getUsersRequiringSync(it) // Use the new query
            } ?: flowOf(emptyList())
        }

        setupLocalEntityObserverAndInitialPush(
            entityName = "User",
            localChangesFlow = modifiedUsersFlow,
            pushItemToFirestore = { user -> // Modified lambda to handle image upload using derived local path
                val specificTag = "$TAG_LOCAL_TO_REMOTE-User"
                var overallSuccess = false // Track overall success

                try {
                    var userToPush = user
                    var uploadSuccess = true
                    var cloudStorageUrl: String? = null

                    // Derive the local profile picture file path
                    val localFilePath = File(context.filesDir, "profile_picture_${user.id}.jpg").absolutePath
                    val localFile = File(localFilePath)

                    // Check if the local profile image file exists
                    if (localFile.exists()) {
                        Log.d(specificTag, "Uploading profile picture for user ${user.id} from local file: ${localFile.path}")
                        // Upload image to Firebase Storage
                        cloudStorageUrl = userRepository.uploadProfilePicture(user.id, localFile) // Use UserRepository for upload
                        uploadSuccess = cloudStorageUrl != null

                        if (uploadSuccess) {
                            Log.d(specificTag, "Profile picture uploaded successfully for user ${user.id}. URL: $cloudStorageUrl")
                            // Update the user object with the Cloud Storage URL
                            userToPush = user.copy(profileImageUrl = cloudStorageUrl!!)
                            // Do NOT delete the local file here, as per user requirement
                        } else {
                            Log.e(specificTag, "Failed to upload profile picture for user ${user.id}.")
                            // If upload fails, the overall push fails.
                            overallSuccess = false
                        }
                    }

                    // If image upload was successful (or no image needed upload), push the user data to Firestore
                    if (uploadSuccess) {
                         val firestorePushSuccess = userRepository.pushUserToFirestore(userToPush.copy(lastModifiedAt = Timestamp.now())) // Update timestamp before pushing
                         overallSuccess = firestorePushSuccess
                         if (!firestorePushSuccess) {
                             Log.e(specificTag, "Firestore push failed for user ${user.id}.")
                         }
                    }


                } catch (e: Exception) {
                    Log.e(specificTag, "Error during user profile picture upload or Firestore push for user ${user.id}.", e)
                    overallSuccess = false // Indicate push failed
                }
                overallSuccess // Return overall success status
            },
            updateLocalAfterPush = { user, success -> // Modified lambda - no longer clears local path
                val specificTag = "$TAG_LOCAL_TO_REMOTE-User"
                if (success) {
                    // Update the local user to set needsSync to false
                    val updatedUser = user.copy(
                        needsSync = false,
                        lastModifiedAt = Timestamp.now() // Update timestamp locally after successful sync
                    )
                    db.userDao().upsertUser(updatedUser)
                    Log.d(specificTag, "Successfully updated local User ${user.id} flags after Firestore push.")
                } else {
                    Log.e(specificTag, "Firestore push failed for User ${user.id}. Local 'needsSync' flag remains true for retry.")
                }
            },
            getAllLocalNeedingSyncSnapshot =  { // This lambda calls .first() which is suspend
                runBlocking { // Or ensure setupLocalEntityObserverAndInitialPush calls this from a coroutine
                    householdGroupIdProvider.getHouseholdGroupId().first()?.let { householdGroupId ->
                        db.userDao().getUsersRequiringSync(householdGroupId).first() // Get snapshot for initial push
                    } ?: emptyList()
                }
            }
        )
    }

    /**
     * Pushes a locally modified User to Firestore using UserRepository.
     */
    private suspend fun pushUserToFirestore(user: com.homeostasis.app.data.model.User): Boolean {
        val specificTag = "$TAG_LOCAL_TO_REMOTE-User"
        return try {
            Log.d(specificTag, "Pushing User ${user.id} to Firestore.")
            // Use the pushUserToFirestore function in UserRepository
            userRepository.pushUserToFirestore(user.copy(lastModifiedAt = Timestamp.now()))
        } catch (e: Exception) {
            Log.e(specificTag, "Error pushing User ${user.id} to Firestore.", e)
            false
        }
    }

    /**
     * Updates the local User entity
     */
    private suspend fun updateLocalUserAfterPush(user: com.homeostasis.app.data.model.User, firestoreSuccess: Boolean) {
        val specificTag = "$TAG_LOCAL_TO_REMOTE-User"
        if (firestoreSuccess) {
            // Update the local user to set needsSync to false
            val updatedUser = user.copy(
                needsSync = false,
                lastModifiedAt = Timestamp.now() // Update timestamp locally after successful sync
            )
            db.userDao().upsertUser(updatedUser)
            Log.d(specificTag, "Successfully updated local User ${user.id} flags after Firestore push.")
        } else {
            Log.e(specificTag, "Firestore push failed for User ${user.id}. Local 'needsSync' flag remains true for retry.")
        }
    }

    /**
     * Downloads a profile picture from a URL and saves it to local storage.
     *
     * @param userId The ID of the user the picture belongs to.
     * @param imageUrl The URL of the profile picture.
     */
    private suspend fun downloadAndSaveProfilePicture(userId: String, imageUrl: String) {
        val specificTag = "$TAG_REMOTE_TO_LOCAL-User-Download"
        try {
            val localFile = File(context.filesDir, "profile_picture_${userId}.jpg")

            // Use Glide or a similar library to download the image
            // Glide requires a Context and runs asynchronously.
            // Since this is a suspend function, we need to bridge the suspend world with Glide's async world.
            // A simple way is to use Glide's .downloadOnly() and await its completion.

            val future = com.bumptech.glide.Glide.with(context)
                .asFile()
                .load(imageUrl)
                .downloadOnly(com.bumptech.glide.request.target.Target.SIZE_ORIGINAL, com.bumptech.glide.request.target.Target.SIZE_ORIGINAL)

            val downloadedFile = future.get() // This will block until download is complete

            if (downloadedFile != null) {
                // Copy the downloaded file to the desired local storage location
                downloadedFile.copyTo(localFile, overwrite = true)
                Log.d(specificTag, "Successfully downloaded and saved profile picture for user $userId to ${localFile.absolutePath}")

                // Clean up the temporary file created by Glide
                downloadedFile.delete()

                // Optional: Update the local user record with the local file path if needed for direct access
                // However, deriving the path when needed might be sufficient.
                // For now, we rely on deriving the path.

            } else {
                Log.e(specificTag, "Failed to download profile picture for user $userId from URL: $imageUrl")
            }

        } catch (e: Exception) {
            Log.e(specificTag, "Error downloading or saving profile picture for user $userId.", e)
        }
    }

    /**
     * Deletes the local profile picture file for a given user ID.
     *
     * @param userId The ID of the user whose profile picture to delete.
     */
    private fun deleteLocalProfilePicture(userId: String) {
        val specificTag = "$TAG-LocalFileDelete"
        val localFile = File(context.filesDir, "profile_picture_${userId}.jpg")
        if (localFile.exists()) {
            try {
                localFile.delete()
                Log.d(specificTag, "Successfully deleted local profile picture file for user $userId.")
            } catch (e: Exception) {
                Log.e(specificTag, "Error deleting local profile picture file for user $userId.", e)
            }
        }
    }

    suspend fun syncUsers() { // Marked suspend
        Log.d(TAG, "syncUsers called - Setting up Remote User Listener & Initial Fetch.")
        // setupFirestoreListenerAndInitialFetch is now suspend and handles householdGroupId internally
        setupFirestoreListenerAndInitialFetch(
            collectionPath = com.homeostasis.app.data.model.User.COLLECTION, // Use User.COLLECTION
            modelClass = com.homeostasis.app.data.model.User::class.java, // Use User class
            entityName = "User",
            localUpsertOrDelete = { changeType, user ->
                processRemoteUserChange(changeType, user, householdGroupIdProvider.getHouseholdGroupId().first()!!) // Pass householdGroupId
            }
        )
    }

    /**
     * Syncs locally deleted task history by deleting corresponding documents from Firestore
     * for the current household group. This is intended for use after a full local reset.
     */
    suspend fun syncDeletedTaskHistoryRemote() {
        val specificTag = "$TAG_LOCAL_TO_REMOTE-TaskHistory-Reset"
        Log.d(specificTag, "Starting remote sync for deleted task history.")
        val householdGroupId = householdGroupIdProvider.getHouseholdGroupId().first()

        if (householdGroupId == null) {
            Log.w(specificTag, "Cannot sync deleted task history: Household Group ID is null.")
            return
        }

        try {
            // Query for all task history documents for this household group in Firestore
            val querySnapshot = firestore.collection(TaskHistory.COLLECTION)
                .whereEqualTo("householdGroupId", householdGroupId)
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                Log.d(specificTag, "No remote task history found for household group $householdGroupId. Nothing to delete remotely.")
                return
            }

            // Perform a batch deletion of the documents
            val batch = firestore.batch()
            querySnapshot.documents.forEach { document ->
                batch.delete(document.reference)
            }

            batch.commit().await()
            Log.i(specificTag, "Successfully deleted ${querySnapshot.size()} remote task history documents for household group $householdGroupId.")

        } catch (e: Exception) {
            Log.e(specificTag, "Error syncing deleted task history to remote.", e)
            // TODO: Handle error - maybe retry or log for manual intervention
        }
    }
}