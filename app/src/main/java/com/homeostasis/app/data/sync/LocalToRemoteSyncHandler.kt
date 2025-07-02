package com.homeostasis.app.data.sync

import android.content.Context
import android.util.Log
// import androidx.work.await // Not directly used in the provided snippet, keep if used elsewhere
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.homeostasis.app.data.Constants
import com.homeostasis.app.data.local.GroupDao
import com.homeostasis.app.data.local.TaskDao
import com.homeostasis.app.data.local.TaskHistoryDao
import com.homeostasis.app.data.local.UserDao
import com.homeostasis.app.data.model.Group
import com.homeostasis.app.data.model.Task
import com.homeostasis.app.data.model.TaskHistory
import com.homeostasis.app.data.model.User
import com.homeostasis.app.data.model.toFirestoreMap
import com.homeostasis.app.data.remote.FirebaseStorageRepository
import com.homeostasis.app.data.remote.GroupRepository
import com.homeostasis.app.data.remote.TaskHistoryRepository
import com.homeostasis.app.data.remote.TaskRepository
import com.homeostasis.app.data.remote.UserRepository
import com.homeostasis.app.utils.ImageUtils.generateMD5 // Assuming this is a static method
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

// Placeholder for the extension function - define this in User.kt or a UserExtensions.kt
// fun User.toFirestoreMap(): Map<String, Any?> {
//    return mapOf(
//        "name" to name,
//        "email" to email, // Ensure all syncable fields are here
//        "profileImageUrl" to profileImageUrl,
//        "householdGroupId" to householdGroupId,
//        "fcmToken" to fcmToken,
//        "currentSessionStreak" to currentSessionStreak,
//        "longestSessionStreak" to longestSessionStreak,
//        "createdAt" to createdAt, // Assuming this is set at object creation or comes from Firestore
//        "lastModifiedAt" to FieldValue.serverTimestamp() // Use server timestamp for Firestore
//        // DO NOT include 'id' if it's the document ID, 'needsSync', 'profileImageHashSignature' (if only local)
//    ).filterValues { it != null }
// }


@Singleton
class LocalToRemoteSyncHandler @Inject constructor(
    private val userRepository: UserRepository, // Keep if other methods in UserRepository are used for sync
    private val taskRepository: TaskRepository,
    private val taskHistoryRepository: TaskHistoryRepository,
    private val groupRepository: GroupRepository,
    private val userDao: UserDao,
    private val taskDao: TaskDao,
    private val taskHistoryDao: TaskHistoryDao,
    private val groupDao: GroupDao,
    private val firebaseStorageRepository: FirebaseStorageRepository,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context
) {

    private val localToRemoteScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var localDataObserverJob: Job? = null

    companion object {
        private const val TAG = "LocalToRemoteSync"
        private const val TAG_TASK = "Task"
        private const val TAG_TASK_HISTORY = "TaskHistory"
        private const val TAG_USER = "User"
        private const val TAG_GROUP = "Group"
    }

    fun startObservingLocalChanges(currentUserId: String, householdGroupId: String?) {
        stopObservingLocalChanges()

        Log.i(TAG, "Starting local data observers for User: $currentUserId, Household: $householdGroupId")
        localDataObserverJob = localToRemoteScope.launch {
            launch {
                Log.d(TAG, "Setting up local observer for current User: $currentUserId")
                setupGenericLocalEntityObserverAndInitialPush<User>(
                    entityName = "$TAG_USER-CurrentUser",
                    localChangesFlow = userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId).map { user ->
                        if (user != null && user.needsSync) listOf(user) else emptyList()
                    },
                    pushItemToFirestore = { user -> pushUserToFirestore(user) },
                    updateLocalAfterPush = { user, success ->
                        // The core logic of updating local user after push is now inside pushUserToFirestore
                        // This lambda can be simplified or pushUserToFirestore can return the updated user for clarity.
                        // For now, if pushUserToFirestore handles its own local update, this might not need to do much.
                        if (success) {
                            Log.d(TAG, "$TAG_USER-CurrentUser: Local update handled within pushUserToFirestore for ${user.id}.")
                        } else {
                            Log.e(TAG, "$TAG_USER-CurrentUser: Push failed for ${user.id}. Local state might be stale or still needsSync=true.")
                        }
                    },
                    getAllLocalNeedingSyncSnapshot = {
                        val user = userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId).first()
                        if (user != null && user.needsSync) listOf(user) else emptyList()
                    }
                )
            }

            if (householdGroupId != null && householdGroupId != Constants.DEFAULT_GROUP_ID) {
                launch {
                    Log.d(TAG, "Setting up local observers for household: $householdGroupId")
                    setupLocalHouseholdDataObservers(householdGroupId)
                }
            } else {
                Log.i(TAG, "No specific household group ID for user $currentUserId. Only user-specific local observer active.")
            }
        }
    }

    fun stopObservingLocalChanges() {
        if (localDataObserverJob?.isActive == true) {
            Log.i(TAG, "Stopping local data observers.")
            localDataObserverJob?.cancel()
            localDataObserverJob = null
        }
    }

    private suspend fun setupLocalHouseholdDataObservers(householdGroupId: String) {
        Log.d(TAG, "Setting up local household data observers for group: $householdGroupId")

        // --- Tasks ---
        setupGenericLocalEntityObserverAndInitialPush(
            entityName = TAG_TASK,
            localChangesFlow = combine(
                taskDao.getModifiedTasksRequiringSync(householdGroupId),
                taskDao.getLocallyDeletedTasksRequiringSync(householdGroupId)
            ) { modified, deleted -> (modified + deleted).distinctBy { it.id } },
            pushItemToFirestore = { task -> pushTaskToFirestore(task as Task) }, // Cast is okay if type is known
            updateLocalAfterPush = { task, success -> updateLocalTaskAfterPush(task as Task, success) },
            getAllLocalNeedingSyncSnapshot = {
                val modified = taskDao.getModifiedTasksRequiringSync(householdGroupId).first()
                val deleted = taskDao.getLocallyDeletedTasksRequiringSync(householdGroupId).first()
                (modified + deleted).distinctBy { it.id }
            }
        )

        // --- Task History ---
        setupGenericLocalEntityObserverAndInitialPush<TaskHistory>(
            entityName = TAG_TASK_HISTORY,
            localChangesFlow = combine(
                taskHistoryDao.getModifiedTaskHistoryRequiringSync(householdGroupId),
                taskHistoryDao.getLocallyDeletedTaskHistoryRequiringSync(householdGroupId)
            ) { modified, deleted -> (modified + deleted).distinctBy { it.id } },
            pushItemToFirestore = { localHistoryItemUntyped ->
                val localHistoryItem = localHistoryItemUntyped as TaskHistory
                var success = false
                val logPrefix = "$TAG_TASK_HISTORY (Group: $householdGroupId)"

                if (localHistoryItem.isDeletedLocally) {
                    Log.d(logPrefix, "Processing LOCALLY DELETED TaskHistory ${localHistoryItem.id} for remote deletion.")
                    val softDeleteSuccess = taskHistoryRepository.softDeleteTaskHistoryInFirestore(localHistoryItem.id)
                    if (softDeleteSuccess) {
                        Log.i(logPrefix, "Successfully SOFT-DELETED TaskHistory ${localHistoryItem.id} in Firestore.")
                        // Consider if hard delete should always follow or be a separate process
                        val hardDeleteFirestoreSuccess = taskHistoryRepository.hardDeleteTaskHistoryFromFirestore(localHistoryItem.id)
                        if (hardDeleteFirestoreSuccess) {
                            Log.i(logPrefix, "Successfully HARD-DELETED TaskHistory ${localHistoryItem.id} from Firestore.")
                            success = true
                        } else {
                            Log.e(logPrefix, "FAILED to HARD-DELETE TaskHistory ${localHistoryItem.id} from Firestore after soft delete.")
                        }
                    } else {
                        Log.e(logPrefix, "FAILED to SOFT-DELETE TaskHistory ${localHistoryItem.id} in Firestore.")
                    }
                } else {
                    Log.d(logPrefix, "Pushing TaskHistory ${localHistoryItem.id} (Create/Update) to Firestore.")
                    success = taskHistoryRepository.createOrUpdateFirestoreTaskHistory(localHistoryItem)
                }
                success
            },
            updateLocalAfterPush = { localHistoryItemUntyped, firestorePushSuccess ->
                val localHistoryItem = localHistoryItemUntyped as TaskHistory
                val logPrefix = "$TAG_TASK_HISTORY (Group: $householdGroupId)"
                if (firestorePushSuccess) {
                    if (localHistoryItem.isDeletedLocally) {
                        taskHistoryDao.deleteTaskHistoryById(localHistoryItem.id) // Hard delete locally
                        Log.i(logPrefix, "Successfully HARD-DELETED TaskHistory ${localHistoryItem.id} from local Room DB.")
                    } else {
                        Log.d(logPrefix, "Updating local TaskHistory ${localHistoryItem.id}, setting needsSync=false.")
                        taskHistoryDao.insertOrUpdate(localHistoryItem.copy(needsSync = false, lastModifiedAt = Timestamp.now()))
                    }
                } else {
                    Log.e(logPrefix, "Firestore push FAILED for TaskHistory ${localHistoryItem.id}.")
                }
            },
            getAllLocalNeedingSyncSnapshot = {
                val modified = taskHistoryDao.getModifiedTaskHistoryRequiringSync(householdGroupId).first()
                val deleted = taskHistoryDao.getLocallyDeletedTaskHistoryRequiringSync(householdGroupId).first()
                (modified + deleted).distinctBy { it.id }
            }
        )

        // --- Group ---
        setupGenericLocalEntityObserverAndInitialPush<Group>(
            entityName = TAG_GROUP,
            localChangesFlow = groupDao.getGroupById(householdGroupId).map { group ->
                if (group != null && group.needsSync) listOf(group) else emptyList()
            },
            pushItemToFirestore = { group -> pushGroupToFirestore(group) },
            updateLocalAfterPush = { group, success -> updateLocalGroupAfterPush(group, success) },
            getAllLocalNeedingSyncSnapshot = {
                val group = groupDao.getGroupById(householdGroupId).first()
                if (group != null && group.needsSync) listOf(group) else emptyList()
            }
        )
    }

    private suspend fun <T : Any> setupGenericLocalEntityObserverAndInitialPush(
        entityName: String,
        localChangesFlow: Flow<List<T>>,
        pushItemToFirestore: suspend (T) -> Boolean,
        updateLocalAfterPush: suspend (T, Boolean) -> Unit,
        getAllLocalNeedingSyncSnapshot: suspend () -> List<T>
    ) {
        Log.d(TAG, "Setting up Local Observer for $entityName.")
        try {
            // Initial push for items that might have been missed or failed previously
            val initialItems = getAllLocalNeedingSyncSnapshot()
            if (initialItems.isNotEmpty()) {
                Log.i(TAG, "$entityName: Found ${initialItems.size} items for initial push.")
                initialItems.forEach { item ->
                    val success = pushItemToFirestore(item)
                    updateLocalAfterPush(item, success) // Ensure local state is updated based on success
                }
            }

            // Observe ongoing changes
            localChangesFlow.conflate().collect{ items ->
                if (items.isNotEmpty()) {
                    Log.d(TAG, "$entityName: Detected ${items.size} local changes.")
                    items.forEach { item ->
                        val success = pushItemToFirestore(item)
                        updateLocalAfterPush(item, success)
                    }
                }
            }
        } catch (e: CancellationException) {
            Log.i(TAG, "$entityName Local Observer cancelled: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$entityName: Error in local observer.", e)
        }
    }


    private suspend fun pushGroupToFirestore(group: Group): Boolean {
        val specificTag = "$TAG-$TAG_GROUP"
        Log.d(specificTag, "Attempting to push Group ${group.id} to Firestore.")

        return try {
            val firestoreGroupData = group.toFirestoreMap() // <--- USE THE EXTENSION FUNCTION

            if (firestoreGroupData.isEmpty() && group.id.isBlank()) { // Basic sanity check
                Log.w(specificTag, "Group data is empty or ID is blank. Skipping push for group: $group")
                return false // Or handle as an error appropriately
            }

            Log.d(specificTag, "Pushing Group ${group.id} with data: $firestoreGroupData")

            firestore.collection(Group.COLLECTION) // Use Group.COLLECTION static field
                .document(group.id)
                .set(firestoreGroupData, SetOptions.merge()) // SetOptions.merge is good for create/update
                .await()

            Log.i(specificTag, "Successfully pushed Group ${group.id} to Firestore.")
            true
        } catch (e: CancellationException) {
            Log.w(specificTag, "Firestore push for Group ${group.id} was cancelled.", e)
            throw e // Re-throw
        } catch (e: Exception) {
            Log.e(specificTag, "Error pushing Group ${group.id} to Firestore.", e)
            false
        }
    }

    private suspend fun updateLocalGroupAfterPush(group: Group, success: Boolean) {
        val specificTag = "$TAG-$TAG_GROUP"
        if (success) {
            // Update local lastModifiedAt with client time, clear needsSync
            val updatedGroup = group.copy(needsSync = false, lastModifiedAt = Timestamp.now())
            groupDao.upsert(updatedGroup) // Assuming groupDao.upsert is a suspend function
            Log.i(specificTag, "Successfully updated local Group ${group.id}, needsSync=false.")
        } else {
            Log.e(specificTag, "Firestore push FAILED for Group ${group.id}. 'needsSync' remains true.")
            // Optionally, you could update lastModifiedAt locally even on failure
            // to prevent rapid re-sync attempts if the error is persistent,
            // or implement a backoff strategy. For now, this is fine.
        }
    }





    private suspend fun pushTaskToFirestore(task: Task): Boolean {
        val specificTag = "$TAG-$TAG_TASK"
        return try {
            if (task.isDeletedLocally) {
                Log.d(specificTag, "Pushing Task ${task.id} (Soft Delete).")
                taskRepository.softDeleteTaskInFirestore(task.id) // This should use server timestamp
            } else {
                Log.d(specificTag, "Pushing Task ${task.id} (Create/Update). isDeleted=${task.isDeleted}")
                // createOrUpdateTaskInFirestore should handle its own server timestamp for lastModifiedAt
                taskRepository.createOrUpdateTaskInFirestore(task)
            }
        } catch (e: Exception) {
            Log.e(specificTag, "Error pushing Task ${task.id}.", e)
            false
        }
    }

    private suspend fun updateLocalTaskAfterPush(task: Task, firestoreSuccess: Boolean) {
        val specificTag = "$TAG-$TAG_TASK"
        if (firestoreSuccess) {
            val updatedTimestamp = Timestamp.now() // Client time for local update
            val updatedTask = if (task.isDeletedLocally) {
                // If soft delete in Firestore succeeded, local isDeletedLocally should be false,
                // and isDeleted can be true. needsSync becomes false.
                task.copy(isDeleted = true, isDeletedLocally = false, needsSync = false, lastModifiedAt = updatedTimestamp)
            } else {
                task.copy(needsSync = false, lastModifiedAt = updatedTimestamp)
            }
            taskDao.upsertTask(updatedTask)
            Log.d(specificTag, "Successfully updated local Task ${task.id} flags.")
        } else {
            Log.e(specificTag, "Firestore push failed for Task ${task.id}. Local flags remain.")
        }
    }

    /**
     * Pushes user data to Firestore, including profile image if changed.
     * Updates the local user record (clears needsSync, updates image URL/hash and lastModifiedAt)
     * upon successful Firestore operation.
     */
    private suspend fun pushUserToFirestore(user: User): Boolean {
        val userId = user.id
        val specificTag = "$TAG-PushUser-$userId"
        Log.d(specificTag, "Processing user for Firestore push. User: $user")

        var remoteUrlForFirestore: String = user.profileImageUrl //TODO Set to null???
        var newImageHashForDb: String? = user.profileImageHashSignature
        var requiresFirestoreUpdate = user.needsSync // Initially true if needsSync is true
        var imageActuallyUploaded = false
        val derivedLocalFile = Constants.getProfileImageFile(context, userId)

        if (derivedLocalFile.exists()) {
            val currentFileHash = derivedLocalFile.generateMD5()
            Log.d(
                specificTag,
                "Local file exists. Current hash: $currentFileHash, Stored hash: ${user.profileImageHashSignature}"
            )

            if (currentFileHash == null) {
                Log.e(
                    specificTag,
                    "Failed to generate hash for local file: ${derivedLocalFile.path}. Skipping image upload."
                )
            } else {
                if (user.profileImageUrl.isNullOrBlank() || currentFileHash != user.profileImageHashSignature) {
                    Log.i(
                        specificTag,
                        "Image change detected (or initial upload). Current hash: $currentFileHash, Old hash: ${user.profileImageHashSignature}. Attempting upload."
                    )
                    try {
                        val uploadedRemoteUrl = firebaseStorageRepository.uploadUserProfileImage(
                            userId,
                            derivedLocalFile
                        )
                        if (uploadedRemoteUrl != null) {
                            remoteUrlForFirestore = uploadedRemoteUrl
                            newImageHashForDb = currentFileHash
                            requiresFirestoreUpdate = true
                            imageActuallyUploaded = true
                            Log.i(
                                specificTag,
                                "Image uploaded. New URL: $remoteUrlForFirestore, New Hash: $newImageHashForDb"
                            )
                        } else {
                            Log.w(
                                specificTag,
                                "Image upload failed (null URL). Using previous URL if available."
                            )
                        }
                    } catch (e: CancellationException) {
                        Log.w(specificTag, "Image upload cancelled.", e); throw e
                    } catch (e: Exception) {
                        Log.e(specificTag, "Exception during image upload.", e)
                        // Decide if this is fatal for the whole user sync. For now, continue to sync other fields.
                    }
                } else {
                    Log.d(specificTag, "Local file hash matches. No image re-upload needed.")
                }
            }
        } else if (!user.profileImageUrl.isNullOrBlank()) {
            Log.i(
                specificTag,
                "Local file missing, but remote URL exists. Assuming remote deletion or clearing URL."
            )
            // Consider if you want to delete from Firebase Storage: firebaseStorageRepository.deleteUserProfileImage(userId)
            remoteUrlForFirestore = ""
            newImageHashForDb = null
            requiresFirestoreUpdate =
                true // Ensure Firestore update happens if image URL is cleared
        }

        // In LocalToRemoteSyncHandler.kt, inside the pushUserToFirestore method:

        // ... (image handling logic before this block) ...

        // Inside LocalToRemoteSyncHandler.kt - pushUserToFirestore

// ... (image upload logic that sets remoteUrlForFirestore and newImageHashForDb) ...

        if (requiresFirestoreUpdate || imageActuallyUploaded) { // Ensure we proceed if an image was uploaded, even if other fields didn't change

            // Create a user object that REFLECTS THE FINAL STATE to be synced
            // This includes the URL and Hash if they were updated from the upload
            val userSnapshotForFirestore = user.copy( // 'user' is the initial state from Room
                profileImageUrl = remoteUrlForFirestore,      // This now has the potentially new URL
                profileImageHashSignature = newImageHashForDb // This now has the potentially new Hash
                // DO NOT change needsSync or needsProfileImageUpload here,
                // those are for the *local* update *after* successful Firestore sync
            )

            // Now, toFirestoreMap() will use the correct profileImageUrl and profileImageHashSignature
            // from userSnapshotForFirestore.
            // If newImageHashForDb was null, profileImageHashSignature in userSnapshotForFirestore will be null,
            // and toFirestoreMap()'s filterValues will correctly exclude it.
            // If newImageHashForDb has a value, it will be included.
            val firestoreUserData = userSnapshotForFirestore.toFirestoreMap()

            Log.d(
                specificTag,
                "Attempting Firestore update for user $userId. Data being sent: $firestoreUserData"
            )

            try {
                firestore.collection(User.COLLECTION).document(userId)
                    .set(firestoreUserData, SetOptions.merge())
                    .await()
                Log.i(specificTag, "User data successfully pushed to Firestore for user $userId.")

                // Update local Room DB AFTER successful Firestore push
                val updatedLocalUser = user.copy(
                    profileImageUrl = remoteUrlForFirestore,         // Persist final URL
                    profileImageHashSignature = newImageHashForDb,   // Persist final hash
                    needsSync = false,
                    needsProfileImageUpload = false, // Reset this flag as upload (if any) was handled
                    lastModifiedAt = Timestamp.now() // Local timestamp for the sync action
                )
                userDao.upsertUser(updatedLocalUser)
                Log.i(
                    specificTag,
                    "Local user $userId updated. Image URL: ${updatedLocalUser.profileImageUrl}, Hash: ${updatedLocalUser.profileImageHashSignature}, needsSync/needsProfileImageUpload set to false."
                )
                return true
            } catch (e: CancellationException) {
                Log.w(
                    specificTag,
                    "Firestore push or local DB update for user $userId was CANCELLED.",
                    e
                )
                throw e // Re-throw
            } catch (e: Exception) {
                Log.e(
                    specificTag,
                    "Error pushing user $userId data to Firestore or updating local DB",
                    e
                )
                return false
            }
        } else {
            // No Firestore update needed and no image was uploaded.
            // Still, if needsSync or needsProfileImageUpload was true, clear them locally.
            if (user.needsSync || user.needsProfileImageUpload) {
                Log.d(
                    specificTag,
                    "No data changes for Firestore for $userId, but resetting local sync flags."
                )
                val flagsClearedUser = user.copy(
                    needsSync = false,
                    needsProfileImageUpload = false,
                    lastModifiedAt = Timestamp.now() // Good to update modified time
                )
                userDao.upsertUser(flagsClearedUser)
            }
            return true
        }
    }

    // This specific updateLocalUserAfterPush might become redundant if pushUserToFirestore handles all local updates.
    // Kept for now for other entities that follow a separate push/updateLocal pattern.
    private suspend fun updateLocalUserAfterPush(user: User, firestoreSuccess: Boolean) {
        val specificTag = "$TAG-$TAG_USER-UpdateAfterPush"
        if (firestoreSuccess) {
            // If pushUserToFirestore already updated the local user, this might be redundant or
            // should confirm based on the object returned by pushUserToFirestore if it were to return User.
            // For now, let's assume pushUserToFirestore has handled it.
            Log.d(specificTag, "Local User ${user.id} update handled by pushUserToFirestore. Confirmation: Success=${firestoreSuccess}")
            // Example if pushUserToFirestore didn't update locally:
            // val updatedUser = user.copy(needsSync = false, lastModifiedAt = Timestamp.now())
            // userDao.upsertUser(updatedUser)
            // Log.d(specificTag, "Updated local User ${user.id}, needsSync=false (by updateLocalUserAfterPush).")
        } else {
            Log.e(specificTag, "Firestore push failed for User ${user.id}. Local flags (needsSync) likely remain true.")
        }
    }

    suspend fun pushAllPendingData(currentUserId: String, householdGroupId: String?) {
        Log.i(TAG, "Pushing all pending data for User: $currentUserId, Household: $householdGroupId")

        // User data
        val user = userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId).first()
        if (user != null && user.needsSync) {
            Log.d(TAG, "$TAG_USER-CurrentUser (PushAll): Pushing data.")
            pushUserToFirestore(user) // pushUserToFirestore now handles its own local update on success
        }

        if (householdGroupId != null && householdGroupId != Constants.DEFAULT_GROUP_ID) {
            // Tasks
            val pendingTasks = (taskDao.getModifiedTasksRequiringSync(householdGroupId).first() +
                    taskDao.getLocallyDeletedTasksRequiringSync(householdGroupId).first()).distinctBy { it.id }
            pendingTasks.forEach { task ->
                Log.d(TAG, "$TAG_TASK (PushAll): Pushing for ${task.id}.")
                val success = pushTaskToFirestore(task)
                updateLocalTaskAfterPush(task, success)
            }

            // Task History
            val pendingHistory = (taskHistoryDao.getModifiedTaskHistoryRequiringSync(householdGroupId).first() +
                    taskHistoryDao.getLocallyDeletedTaskHistoryRequiringSync(householdGroupId).first()).distinctBy { it.id }
            pendingHistory.forEach { historyItem ->
                Log.d(TAG, "$TAG_TASK_HISTORY (PushAll): Pushing for ${historyItem.id}.")
                val pushSuccess = if (historyItem.isDeletedLocally) {
                    val softDeleteOk = taskHistoryRepository.softDeleteTaskHistoryInFirestore(historyItem.id)
                    if (softDeleteOk) taskHistoryRepository.hardDeleteTaskHistoryFromFirestore(historyItem.id) else false
                } else {
                    taskHistoryRepository.createOrUpdateFirestoreTaskHistory(historyItem)
                }

                if (pushSuccess) {
                    if (historyItem.isDeletedLocally) {
                        taskHistoryDao.deleteTaskHistoryById(historyItem.id)
                    } else {
                        taskHistoryDao.insertOrUpdate(historyItem.copy(needsSync = false, lastModifiedAt = Timestamp.now()))
                    }
                }
            }

            // Group
            val group = groupDao.getGroupById(householdGroupId).first()
            if (group != null && group.needsSync) {
                Log.d(TAG, "$TAG_GROUP (PushAll): Pushing for ${group.id}.")
                val success = pushGroupToFirestore(group)
                updateLocalGroupAfterPush(group, success)
            }
        }
        Log.i(TAG, "Finished pushing all pending data.")
    }
}