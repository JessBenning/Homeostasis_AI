package com.homeostasis.app.ui.settings

import android.net.Uri // Import Uri for parsing
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeostasis.app.data.UserDao
import com.homeostasis.app.data.model.Group
import com.homeostasis.app.data.remote.GroupRepository
import com.homeostasis.app.data.remote.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AcceptInviteViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val userDao: UserDao,
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val TAG = "AcceptInviteViewModel"

    // Channel for sending one-time events back to the UI (e.g., success, error messages)
    private val _acceptInviteResult = Channel<AcceptInviteResult>(Channel.BUFFERED)
    val acceptInviteResult = _acceptInviteResult.receiveAsFlow()

    sealed class AcceptInviteResult {
        object Success : AcceptInviteResult()
        data class Error(val message: String) : AcceptInviteResult()
        data class ValidationFailed(val message: String) : AcceptInviteResult()
    }

    /**
     * Processes the invite link, validates it, checks group existence, and updates user's group ID.
     */



    fun processInviteLink(inviteLink: String) {
        viewModelScope.launch {

            Log.d(TAG, "Processing invite link: $inviteLink")
            // 1. Validate the link format and extract groupId
            val groupId = extractGroupIdFromLink(inviteLink)

            if (groupId == null) {
                _acceptInviteResult.send(AcceptInviteResult.ValidationFailed("Invalid invite link format."))
                Log.w(TAG, "Invalid invite link format: $inviteLink")
                return@launch
            }

            // 2. Check if the group exists in Firestore
            val group = groupRepository.getGroupByIdFromFirestore(groupId)

            if (group == null) {
                _acceptInviteResult.send(AcceptInviteResult.ValidationFailed("Group with this ID does not exist in Firestore."))
                Log.w(TAG, "Group with ID $groupId not found in Firestore.")
                return@launch
            }
            Log.i(TAG, "Group found in Firestore: ${group.name}")

            // 3. Get current user and update their householdGroupId
            val currentUserId = userRepository.getCurrentUserId()

            if (currentUserId == null) {
                _acceptInviteResult.send(AcceptInviteResult.Error("User not logged in."))
                Log.e(TAG, "Current user ID is null. Cannot accept invite.")
                return@launch
            }

            try {
                // Fetch the current user from the local database
                val currentUser = userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId).firstOrNull()

                if (currentUser != null) {
                    // Update the user's householdGroupId and mark for sync
                    val updatedUser = currentUser.copy(
                        householdGroupId = groupId,
                        needsSync = true // Mark for sync
                    )
                    userDao.upsertUser(updatedUser) // Save updated user to local DB

                    _acceptInviteResult.send(AcceptInviteResult.Success)
                    Log.i(TAG, "User $currentUserId successfully joined group $groupId.")
                } else {
                    _acceptInviteResult.send(AcceptInviteResult.Error("Current user data not found locally."))
                    Log.e(TAG, "Current user data not found locally for ID: $currentUserId")
                }

            } catch (e: Exception) {
                _acceptInviteResult.send(AcceptInviteResult.Error("Error accepting invite: ${e.message}"))
                Log.e(TAG, "Error updating user's group ID.", e)
            }
        }
    }

    /**
     * Extracts the groupId from a homeostasis://invite?groupId=... link.
     * Returns the groupId or null if the format is invalid.
     */
    private fun extractGroupIdFromLink(inviteLink: String): String? {
        return try {
            val uri = Uri.parse(inviteLink)
            if (uri.scheme == "homeostasis" && uri.host == "invite") {
                uri.getQueryParameter("groupId")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing invite link URI: $inviteLink", e)
            null
        }
    }
}