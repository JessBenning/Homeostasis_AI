package com.homeostasis.app.data.sync

import android.content.Context
import android.util.Log

import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.Target
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.homeostasis.app.data.Constants // << IMPORT ADDED/VERIFIED
import com.homeostasis.app.data.local.GroupDao
import com.homeostasis.app.data.local.TaskDao
import com.homeostasis.app.data.local.TaskHistoryDao
import com.homeostasis.app.data.local.UserDao
import com.homeostasis.app.data.model.Group
import com.homeostasis.app.data.model.Task
import com.homeostasis.app.data.model.TaskHistory
import com.homeostasis.app.data.model.User
import com.homeostasis.app.data.remote.GroupRepository
import com.homeostasis.app.data.remote.UserRepository
import com.homeostasis.app.utils.ImageUtils.generateMD5 // Assuming this is your extension function
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import com.google.firebase.Timestamp

@Singleton
class RemoteToLocalSyncHandler @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userDao: UserDao,
    private val taskDao: TaskDao,
    private val taskHistoryDao: TaskHistoryDao,
    private val groupDao: GroupDao,
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    @ApplicationContext private val context: Context
) {
    private val remoteToLocalScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var remoteDataListenerJob: Job? = null

    private val householdFirestoreListeners = mutableListOf<ListenerRegistration>()
    private var groupDocumentListener: ListenerRegistration? = null
    private var currentUserDocumentListener: ListenerRegistration? = null

    companion object {
        private const val TAG = "RemoteToLocalSync"
        private const val TAG_TASK = "Task"
        private const val TAG_TASK_HISTORY = "TaskHistory"
        private const val TAG_USER = "User"
        private const val TAG_GROUP = "Group"
    }

    fun startObservingRemoteChanges(currentUserId: String, householdGroupId: String?) {
        stopObservingRemoteChanges()

        Log.i(
            TAG,
            "Starting remote data listeners for User: $currentUserId, Household: $householdGroupId"
        )
        remoteDataListenerJob = remoteToLocalScope.launch {
            fetchAndCacheRemoteUser(currentUserId)
            setupRemoteCurrentUserListener(currentUserId)

            val effectiveHouseholdGroupId =
                householdGroupId ?: userDao.getUserById(currentUserId)?.householdGroupId

            if (effectiveHouseholdGroupId != null && effectiveHouseholdGroupId != Constants.DEFAULT_GROUP_ID) {
                Log.i(
                    TAG,
                    "User $currentUserId in household $effectiveHouseholdGroupId. Setting up household remote listeners."
                )
                fetchAndCacheGroupById(currentUserId, effectiveHouseholdGroupId)
                setupRemoteHouseholdDataListeners(effectiveHouseholdGroupId)
                setupRemoteGroupDocumentListener(effectiveHouseholdGroupId)
            } else {
                Log.i(
                    TAG,
                    "User $currentUserId not in a specific household or householdGroupId not yet available. Only current user remote listener active."
                )
            }
        }
    }

    fun stopObservingRemoteChanges() {
        if (remoteDataListenerJob?.isActive == true) {
            Log.i(TAG, "Stopping remote data listeners and cleaning up.")
            remoteDataListenerJob?.cancel()
        }
        cleanupAllFirestoreListeners()
    }

    private fun cleanupAllFirestoreListeners() {
        Log.d(TAG, "Cleaning up all Firestore listeners.")
        cleanupHouseholdFirestoreListeners()
        cleanupGroupDocumentListener()
        cleanupCurrentUserDocumentListener()
    }

    private fun cleanupHouseholdFirestoreListeners() {
        Log.d(TAG, "Cleaning up ${householdFirestoreListeners.size} household Firestore listeners.")
        householdFirestoreListeners.forEach { it.remove() }
        householdFirestoreListeners.clear()
    }

    private fun cleanupGroupDocumentListener() {
        groupDocumentListener?.remove()
        groupDocumentListener = null
        Log.d(TAG, "Cleaned up group document listener.")
    }

    private fun cleanupCurrentUserDocumentListener() {
        currentUserDocumentListener?.remove()
        currentUserDocumentListener = null
        Log.d(TAG, "Cleaned up current user document listener.")
    }

    private suspend fun setupRemoteHouseholdDataListeners(householdGroupId: String) {
        Log.d(TAG, "Setting up remote listeners for household: $householdGroupId")
        setupGenericFirestoreListenerAndInitialFetch(
            collectionPath = Task.COLLECTION, modelClass = Task::class.java, entityName = TAG_TASK,
            householdGroupId = householdGroupId, filterByHouseholdGroup = true,
            localUpsertOrDelete = { type, task ->
                processRemoteTaskChange(type, task, householdGroupId)
            }
        )
        setupGenericFirestoreListenerAndInitialFetch(
            collectionPath = TaskHistory.COLLECTION,
            modelClass = TaskHistory::class.java,
            entityName = TAG_TASK_HISTORY,
            householdGroupId = householdGroupId,
            filterByHouseholdGroup = true,
            localUpsertOrDelete = { type, history ->
                processRemoteTaskHistoryChange(type, history, householdGroupId)
            }
        )
        setupGenericFirestoreListenerAndInitialFetch(
            collectionPath = User.COLLECTION,
            modelClass = User::class.java,
            entityName = TAG_USER,
            householdGroupId = householdGroupId,
            filterByHouseholdGroup = true,
            localUpsertOrDelete = { type, user -> processRemoteUserChange(type, user) }
        )
    }

    private fun setupRemoteGroupDocumentListener(groupId: String) {
        Log.d(TAG, "Setting up remote listener for Group document: $groupId")
        if (groupId == Constants.DEFAULT_GROUP_ID) return
        cleanupGroupDocumentListener()

        val docRef = firestore.collection(Group.COLLECTION).document(groupId)
        groupDocumentListener = docRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w(TAG, "Listen failed for Group $groupId.", e); return@addSnapshotListener
            }
            remoteToLocalScope.launch {
                if (snapshot != null && snapshot.exists()) {
                    snapshot.toObject(Group::class.java)?.let { group ->
                        Log.d(TAG, "$TAG_GROUP: Remote change: MODIFIED for doc ${snapshot.id}")
                        processRemoteGroupChange(DocumentChange.Type.MODIFIED, group)
                    } ?: Log.w(TAG, "$TAG_GROUP: Failed to convert document ${snapshot.id} to Group")
                } else {
                    Log.d(TAG, "$TAG_GROUP: Group document $groupId does not exist or deleted remotely.")
                    processRemoteGroupChange(
                        DocumentChange.Type.REMOVED,
                        Group(id = groupId, name = "deleted_placeholder_name", ownerId = "") // Placeholder for ID
                    )
                }
            }
        }
    }

    private fun setupRemoteCurrentUserListener(userId: String) {
        Log.d(TAG, "Setting up remote listener for current User document: $userId")
        cleanupCurrentUserDocumentListener()

        val userDocRef = firestore.collection(User.COLLECTION).document(userId)
        currentUserDocumentListener = userDocRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w(TAG, "Listen failed for current user $userId.", e); return@addSnapshotListener
            }
            remoteToLocalScope.launch {
                if (snapshot != null && snapshot.exists()) {
                    snapshot.toObject(User::class.java)?.let { user ->
                        Log.d(TAG, "$TAG_USER: Remote change for current user ${user.id}")
                        processRemoteUserChange(DocumentChange.Type.MODIFIED, user)
                    } ?: Log.w(TAG, "$TAG_USER: Failed to convert current user document ${snapshot.id}")
                } else {
                    Log.w(TAG, "$TAG_USER: Current user document $userId does not exist. Might have been deleted remotely.")
                    // Consider further action if current user doc is gone, e.g., trigger logout via FirebaseSyncManager
                }
            }
        }
    }

    private suspend fun <T : Any> setupGenericFirestoreListenerAndInitialFetch(
        collectionPath: String, modelClass: Class<T>, entityName: String,
        householdGroupId: String?, filterByHouseholdGroup: Boolean,
        localUpsertOrDelete: suspend (changeType: DocumentChange.Type, item: T) -> Unit
    ) {
        Log.d(
            TAG,
            "Setting up Firestore listener for $entityName on path $collectionPath, Group: $householdGroupId, Filter: $filterByHouseholdGroup"
        )
        if (filterByHouseholdGroup && (householdGroupId == null || householdGroupId.isEmpty() || householdGroupId == Constants.DEFAULT_GROUP_ID)) {
            Log.w(
                TAG,
                "Skipping Firestore listener for $entityName: Household Group ID ($householdGroupId) not suitable for filtering."
            )
            return
        }

        val query = firestore.collection(collectionPath).let {
            if (filterByHouseholdGroup && householdGroupId != null) it.whereEqualTo("householdGroupId", householdGroupId) else it
        }

        try {
            Log.d(TAG, "$entityName: Performing initial fetch for query on $collectionPath for group $householdGroupId.")
            val initialSnapshot = query.get().await()
            for (document in initialSnapshot.documents) {
                document.toObject(modelClass)?.let { item ->
                    Log.d(TAG, "$entityName: Initial fetch processing item ${document.id}")
                    localUpsertOrDelete(DocumentChange.Type.ADDED, item)
                }
            }
            Log.d(TAG, "$entityName: Initial fetch completed for $collectionPath.")
        } catch (e: Exception) {
            Log.e(TAG, "$entityName: Error during initial fetch for $collectionPath.", e)
        }

        val listener = query.addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.w(TAG, "$entityName: Listen failed.", e); return@addSnapshotListener
            }
            if (snapshots == null) {
                Log.w(TAG, "$entityName: Snapshots are null."); return@addSnapshotListener
            }
            remoteToLocalScope.launch {
                for (dc in snapshots.documentChanges) {
                    try {
                        dc.document.toObject(modelClass)?.let { item ->
                            Log.d(TAG, "$entityName: Remote change: ${dc.type} for doc ${dc.document.id}")
                            localUpsertOrDelete(dc.type, item)
                        } ?: Log.w(TAG, "$entityName: Failed to convert doc ${dc.document.id} to $modelClass. Data: ${dc.document.data}")
                    } catch (ex: Exception) {
                        Log.e(TAG, "$entityName: Error processing change for ${dc.document.id}. Type: ${dc.type}", ex)
                    }
                }
            }
        }
        householdFirestoreListeners.add(listener)
        Log.d(TAG, "$entityName: Firestore listener ADDED for $collectionPath.")
    }

    private suspend fun processRemoteGroupChange(changeType: DocumentChange.Type, remoteGroup: Group) {
        val tag = "$TAG_GROUP"
        val groupToProcess = remoteGroup.copy(needsSync = false)
        Log.d(tag, "Processing remote change: ${changeType}, ID=${groupToProcess.id}")
        when (changeType) {
            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                groupDao.upsert(groupToProcess)
                Log.d(tag, "Upserted local Group ${groupToProcess.id}")
            }
            DocumentChange.Type.REMOVED -> {
                Log.d(tag, "REMOVED Group: ${groupToProcess.id}. Triggering cascade delete locally.")
                handleGroupDeletionCascadingEffects(groupToProcess.id, keepTasksAssociatedWithGroup = false) // Or true based on your logic
                Log.d(tag, "Local Group ${groupToProcess.id} and associated data processed for remote deletion.")
            }
        }
    }

    private suspend fun processRemoteTaskChange(
        changeType: DocumentChange.Type,
        remoteTask: Task,
        householdGroupIdContext: String
    ) {
        val tag = "$TAG_TASK (Group: $householdGroupIdContext)"
        val taskFromRemoteClean = remoteTask.copy(
            needsSync = false,
            isDeletedLocally = false,
            householdGroupId = remoteTask.householdGroupId ?: householdGroupIdContext
        )
        Log.d(
            tag,
            "Processing remote change: ${changeType}, ID=${taskFromRemoteClean.id}, RemoteDeleted=${taskFromRemoteClean.isDeleted}, HGID=${taskFromRemoteClean.householdGroupId}"
        )

        val existingLocalTask = taskDao.getTaskById(taskFromRemoteClean.id, taskFromRemoteClean.householdGroupId ?: "")

        when (changeType) {
            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                val taskToUpsert: Task
                if (existingLocalTask != null) {
                    if (existingLocalTask.needsSync || existingLocalTask.isDeletedLocally) {
                        val remoteTimestamp = taskFromRemoteClean.lastModifiedAt?.seconds ?: 0L
                        val localTimestamp = existingLocalTask.lastModifiedAt?.seconds ?: 0L
                        if (remoteTimestamp >= localTimestamp) {
                            Log.i(tag, "Conflict: Remote ${taskFromRemoteClean.id} (ts:$remoteTimestamp) wins over local pending (ts:$localTimestamp). Upserting remote.")
                            taskToUpsert = taskFromRemoteClean
                        } else {
                            Log.i(tag, "Conflict: Local ${existingLocalTask.id} (ts:$localTimestamp) pending and newer. Local sync will handle. Skipping remote update.")
                            return // Let local sync proceed
                        }
                    } else {
                        taskToUpsert = taskFromRemoteClean
                    }
                } else {
                    taskToUpsert = taskFromRemoteClean
                }
                Log.d(tag, "Upserting local Task: ID=${taskToUpsert.id}, isDeleted=${taskToUpsert.isDeleted}")
                taskDao.upsertTask(taskToUpsert)
            }
            DocumentChange.Type.REMOVED -> {
                Log.d(tag, "REMOVED Task: ${taskFromRemoteClean.id}. Hard-deleting locally.")
                taskDao.hardDeleteTaskFromRoom(taskFromRemoteClean)
                taskHistoryDao.deleteTaskHistoryById(taskFromRemoteClean.id) // << Ensure this DAO method exists
                Log.d(tag, "Also deleted associated task history for task ${taskFromRemoteClean.id}")
            }
        }
    }

    suspend fun processRemoteUserChange(changeType: DocumentChange.Type, userFromFirestore: User) {
        val userId = userFromFirestore.id
        val tag = "$TAG_USER-user-${changeType.name}"

        when (changeType) {
            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                Log.d(tag, "Processing User ADDED/MODIFIED: ${userFromFirestore.id} - ${userFromFirestore.name}")


                //val effectiveLastModifiedAt = userFromFirestore.lastModifiedAt ?: Timestamp.now()
                // Or Option 1.B: Default to createdAt if null and createdAt is non-null
                val effectiveLastModifiedAt = userFromFirestore.lastModifiedAt ?: userFromFirestore.createdAt ?: Timestamp.now()

                userDao.upsertUser(
                    userFromFirestore.copy(
                        needsSync = false,
                        isDeletedLocally = false,
                        lastModifiedAt = effectiveLastModifiedAt // Use the handled value
                    )
                )// Ensure flags are reset
                Log.d(tag, "Upserted local User ${userFromFirestore.id} with data from Firestore.")

                if (!userFromFirestore.profileImageUrl.isNullOrBlank()) {
                    val remoteHash = userFromFirestore.profileImageHashSignature
                    if (!remoteHash.isNullOrBlank()) {
                        val localFile = getLocalProfilePictureFile(userId)
                        val localFileHash = localFile.generateMD5()

                        if (!localFile.exists() || remoteHash != localFileHash) {
                            if (!localFile.exists()) Log.i(tag, "User $userId: Local profile picture missing. Downloading from ${userFromFirestore.profileImageUrl}.")
                            else Log.i(tag, "User $userId: Remote hash ($remoteHash) differs from local ($localFileHash). Downloading new profile picture from ${userFromFirestore.profileImageUrl}.")

                            remoteToLocalScope.launch { // Launch in scope for background download
                                downloadAndSaveProfilePicture(userId, userFromFirestore.profileImageUrl!!)
                            }
                        } else {
                            Log.i(tag, "User $userId: Local profile picture hash ($localFileHash) matches remote ($remoteHash). No download needed.")
                        }
                    } else {
                        Log.w(tag, "User $userId: profileImageUrl is present but profileImageHashSignature is missing in Firestore. Cannot perform hash check.")
                        val localFile = getLocalProfilePictureFile(userId)
                        if (!localFile.exists()) {
                            Log.i(tag, "User $userId: profileImageHashSignature missing, local file missing. Downloading from ${userFromFirestore.profileImageUrl}.")
                            remoteToLocalScope.launch {
                                downloadAndSaveProfilePicture(userId, userFromFirestore.profileImageUrl!!)
                            }
                        }
                    }
                } else {
                    Log.i(tag, "User $userId: profileImageUrl is blank. Deleting local profile picture if it exists.")
                    deleteLocalProfilePicture(userId)
                }
            }
            DocumentChange.Type.REMOVED -> {
                Log.d(tag, "Processing User REMOVED: ${userFromFirestore.id}")
                userDao.deleteUserById(userId)
                deleteLocalProfilePicture(userId)
                Log.i(tag, "Deleted User $userId locally and removed profile picture.")
            }
        }
    }

    private suspend fun processRemoteTaskHistoryChange(
        changeType: DocumentChange.Type,
        remoteHistory: TaskHistory,
        householdGroupIdContext: String
    ) {
        val tag = "$TAG_TASK_HISTORY (Group: $householdGroupIdContext)"
        val historyToProcess = remoteHistory.copy(
            needsSync = false,
            isDeletedLocally = false, // Reset local-only flag
            householdGroupId = remoteHistory.householdGroupId ?: householdGroupIdContext
        )
        Log.d(tag, "Processing remote change: ${changeType}, ID=${historyToProcess.id}, RemoteSoftDeleted=${historyToProcess.isDeleted}")

        when (changeType) {
            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                if (historyToProcess.isDeleted) { // Check Firestore's standard "isDeleted" field for soft delete
                    Log.d(tag, "Remote TaskHistory ${historyToProcess.id} is marked as soft-deleted. Deleting locally (hard delete).")
                    taskHistoryDao.deleteTaskHistoryById(historyToProcess.id) // Hard delete locally if remote is soft-deleted
                } else {
                    Log.d(tag, "Upserting local TaskHistory ${historyToProcess.id}")
                    taskHistoryDao.insertOrUpdate(historyToProcess)
                }
            }
            DocumentChange.Type.REMOVED -> {
                Log.d(tag, "REMOVED TaskHistory (hard delete from Firestore or query mismatch): ${historyToProcess.id}. Deleting locally.")
                taskHistoryDao.deleteTaskHistoryById(historyToProcess.id)
            }
        }
    }

    suspend fun fetchAndCacheRemoteUser(userId: String): User? {
        val specificTag = "$TAG-User-Fetch"
        if (userId.isEmpty()) {
            Log.w(specificTag, "Cannot fetch remote user: userId is empty."); return null
        }
        Log.d(specificTag, "Attempting to fetch remote user profile for UID: $userId from Firestore.")
        return try {
            val userDocument = firestore.collection(User.COLLECTION) // Use Constant
                .document(userId)
                .get()
                .await()
            val remoteUser: User? = userDocument.toObject(User::class.java)

            if (remoteUser != null) {
                Log.d(specificTag, "User $userId (${remoteUser.name}) fetched from Firestore. Caching to Room.")
                val userToCache = remoteUser.copy(id = userId, needsSync = false, isDeletedLocally = false) // Ensure ID is set and flags are reset
                userDao.upsertUser(userToCache)
                Log.i(specificTag, "Successfully fetched and cached user ${userToCache.id}, groupID: ${userToCache.householdGroupId}.")

                if (!userToCache.profileImageUrl.isNullOrBlank()) {
                    val remoteHash = userToCache.profileImageHashSignature
                    if (!remoteHash.isNullOrBlank()) {
                        val localFile = getLocalProfilePictureFile(userToCache.id)
                        val localFileHash = localFile.generateMD5()
                        if (!localFile.exists() || remoteHash != localFileHash) {
                            Log.i(specificTag, "User ${userToCache.id} (fetchAndCache): Pic needs download/update.")
                            downloadAndSaveProfilePicture(userToCache.id, userToCache.profileImageUrl!!)
                        } else {
                            Log.i(specificTag, "User ${userToCache.id} (fetchAndCache): Pic hash matches, no download.")
                        }
                    } else {
                        val localFile = getLocalProfilePictureFile(userToCache.id)
                        if (!localFile.exists()){
                            Log.i(specificTag, "User ${userToCache.id} (fetchAndCache): Pic hash missing, local missing. Downloading.")
                            downloadAndSaveProfilePicture(userToCache.id, userToCache.profileImageUrl!!)
                        }
                    }
                } else {
                    deleteLocalProfilePicture(userToCache.id)
                }
                userToCache
            } else {
                Log.w(specificTag, "User $userId not found in Firestore.")
                null
            }
        } catch (e: Exception) {
            Log.e(specificTag, "Error fetching user $userId from Firestore.", e)
            null
        }
    }

    suspend fun fetchAndCacheGroupById(userId: String, householdGroupId: String): Group? {
        val specificTag = "$TAG-Group-Fetch"
        if (householdGroupId.isEmpty() || householdGroupId == Constants.DEFAULT_GROUP_ID) {
            Log.i(specificTag, "User $userId has no specific household group ID ('$householdGroupId') to sync group details for.")
            return null
        }
        Log.d(specificTag, "Attempting to fetch remote group $householdGroupId for user $userId")
        try {
            val remoteGroup = groupRepository.fetchRemoteGroup(householdGroupId) // Assuming this repo method fetches from Firestore
            if (remoteGroup != null) {
                Log.d(specificTag, "Group $householdGroupId fetched from Firestore. Caching to Room.")
                val groupToCache = remoteGroup.copy(needsSync = false)
                groupDao.upsert(groupToCache)
                Log.i(specificTag, "Successfully fetched and cached group ${groupToCache.id}.")
                return groupToCache
            } else {
                Log.w(specificTag, "Group $householdGroupId not found in Firestore.")
                return null
            }
        } catch (e: Exception) {
            Log.e(specificTag, "Error fetching group $householdGroupId from Firestore and caching locally.", e)
            return null
        }
    }

    /**
     * Gets the File object for a user's local profile picture using the convention from Constants.
     */
    private fun getLocalProfilePictureFile(userId: String): File {
        val imageDir = File(context.filesDir, Constants.PROFILE_IMAGE_LOCAL_FILENAME_DIR) // Consistent subdirectory name
        if (!imageDir.exists()) {
            imageDir.mkdirs()
        }
        val fileName = Constants.determineLocalFileName(userId) // << USING THE CONSTANT FUNCTION
        return File(imageDir, fileName)
    }

    private suspend fun downloadAndSaveProfilePicture(userId: String, imageUrl: String) {
        val specificTag = "$TAG-User-Download"
        val localFile = getLocalProfilePictureFile(userId) // Uses the updated method

        Log.d(specificTag, "Attempting to download profile picture for user $userId from $imageUrl to ${localFile.absolutePath}")
        try {
            // Using withContext(Dispatchers.IO) as Glide's submit().get() can block.
            val downloadedFile = withContext(Dispatchers.IO) {
                Glide.with(context)
                    .asFile()
                    .load(imageUrl)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .submit() // Returns a Future<File>
                    .get()    // Blocks until download is complete
            }

            if (downloadedFile != null) {
                downloadedFile.copyTo(localFile, overwrite = true)
                Log.i(specificTag, "Successfully downloaded and saved profile picture for user $userId to ${localFile.absolutePath}")
                // Optional: Update local user with the path if you store it
                // userDao.updateLocalProfilePicturePath(userId, "file://${localFile.absolutePath}", imageUrl)

                if (downloadedFile.absolutePath != localFile.absolutePath && downloadedFile.exists()) {
                    downloadedFile.delete() // Clean up Glide's temporary file if it's different
                }
            } else {
                Log.e(specificTag, "Glide downloaded a null file for user $userId from $imageUrl.")
            }
        } catch (e: Exception) {
            Log.e(specificTag, "Error downloading or saving profile picture for user $userId from $imageUrl.", e)
            // Optional: If download fails, maybe clear the local path
            // userDao.updateLocalProfilePicturePath(userId, null, imageUrl)
        }
    }

    private fun deleteLocalProfilePicture(userId: String) {
        val specificTag = "$TAG-LocalFileDelete"
        val localFile = getLocalProfilePictureFile(userId) // Uses the updated method
        try {
            if (localFile.exists()) {
                if (localFile.delete()) {
                    Log.d(specificTag, "Successfully deleted local profile picture for user $userId: ${localFile.path}")
                    // Optional: Also clear the path in the local user model
                    // remoteToLocalScope.launch { userDao.clearLocalProfilePicturePath(userId) }
                } else {
                    Log.w(specificTag, "Failed to delete local profile picture for user $userId at ${localFile.path}")
                }
            } else {
                Log.d(specificTag, "No local profile picture to delete for user $userId (file not found: ${localFile.path})")
            }
        } catch (e: SecurityException) {
            Log.e(specificTag, "Security error deleting local profile picture for user $userId: ${localFile.path}", e)
        } catch (e: Exception) {
            Log.e(specificTag, "Error deleting local profile picture for user $userId: ${localFile.path}", e)
        }
    }

    private suspend fun handleGroupDeletionCascadingEffects(
        deletedGroupId: String,
        keepTasksAssociatedWithGroup: Boolean
    ) {
        val specificTag = "$TAG-GroupLocalCascadeDelete"
        Log.d(specificTag, "Handling local cascading effects for deleted group: $deletedGroupId (Keep Tasks: $keepTasksAssociatedWithGroup)")

        val usersInGroup = userDao.getUsersByHouseholdGroupIdSnapshot(deletedGroupId)
        usersInGroup.forEach { user ->
            userDao.upsertUser(user.copy(householdGroupId = Constants.DEFAULT_GROUP_ID, needsSync = true))
        }
        Log.d(specificTag, "Updated ${usersInGroup.size} users locally to remove from group $deletedGroupId.")

        taskHistoryDao.deleteAllTaskHistory(deletedGroupId)
        Log.d(specificTag, "Deleted task history locally for group $deletedGroupId.")

        val tasksInGroup = taskDao.getAllTasksFromRoomSnapshot(deletedGroupId)
        if (keepTasksAssociatedWithGroup) {
            tasksInGroup.forEach { task ->
                taskDao.upsertTask(task.copy(householdGroupId = Constants.DEFAULT_GROUP_ID, needsSync = true))
            }
            Log.d(specificTag, "Updated ${tasksInGroup.size} tasks locally to remove from group $deletedGroupId.")
        } else {
            tasksInGroup.forEach { task ->
                taskDao.hardDeleteTaskFromRoom(task)
            }
            Log.d(specificTag, "Hard-deleted ${tasksInGroup.size} tasks locally from group $deletedGroupId.")
        }

        groupDao.deleteGroupById(deletedGroupId)
        Log.i(specificTag, "Completed local cascading effects for deleted group: $deletedGroupId.")
    }

    fun onCleared() {
        Log.d(TAG, "onCleared called, stopping remote changes observation.")
        stopObservingRemoteChanges()
        // Cancel the scope to clean up any ongoing coroutines if not already handled by job cancellation.
        // SupervisorJob allows children to fail independently, but cancelling the job passed to its context
        // is the standard way to clean up. stopObservingRemoteChanges already cancels remoteDataListenerJob.
    }
}