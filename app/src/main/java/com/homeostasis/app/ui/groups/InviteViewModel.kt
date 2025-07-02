package com.homeostasis.app.ui.groups

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeostasis.app.data.Constants
import com.homeostasis.app.data.local.UserDao
import com.homeostasis.app.data.remote.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InviteViewModel @Inject constructor(
    private val userRepository: UserRepository, // Inject UserRepository
    private val userDao: UserDao
) : ViewModel() {

    private val TAG = "InviteViewModel"

    // StateFlow to expose the current user's household group ID
    private val _householdGroupId = MutableStateFlow<String?>(null)
    val householdGroupId: StateFlow<String?> = _householdGroupId.asStateFlow()

    init {
        Log.d(TAG, "InviteViewModel initialized")
        observeHouseholdGroupId()
    }

    private fun observeHouseholdGroupId() {
        viewModelScope.launch {
            userRepository.getCurrentUserId()?.let { userId ->
                userDao.getUserByIdWithoutHouseholdIdFlow(userId)
                    .map { user ->
                        // Provide the householdGroupId from the user object, or default if null/empty
                        user?.householdGroupId?.takeIf { it.isNotEmpty() } ?: Constants.DEFAULT_GROUP_ID
                    }
                    .collect { groupId ->
                        Log.d(TAG, "Observed householdGroupId: $groupId")
                        _householdGroupId.value = groupId
                    }
            } ?: run {
                // Handle case where user ID is null (not logged in)
                Log.w(TAG, "Current user ID is null. Cannot observe household group ID.")
                _householdGroupId.value = null // Explicitly set to null if no user
            }
        }
    }
}