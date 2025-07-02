package com.homeostasis.app.ui.groups

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeostasis.app.data.local.GroupDao
import com.homeostasis.app.data.local.UserDao
import com.homeostasis.app.data.remote.GroupRepository
import com.homeostasis.app.data.remote.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class GroupViewModel @Inject constructor(
    private val groupRepository: GroupRepository, // This WILL NOW be used for REMOTE operations
    private val userRepository: UserRepository,
    private val userDao: UserDao,
    private val groupDao: GroupDao
) : ViewModel() {

    sealed class CreateGroupResult {
        object Success : CreateGroupResult()
        data class Error(val message: String) : CreateGroupResult()
        object InProgress : CreateGroupResult()
    }

    private val _createGroupResult = MutableStateFlow<CreateGroupResult?>(null)
    val createGroupResult: StateFlow<CreateGroupResult?> = _createGroupResult

    fun createGroup(groupName: String) {
        viewModelScope.launch {
            _createGroupResult.value = CreateGroupResult.InProgress
            Log.d("GroupViewModel", "[${Thread.currentThread().name}] Starting createGroup for name: $groupName")

            val currentUserId = userRepository.getCurrentUserId()
            if (currentUserId == null) {
                Log.e("GroupViewModel", "Cannot create group: Current User ID is null.")
                _createGroupResult.value = CreateGroupResult.Error("User not logged in.")
                return@launch
            }

            var newGroupId = "" // To store the ID for local user update

            try {
                // Step 1: Create the new Group object for Firestore
                // Firestore will generate its own lastModifiedAt on write, but we need createdAt.
                // We don't set needsSync here as it's going directly to Firestore.
                val groupForRemote = com.homeostasis.app.data.model.Group(
                    id = java.util.UUID.randomUUID().toString(), // Or let Firestore generate if you prefer, but you'll need it back
                    name = groupName,
                    ownerId = currentUserId,
                    createdAt = com.google.firebase.Timestamp.now(),
                    lastModifiedAt = com.google.firebase.Timestamp.now(), // Firestore will overwrite this with server timestamp
                    // isDeleted, members, etc. as needed for Firestore schema
                    needsSync = false // This object is for direct Firestore write initially
                )
                newGroupId = groupForRemote.id // Store the ID
                Log.d("GroupViewModel", "[${Thread.currentThread().name}] Group object for Firestore prepared: ${newGroupId}")

                // Step 2 (Option 3 - Step a & b): Write the group document to Firestore AND AWAIT
                // Ensure GroupRepository.createGroupInFirestore is a suspend function that returns success/failure
                // and handles the actual Firestore write. It should not just write to local DAO.
                val groupCreationSuccessful = groupRepository.createGroupInFirestore(groupForRemote)
                Log.d("GroupViewModel", "[${Thread.currentThread().name}] Firestore group creation attempt for ${newGroupId}, Success: $groupCreationSuccessful")

                if (!groupCreationSuccessful) {
                    _createGroupResult.value = CreateGroupResult.Error("Failed to create group on the server.")
                    return@launch
                }

                // Step 3 (Local Cache Update for the Group):
                // Now that the group is in Firestore, also cache it locally.
                // Mark it as NOT needing sync because it's already on the server.
                // The lastModifiedAt should ideally match what the server set,
                // but for client-side representation, using the server-generated one from a listener is best.
                // For now, we update with what we have, knowing the listener will correct it.
                val localGroupCopy = groupForRemote.copy(
                    needsSync = false, // It's on Firestore
                    lastModifiedAt = com.google.firebase.Timestamp.now() // Or try to get server timestamp if possible from createGroupInFirestore response
                )
                groupDao.upsert(localGroupCopy) // Cache the group locally
                Log.d("GroupViewModel", "[${Thread.currentThread().name}] Group ${newGroupId} cached locally in groupDao.")


                // Step 4 (Option 3 - Step c): Update the local user's householdGroupId
                val currentUser = userDao.getUserById(currentUserId) // Fetch fresh local user
                if (currentUser == null) {
                    Log.e("GroupViewModel", "Cannot update user: Current User object not found in local DB for ID: $currentUserId after group creation.")
                    // This is less likely if the initial check passed, but good to have
                    _createGroupResult.value = CreateGroupResult.Error("Failed to retrieve local user data to update group.")
                    // Potentially consider how to handle cleanup if group was created but user can't be updated.
                    return@launch
                }
                Log.d("GroupViewModel", "[${Thread.currentThread().name}] Current user found for update: ${currentUser.id}")

                val updatedUser = currentUser.copy(
                    householdGroupId = newGroupId,
                    needsSync = true, // This user update needs to be synced to Firestore
                    lastModifiedAt = com.google.firebase.Timestamp.now()
                )
                userDao.upsertUser(updatedUser)
                Log.d("GroupViewModel", "[${Thread.currentThread().name}] Local user ${updatedUser.id} updated with householdGroupId ${newGroupId} and marked for sync.")

                // Step 5 (Option 3 - Step d - Implicit):
                // - LocalToRemoteSyncHandler will pick up the 'updatedUser' and sync it to Firestore.
                // - When this User update hits Firestore, RemoteToLocalSyncHandler's `setupRemoteCurrentUserListener`
                //   will detect the change in `householdGroupId`.
                // - FirebaseSyncManager (or logic within RemoteToLocalSyncHandler) should then
                //   trigger listeners for the new 'newGroupId'. Since the group was created in Firestore
                //   in Step 2, the `setupRemoteGroupDocumentListener` will find it.

                _createGroupResult.value = CreateGroupResult.Success
                Log.d("GroupViewModel", "[${Thread.currentThread().name}] Finished createGroup coroutine successfully for group ${newGroupId}.")

            } catch (e: CancellationException) {
                Log.w("GroupViewModel", "[${Thread.currentThread().name}] createGroup coroutine was cancelled.", e)
                _createGroupResult.value = CreateGroupResult.Error("Operation cancelled.")
                throw e // Re-throw if you want higher-level cancellation handling
            } catch (e: Exception) {
                Log.e("GroupViewModel", "[${Thread.currentThread().name}] Error in createGroup: ${e.message}", e)
                _createGroupResult.value = CreateGroupResult.Error(e.message ?: "Unknown error creating group.")
            }
        }
    }

    fun consumeCreateGroupResult() {
        _createGroupResult.value = null
    }

    fun getCurrentUserId(): String? {
        return userRepository.getCurrentUserId()
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("GroupViewModel", "[${Thread.currentThread().name}] onCleared() called.")
    }
}