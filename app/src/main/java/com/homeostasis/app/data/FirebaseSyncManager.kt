package com.homeostasis.app.data

import com.homeostasis.app.data.Constants
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
    private val groupDao: GroupDao, // Inject GroupDao
    private val groupRepository: com.homeostasis.app.data.remote.GroupRepository, // Inject GroupRepository
    private val userDao: UserDao, // Inject UserDao
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

        // --- Group Synchronization ---
        syncScope.launch {
            setupRemoteGroupListenerAndInitialFetch()
        }
        syncScope.launch {
            setupLocalGroupObserverAndInitialPush()
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

    
        // --- GROUPS: Remote (Firestore) -> Local (Room) ---
        private suspend fun setupRemoteGroupListenerAndInitialFetch() { // Marked suspend
            Log.d(TAG, "Setting up Remote Group Listener & Initial Fetch.")
            // Groups are NOT filtered by householdGroupId here, as a user needs to see the group they belong to.
            // The filtering by householdGroupId happens when fetching tasks, history, and users.
            // A user's membership is determined by their User document's householdGroupId.
            // We listen to ALL group changes and process only the one the current user belongs to (or if they join/leave).
            setupFirestoreGroupListenerAndInitialFetch() // Call specialized function
        }
    
    // --- GROUPS: Local (Room) -> Remote (Firestore) ---
    private suspend fun setupLocalGroupObserverAndInitialPush() { // Marked suspend
        Log.d(TAG, "Setting up Local Group Observer & Initial Push.")
        val localChangesFlow = groupDao.getGroupsRequiringSync() // Get flow of groups needing sync

        setupLocalEntityObserverAndInitialPush(
            entityName = "Group",
            localChangesFlow = localChangesFlow,
            pushItemToFirestore = { group -> groupRepository.pushGroupToFirestore(group) }, // Use GroupRepository
            updateLocalAfterPush = { group, success -> groupRepository.updateLocalGroupAfterPush(group, success) }, // Use GroupRepository
            getAllLocalNeedingSyncSnapshot = { groupDao.getGroupsRequiringSync().first() } // Get snapshot for initial push
        )
    }

        // --- TASKS: Remote (Firestore) -> Local (Room) ---
        private suspend fun setupRemoteTaskListenerAndInitialFetch() { // Marked suspend
            Log.d(TAG, "Setting up Remote Task Listener & Initial Fetch.")

            // Get the current user's ID
            val currentUserId = userRepository.getCurrentUserId()

            if (currentUserId == null) {
                Log.w(TAG, "Cannot set up Remote Task Listener: Current user ID is null.")
                return // Cannot set up listener without a logged-in user
            }

            // Observe the current user's householdGroupId from the local database
            userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId)
                .map { user ->
                    // Provide the householdGroupId from the user object, or default if null/empty
                    user?.householdGroupId?.takeIf { it.isNotEmpty() } ?: Constants.DEFAULT_GROUP_ID
                }
                .distinctUntilChanged() // Only react when the householdGroupId actually changes
                .collectLatest { householdGroupId -> // Use collectLatest to cancel previous collection when ID changes
                    // Clean up the old listener before setting up a new one
                    // Note: This cleans ALL listeners. Consider more granular cleanup if needed.
                    cleanupFirestoreListeners()

                    if (householdGroupId != Constants.DEFAULT_GROUP_ID) { // Only set up listener if in a specific group
                        Log.d(TAG, "Household Group ID changed to $householdGroupId. Setting up new Remote Task Listener.")
                        setupFirestoreListenerAndInitialFetch(
                            collectionPath = Task.COLLECTION,
                            modelClass = Task::class.java,
                            entityName = "Task",
                            localUpsertOrDelete = { changeType, task ->
                                // Pass the current householdGroupId from the collectLatest scope
                                processRemoteTaskChange(changeType, task, householdGroupId)
                            },
                            householdGroupId = householdGroupId, // Pass the obtained householdGroupId
                            filterByHouseholdGroup = true // Explicitly filter by household group
                        )
                    } else {
                        Log.d(TAG, "Household Group ID is default or empty. Removing Remote Task Listener.")
                        // No need to set up a listener if there's no specific household group
                    }
                }
        }
    // --- TASKS: Local (Room) -> Remote (Firestore) ---
    private suspend fun setupLocalTaskObserverAndInitialPush() { // Marked suspend
        Log.d(TAG, "Setting up Local Task Observer & Initial Push.")

        // Get the current user's ID
        val currentUserId = userRepository.getCurrentUserId()

        if (currentUserId == null) {
            Log.w(TAG, "Cannot set up Local Task Observer: Current user ID is null.")
            return // Cannot set up observer without a logged-in user
        }

        // Observe the current user's householdGroupId from the local database
        userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId)
            .map { user ->
                // Provide the householdGroupId from the user object, or default if null/empty
                user?.householdGroupId?.takeIf { it.isNotEmpty() } ?: Constants.DEFAULT_GROUP_ID
            }
            .distinctUntilChanged() // Only react when the householdGroupId actually changes
            .flatMapLatest { householdGroupId -> // Use flatMapLatest to switch flows when ID changes
                if (householdGroupId != Constants.DEFAULT_GROUP_ID) { // Only observe if in a specific group
                    Log.d(TAG, "Household Group ID changed to $householdGroupId. Setting up new Local Task Observer.")
                    val modifiedTasksFlow = db.taskDao().getModifiedTasksRequiringSync(householdGroupId)
                    val deletedLocallyTasksFlow = db.taskDao().getLocallyDeletedTasksRequiringSync(householdGroupId)

                    combine(modifiedTasksFlow, deletedLocallyTasksFlow) { modified, deleted ->
                        (modified + deleted).distinctBy { it.id }
                    }
                } else {
                    Log.d(TAG, "Household Group ID is default or empty. Removing Local Task Observer.")
                    flowOf(emptyList()) // Emit empty list if no specific household ID
                }
            }
            .collect { items -> // Collect the combined flow
                if (items.isNotEmpty()) {
                    Log.d(TAG, "Task: Detected ${items.size} local changes to sync.")
                    for (item in items) {
                        val success = pushTaskToFirestore(item)
                        updateLocalTaskAfterPush(item, success)
                    }
                }
            }
    }


    // --- TASK HISTORY: Remote (Firestore) -> Local (Room) ---
    private suspend fun setupRemoteTaskHistoryListenerAndInitialFetch() { // Marked suspend
        Log.d(TAG, "Setting up Remote TaskHistory Listener & Initial Fetch.")

        // Get the current user's ID
        val currentUserId = userRepository.getCurrentUserId()

        if (currentUserId == null) {
            Log.w(TAG, "Cannot set up Remote TaskHistory Listener: Current user ID is null.")
            return // Cannot set up listener without a logged-in user
        }

        // Observe the current user's householdGroupId from the local database
        userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId)
            .map { user ->
                // Provide the householdGroupId from the user object, or default if null/empty
                user?.householdGroupId?.takeIf { it.isNotEmpty() } ?: Constants.DEFAULT_GROUP_ID
            }
            .distinctUntilChanged() // Only react when the householdGroupId actually changes
            .collectLatest { householdGroupId -> // Use collectLatest to cancel previous collection when ID changes
                // Clean up the old listener before setting up a new one
                // Note: This cleans ALL listeners. Consider more granular cleanup if needed.
                cleanupFirestoreListeners()

                if (householdGroupId != Constants.DEFAULT_GROUP_ID) { // Only set up listener if in a specific group
                    Log.d(TAG, "Household Group ID changed to $householdGroupId. Setting up new Remote TaskHistory Listener.")
                    setupFirestoreListenerAndInitialFetch(
                        collectionPath = TaskHistory.COLLECTION,
                        modelClass = TaskHistory::class.java,
                        entityName = "TaskHistory",
                        localUpsertOrDelete = { changeType, history ->
                            // Pass the current householdGroupId from the collectLatest scope
                            processRemoteTaskHistoryChange(changeType, history, householdGroupId)
                        },
                        householdGroupId = householdGroupId, // Pass the obtained householdGroupId
                        filterByHouseholdGroup = true // Explicitly filter by household group
                    )
                } else {
                    Log.d(TAG, "Household Group ID is default or empty. Removing Remote TaskHistory Listener.")
                    // No need to set up a listener if there's no specific household group
                }
            }
    }

    // --- TASK HISTORY: Local (Room) -> Remote (Firestore) ---
    private suspend fun setupLocalTaskHistoryObserverAndInitialPush() { // Marked suspend
        Log.d(TAG, "Setting up Local TaskHistory Observer & Initial Push.")

        // Get the current user's ID
        val currentUserId = userRepository.getCurrentUserId()

        if (currentUserId == null) {
            Log.w(TAG, "Cannot set up Local TaskHistory Observer: Current user ID is null.")
            return // Cannot set up observer without a logged-in user
        }

        // Observe the current user's householdGroupId from the local database
        userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId)
            .map { user ->
                // Provide the householdGroupId from the user object, or default if null/empty
                user?.householdGroupId?.takeIf { it.isNotEmpty() } ?: Constants.DEFAULT_GROUP_ID
            }
            .distinctUntilChanged() // Only react when the householdGroupId actually changes
            .flatMapLatest { householdGroupId -> // Use flatMapLatest to switch flows when ID changes
                if (householdGroupId != Constants.DEFAULT_GROUP_ID) { // Only observe if in a specific group
                    Log.d(TAG, "Household Group ID changed to $householdGroupId. Setting up new Local TaskHistory Observer.")
                    val modifiedHistoryFlow = db.taskHistoryDao().getModifiedTaskHistoryRequiringSync(householdGroupId)
                    val deletedLocallyHistoryFlow = db.taskHistoryDao().getLocallyDeletedTaskHistoryRequiringSync(householdGroupId)

                    combine(modifiedHistoryFlow, deletedLocallyHistoryFlow) { modified, deleted ->
                        (modified + deleted).distinctBy { it.id }
                    }
                } else {
                    Log.d(TAG, "Household Group ID is default or empty. Removing Local TaskHistory Observer.")
                    flowOf(emptyList()) // Emit empty list if no specific household ID
                }
            }
            .collect { items -> // Collect the combined flow
                if (items.isNotEmpty()) {
                    Log.d(TAG, "TaskHistory: Detected ${items.size} local changes to sync.")
                    for (item in items) {
                        val success = pushTaskHistoryToFirestore(item)
                        updateLocalTaskHistoryAfterPush(item, success)
                    }
                }
            }
    }


    // === SPECIFIC PROCESSING HELPERS (WORKER FUNCTIONS) ===

    // --- Group Specific Helpers ---

    private suspend fun processRemoteGroupChange(changeType: DocumentChange.Type, remoteGroupSource: com.homeostasis.app.data.model.Group) {
        val specificTag = "$TAG_REMOTE_TO_LOCAL-Group"
        val groupFromRemoteClean = remoteGroupSource.copy(needsSync = false) // Groups don't have isDeletedLocally flag

        Log.d(specificTag, "Processing remote Group change: Type=$changeType, ID=${groupFromRemoteClean.id}")
        when (changeType) {
            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                // Upsert the group into the local database
                groupDao.upsert(groupFromRemoteClean)
                Log.d(specificTag, "Upserted Group ${groupFromRemoteClean.id} from remote.")
            }
            DocumentChange.Type.REMOVED -> {
                Log.d(specificTag, "Remote Group REMOVED: ${groupFromRemoteClean.id}. Triggering cascading effects.")
                // Trigger cascading deletion effects, defaulting to NOT keeping tasks on other clients
                handleGroupDeletionCascadingEffects(groupFromRemoteClean.id, false)
                // The local group deletion is handled within handleGroupDeletionCascadingEffects
            }
        }
    }

    /**
     * Handles the cascading deletion and updates of data associated with a deleted group.
     * This is triggered when a group is removed from Firestore.
     *
     * @param deletedGroupId The ID of the group that was deleted.
     * @param keepTasks Flag indicating whether to keep tasks associated with the group owner.
     */
    private suspend fun handleGroupDeletionCascadingEffects(deletedGroupId: String, keepTasks: Boolean) {
        val specificTag = "$TAG-GroupDeletion"
        Log.d(specificTag, "Handling cascading effects for deleted group: $deletedGroupId (Keep Tasks: $keepTasks)")

        try {
            // 1. Update/Delete associated Users
            // Query for all users belonging to this group
            val usersInGroup = db.userDao().getUsersByHouseholdGroupIdSnapshot(deletedGroupId) // Assuming a snapshot query exists
            val userBatch = firestore.batch()
            usersInGroup.forEach { user ->
                // Update user's householdGroupId to null/default and mark for sync
                val updatedUser = user.copy(householdGroupId = "", needsSync = true) // Assuming "" is the default for no group
                db.userDao().upsertUser(updatedUser) // Update locally
                // Prepare remote update (Firestore doesn't need needsSync/isDeletedLocally)
                userBatch.update(firestore.collection(com.homeostasis.app.data.model.User.COLLECTION).document(user.id), "householdGroupId", "")
                Log.d(specificTag, "Prepared remote update for user ${user.id}: householdGroupId cleared.")
            }
            userBatch.commit().await() // Commit user updates
            Log.d(specificTag, "Committed remote updates for ${usersInGroup.size} users.")

            // 2. Delete associated TaskHistory
            // Query for all task history belonging to this group
            val historyInGroup = db.taskHistoryDao().getAllTaskHistoryBlocking(deletedGroupId) // Assuming a blocking query exists
            val historyBatch = firestore.batch()
            historyInGroup.forEach { history ->
                // Delete locally
                db.taskHistoryDao().delete(history)
                // Prepare remote deletion
                historyBatch.delete(firestore.collection(com.homeostasis.app.data.model.TaskHistory.COLLECTION).document(history.id))
                Log.d(specificTag, "Prepared remote deletion for task history ${history.id}.")
            }
            historyBatch.commit().await() // Commit history deletions
            Log.d(specificTag, "Committed remote deletions for ${historyInGroup.size} task history records.")

            // 3. Delete/Update associated Tasks (Conditional)
            val tasksInGroup = db.taskDao().getAllTasksFromRoomSnapshot(deletedGroupId) // Assuming a snapshot query exists
            val taskBatch = firestore.batch()

            if (keepTasks) {
                // Keep tasks: Update householdGroupId to null/default
                tasksInGroup.forEach { task ->
                    // Update locally
                    val updatedTask = task.copy(householdGroupId = "", needsSync = true) // Assuming "" is default
                    db.taskDao().upsertTask(updatedTask)
                    // Prepare remote update
                    taskBatch.update(firestore.collection(com.homeostasis.app.data.model.Task.COLLECTION).document(task.id), "householdGroupId", "")
                    Log.d(specificTag, "Prepared remote update for task ${task.id}: householdGroupId cleared.")
                }
                taskBatch.commit().await() // Commit task updates
                Log.d(specificTag, "Committed remote updates for ${tasksInGroup.size} tasks (kept).")

            } else {
                // Delete tasks
                tasksInGroup.forEach { task ->
                    // Delete locally
                    db.taskDao().hardDeleteTaskFromRoom(task) // Assuming hard delete function exists
                    // Prepare remote deletion
                    taskBatch.delete(firestore.collection(com.homeostasis.app.data.model.Task.COLLECTION).document(task.id))
                    Log.d(specificTag, "Prepared remote deletion for task ${task.id}.")
                }
                taskBatch.commit().await() // Commit task deletions
                Log.d(specificTag, "Committed remote deletions for ${tasksInGroup.size} tasks.")
            }

            Log.i(specificTag, "Completed cascading effects for deleted group: $deletedGroupId.")

        } catch (e: Exception) {
            Log.e(specificTag, "Error handling cascading effects for group deletion $deletedGroupId", e)
            // TODO: Implement retry or error handling mechanism
        }
    }

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

    // Specialized Firestore listener setup for Group entities
    private suspend fun setupFirestoreGroupListenerAndInitialFetch() {
        Log.d(TAG, "Setting up Firestore listener for Group on path ${com.homeostasis.app.data.model.Group.COLLECTION}")

        val collectionQuery = firestore.collection(com.homeostasis.app.data.model.Group.COLLECTION)

        val listener = collectionQuery
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Listen failed for Group.", e)
                    return@addSnapshotListener
                }

                syncScope.launch { // Launch coroutine to handle snapshots and call suspend lambda
                    for (dc in snapshots!!.documentChanges) {
                        val item = dc.document.toObject(com.homeostasis.app.data.model.Group::class.java)
                        if (item != null) {
                            Log.d(TAG, "Group: Remote change: ${dc.type} for doc ${dc.document.id}")
                            try {
                                // Call the suspend lambda within the coroutine scope
                                processRemoteGroupChange(dc.type, item) // Call the specific group processing function
                            } catch (ex: Exception) {
                                Log.e(TAG, "Error processing Group remote change for ${dc.document.id}", ex)
                            }
                        } else {
                            Log.w(TAG, "Group: Failed to convert document ${dc.document.id} to Group")
                        }
                    }
                }
            }
        firestoreListeners.add(listener)
    }


    private suspend fun <T : Any> setupFirestoreListenerAndInitialFetch( // Marked suspend
        collectionPath: String,
        modelClass: Class<T>,
        entityName: String,
        localUpsertOrDelete: suspend (DocumentChange.Type, T) -> Unit, // Made suspend
        householdGroupId: String?, // Accept householdGroupId as a parameter
        filterByHouseholdGroup: Boolean = true // Keep filterByHouseholdGroup parameter
    ) {
        // This function sets up a Firestore listener. The listener's callback will receive data.
        // The 'localUpsertOrDelete' lambda is suspend, so it must be called from a coroutine.
        // This implies that the Firestore listener callback should launch a coroutine.
        Log.d(TAG, "Setting up Firestore listener for $entityName on path $collectionPath with householdGroupId: $householdGroupId")

        if (filterByHouseholdGroup && (householdGroupId == null || householdGroupId.isEmpty())) {
            Log.w(TAG, "Cannot set up Firestore listener for $entityName: Household Group ID is null or empty and filtering is enabled.")
            return // Cannot set up listener without a valid household group ID if filtering
        }

        val collectionQuery = firestore.collection(collectionPath)
        val filteredQuery = if (filterByHouseholdGroup && householdGroupId != null) {
            collectionQuery.whereEqualTo("householdGroupId", householdGroupId) // Filter by householdGroupId
        } else {
            collectionQuery // No filtering if filterByHouseholdGroup is false or householdGroupId is null
        }


        val listener = filteredQuery
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
                                // Call the suspend lambda within the coroutine scope
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

        // Get the current user's ID
        val currentUserId = userRepository.getCurrentUserId()

        if (currentUserId == null) {
            Log.w(TAG, "Cannot set up Local User Observer: Current user ID is null.")
            return // Cannot set up observer without a logged-in user
        }

        // Observe the current user's householdGroupId from the local database
        userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId)
            .map { user ->
                // Provide the householdGroupId from the user object, or default if null/empty
                user?.householdGroupId?.takeIf { it.isNotEmpty() } ?: Constants.DEFAULT_GROUP_ID
            }
            .distinctUntilChanged() // Only react when the householdGroupId actually changes
            .flatMapLatest { householdGroupId -> // Use flatMapLatest to switch flows when ID changes
                if (householdGroupId != Constants.DEFAULT_GROUP_ID) { // Only observe if in a specific group
                    Log.d(TAG, "Household Group ID changed to $householdGroupId. Setting up new Local User Observer.")
                    db.userDao().getUsersRequiringSync() // Use the new query without householdGroupId
                } else {
                    Log.d(TAG, "Household Group ID is default or empty. Removing Local User Observer.")
                    flowOf(emptyList()) // Emit empty list if no specific household ID
                }
            }
            .collect { items -> // Collect the combined flow
                if (items.isNotEmpty()) {
                    Log.d(TAG, "User: Detected ${items.size} local changes to sync.")
                    for (item in items) {
                        val success = pushUserToFirestore(item)
                        updateLocalUserAfterPush(item, success)
                    }
                }
            }
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
        Log.d(TAG, "syncUsers called - Setting up Remote User Listener based on Household Group ID changes from local DB.")

        // Get the current user's ID
        val currentUserId = userRepository.getCurrentUserId()

        if (currentUserId == null) {
            Log.w(TAG, "Cannot set up User sync: Current user ID is null.")
            return // Cannot sync users without a logged-in user
        }

        // Observe the current user's householdGroupId from the local database
        userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId)
            .map { user ->
                // Provide the householdGroupId from the user object, or default if null/empty
                user?.householdGroupId?.takeIf { it.isNotEmpty() } ?: Constants.DEFAULT_GROUP_ID
            }
            .distinctUntilChanged() // Only react when the householdGroupId actually changes
            .collectLatest { householdGroupId -> // Use collectLatest to cancel previous collection when ID changes
                // Clean up the old listener before setting up a new one
                cleanupFirestoreListeners() // This cleans ALL listeners, might need more granular control if other listeners shouldn't be affected

                if (householdGroupId != Constants.DEFAULT_GROUP_ID) { // Only set up listener if in a specific group
                    Log.d(TAG, "Household Group ID changed to $householdGroupId. Setting up new Remote User Listener.")
                    setupFirestoreListenerAndInitialFetch(
                        collectionPath = com.homeostasis.app.data.model.User.COLLECTION, // Use User.COLLECTION
                        modelClass = com.homeostasis.app.data.model.User::class.java, // Use User class
                        entityName = "User",
                        localUpsertOrDelete = { changeType, user ->
                            // Pass the current householdGroupId from the collectLatest scope
                            processRemoteUserChange(changeType, user, householdGroupId)
                        },
                        householdGroupId = householdGroupId, // Pass the obtained householdGroupId
                        filterByHouseholdGroup = true // Explicitly filter by household group
                    )
                } else {
                    Log.d(TAG, "Household Group ID is default or empty. Removing Remote User Listener.")
                    // No need to set up a listener if there's no specific household group
                }
            }
    }


    /**
     * Syncs locally deleted task history by deleting corresponding documents from Firestore
     * for the current household group. This is intended for use after a full local reset.
     */
    suspend fun syncDeletedTaskHistoryRemote() {
        val specificTag = "$TAG_LOCAL_TO_REMOTE-TaskHistory-Reset"
        Log.d(specificTag, "Starting remote sync for deleted task history.")

        // Get the current user's ID
        val currentUserId = userRepository.getCurrentUserId()

        if (currentUserId == null) {
            Log.w(specificTag, "Cannot sync deleted task history: Current user ID is null.")
            return // Cannot sync without a logged-in user
        }

        // Get the current user's householdGroupId from the local database
        val householdGroupId = userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId).first()?.householdGroupId?.takeIf { it.isNotEmpty() } ?: Constants.DEFAULT_GROUP_ID

        if (householdGroupId == Constants.DEFAULT_GROUP_ID) {
            Log.w(specificTag, "Cannot sync deleted task history: Household Group ID is default or empty.")
            return // Cannot sync if not in a specific group
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