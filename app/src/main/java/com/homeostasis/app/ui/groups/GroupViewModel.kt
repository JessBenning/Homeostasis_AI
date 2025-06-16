package com.homeostasis.app.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeostasis.app.data.model.Group
import com.homeostasis.app.data.remote.GroupRepository
import com.homeostasis.app.data.remote.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository, // Inject UserRepository to get current user ID
    private val userDao: com.homeostasis.app.data.UserDao, // Inject UserDao
    private val groupDao: com.homeostasis.app.data.GroupDao // Inject GroupDao
) : ViewModel() {

    fun createGroup(name: String, ownerId: String) {
        viewModelScope.launch {
            // 1. Create the new Group object locally
            val newGroup = com.homeostasis.app.data.model.Group(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                ownerId = ownerId,
                createdAt = com.google.firebase.Timestamp.now(),
                lastModifiedAt = com.google.firebase.Timestamp.now(),
                needsSync = true // Mark for sync
            )

            // 2. Save the new Group object to the local database
            groupDao.upsert(newGroup)

            // 3. Update the current user's householdGroupId in their local User object
            val currentUserId = userRepository.getCurrentUserId()
            if (currentUserId != null) {
                val currentUser = userRepository.getUser(currentUserId) // Fetch local User object
                if (currentUser != null) {
                    val updatedUser = currentUser.copy(householdGroupId = newGroup.id, needsSync = true)
                    // 4. Save the updated user to Room
                    userDao.upsertUser(updatedUser)
                }
            }
        }
    }

    fun getCurrentUserId(): String? {
        return userRepository.getCurrentUserId()
    }

    // TODO: Add other group-related functions as needed (e.g., get group details, invite members, leave group, delete group)
}