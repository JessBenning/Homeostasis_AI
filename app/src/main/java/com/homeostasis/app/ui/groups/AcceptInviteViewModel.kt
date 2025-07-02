package com.homeostasis.app.ui.groups

import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData // Import LiveData
import androidx.lifecycle.MutableLiveData // Import MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeostasis.app.data.local.UserDao
import com.homeostasis.app.data.remote.GroupRepository
import com.homeostasis.app.data.remote.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
//import kotlinx.coroutines.channels.Channel // Keep if used for other more complex events
//import kotlinx.coroutines.flow.receiveAsFlow // Keep if Channel is used elsewhere
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AcceptInviteViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val userDao: UserDao,
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val TAG = "AcceptInviteViewModel"

    // LiveData for signaling simple success/failure of the invite process to the DialogFragment
    private val _inviteProcessComplete = MutableLiveData<Boolean>()
    val inviteProcessComplete: LiveData<Boolean> = _inviteProcessComplete

    // You can still keep your Channel for more granular error/state reporting if other parts of
    // your app need it, or if the DialogFragment needs to show more specific error messages
    // directly based on these ViewModel events. For the DialogFragment's basic "succeeded or not"
    // communication, LiveData<Boolean> is simpler.
    //
    // Example: If you wanted the DialogFragment to show "Invalid Link" vs "Group not Found"
    // you could expose the sealed class result as another LiveData or transform the channel.
    // For now, _inviteProcessComplete just signals overall success/failure of the operation.

    /*
    // Kept for reference if you want more detailed feedback to the UI later
    private val _acceptInviteChannel = Channel<AcceptInviteResultEvent>(Channel.Factory.BUFFERED)
    val acceptInviteEvent = _acceptInviteChannel.receiveAsFlow()

    sealed class AcceptInviteResultEvent {
        object Success : AcceptInviteResultEvent()
        data class Error(val message: String) : AcceptInviteResultEvent()
        // ValidationFailed could be a type of Error or separate if needed
    }
    */

    /**
     * Processes the invite link, validates it, checks group existence, and updates user's group ID.
     * Signals completion (success/failure) via _inviteProcessComplete LiveData.
     */
    fun processInviteLink(inviteLink: String, isFromOnboarding: Boolean) { // isFromOnboarding might be used for analytics or slightly different logic later
        viewModelScope.launch {
            Log.d(TAG, "Processing invite link: $inviteLink (isFromOnboarding: $isFromOnboarding)")

            // 1. Validate the link format and extract groupId
            val groupId = extractGroupIdFromLink(inviteLink)
            if (groupId == null) {
                Log.w(TAG, "Invalid invite link format: $inviteLink")
                // _acceptInviteChannel.send(AcceptInviteResultEvent.Error("Invalid invite link format.")) // If using channel for detailed errors
                _inviteProcessComplete.postValue(false) // Signal failure
                return@launch
            }

            // 2. Check if the group exists in Firestore
            val group = groupRepository.getGroupByIdFromFirestore(groupId)
            if (group == null) {
                Log.w(TAG, "Group with ID $groupId not found in Firestore.")
                // _acceptInviteChannel.send(AcceptInviteResultEvent.Error("Group not found.")) // If using channel
                _inviteProcessComplete.postValue(false) // Signal failure
                return@launch
            }
            Log.i(TAG, "Group found in Firestore: ${group.name}")

            // 3. Get current user
            val currentUserId = userRepository.getCurrentUserId()
            if (currentUserId == null) {
                Log.e(TAG, "Current user ID is null. Cannot accept invite.")
                // _acceptInviteChannel.send(AcceptInviteResultEvent.Error("User not logged in.")) // If using channel
                _inviteProcessComplete.postValue(false) // Signal failure
                return@launch
            }

            // 4. Update user's householdGroupId in local DB
            try {
                val currentUser = userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId).firstOrNull()
                if (currentUser != null) {
                    val updatedUser = currentUser.copy(
                        householdGroupId = groupId,
                        needsSync = true // Mark for sync with Firestore
                    )
                    userDao.upsertUser(updatedUser)
                    Log.i(TAG, "User $currentUserId successfully joined group $groupId locally. Marked for sync.")
                    // _acceptInviteChannel.send(AcceptInviteResultEvent.Success) // If using channel
                    _inviteProcessComplete.postValue(true) // Signal success
                } else {
                    Log.e(TAG, "Current user data not found locally for ID: $currentUserId")
                    // _acceptInviteChannel.send(AcceptInviteResultEvent.Error("Local user data not found.")) // If using channel
                    _inviteProcessComplete.postValue(false) // Signal failure
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating user's group ID locally.", e)
                // _acceptInviteChannel.send(AcceptInviteResultEvent.Error("Error saving changes: ${e.message}")) // If using channel
                _inviteProcessComplete.postValue(false) // Signal failure
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