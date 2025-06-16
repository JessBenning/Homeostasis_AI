package com.homeostasis.app.ui.profile

import androidx.lifecycle.ViewModel
import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.homeostasis.app.data.UserDao
import com.homeostasis.app.data.HouseholdGroupIdProvider
import com.homeostasis.app.data.remote.UserRepository
import com.homeostasis.app.utils.ImageUtils
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
// viewModelScope is already imported
import com.homeostasis.app.data.model.User
import com.google.firebase.Timestamp // <<< ADD THIS IMPORT
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.Channel // Import Channel
import kotlinx.coroutines.flow.receiveAsFlow // Import receiveAsFlow
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class ProfileSettingsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val userDao: UserDao,
    private val taskHistoryRepository: com.homeostasis.app.data.remote.TaskHistoryRepository, // Inject TaskHistoryRepository
    private val firebaseSyncManager: com.homeostasis.app.data.FirebaseSyncManager, // Inject FirebaseSyncManager
    private val householdGroupIdProvider: HouseholdGroupIdProvider,
    private val imageUtils: ImageUtils,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _userProfile = MutableLiveData<User?>()
    val userProfile: LiveData<User?> get() = _userProfile

    private val _resetScoresEvent = Channel<Unit>(Channel.BUFFERED) // Channel for one-time events
    val resetScoresEvent = _resetScoresEvent.receiveAsFlow() // Expose as Flow

    private val _selectedImagePreview = MutableLiveData<ByteArray?>()
    val selectedImagePreview: LiveData<ByteArray?> get() = _selectedImagePreview

    private var selectedImageBytes: ByteArray? = null

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            // Consider fetching from local UserDao first for faster initial load,
            // then optionally refresh from remote if needed or let sync manager handle updates.
            userRepository.getCurrentUserId()?.let { userId ->
                // Example: Fetch from local first
                val localUser = householdGroupIdProvider.getHouseholdGroupId().first()?.let { hid ->
                    userDao.getUserById(userId, hid)
                }
                if (localUser != null) {
                    _userProfile.postValue(localUser)
                } else {
                    // Fallback to remote if not found locally or if you prefer remote first
                    val remoteUser = userRepository.getUser(userId)
                    _userProfile.postValue(remoteUser)
                }
            }
        }
    }

    fun handleImageSelection(context: android.content.Context, imageUri: android.net.Uri) {
        viewModelScope.launch {
            val bitmap = imageUtils.decodeUriToBitmap(context, imageUri)
            bitmap?.let {
                val resizedBitmap = ImageUtils.resizeBitmap(it, 250)
                val compressedBytes =
                    ImageUtils.compressBitmapToByteArray(resizedBitmap, 1 * 1024 * 1024)
                selectedImageBytes = compressedBytes
                _selectedImagePreview.postValue(compressedBytes)
            } ?: run {
                _selectedImagePreview.postValue(null)
            }
        }
    }

    fun saveProfile(name: String) {
        viewModelScope.launch {
            val userId = userRepository.getCurrentUserId()
            val householdGroupId = householdGroupIdProvider.getHouseholdGroupId().first()

            if (userId != null && householdGroupId != null) {
                val existingUser = userDao.getUserById(userId, householdGroupId)
                val currentTime = Timestamp.now() // Get current time once

                var localImageSaveSuccess = true // Assume success unless it fails
                selectedImageBytes?.let { bytes ->
                    try {
                        val file = File(context.filesDir, "profile_picture_$userId.jpg")
                        FileOutputStream(file).use { fos ->
                            fos.write(bytes)
                        }
                        Log.d(
                            "ProfileSettingsVM",
                            "New profile image saved locally to: ${file.absolutePath}"
                        )
                        selectedImageBytes = null // Clear temporary bytes after successful save
                    } catch (e: IOException) {
                        e.printStackTrace()
                        localImageSaveSuccess = false
                        Log.e("ProfileSettingsVM", "Error saving image locally", e)
                        // TODO: Handle local file save error (e.g., show a message to the user)
                    }
                }

                // Proceed with DB update only if local image save was successful (if an image was selected)
                if (!localImageSaveSuccess && selectedImageBytes != null /* check if image was selected to ensure error is relevant */) {
                    // Don't update DB if critical image save failed.
                    // Or, decide if DB update (like name) should proceed anyway.
                    // For now, let's assume if image was selected & failed, we might stop.
                    Log.e("ProfileSettingsVM", "Aborting profile save due to image save failure.")
                    // TODO: Notify user of failure
                    return@launch
                }


                if (existingUser != null) {
                    val updatedUser = existingUser.copy(
                        name = name,
                        lastModifiedAt = currentTime, // <<< SET/UPDATE lastModifiedAt
                        needsSync = true
                    )
                    userDao.upsertUser(updatedUser)
                    Log.d(
                        "ProfileSettingsVM",
                        "Existing user ${updatedUser.id} updated in local DB. New lastModifiedAt: ${currentTime.toDate()}"
                    )
                    _userProfile.postValue(updatedUser) // Optionally update LiveData for the current screen
                } else {
                    // Create new user
                    val newUser = User(
                        id = userId,
                        name = name,
                        profileImageUrl = "", // This should be updated by sync manager when remote URL is available
                        householdGroupId = householdGroupId,
                        createdAt = currentTime,      // <<< SET createdAt for new user
                        lastModifiedAt = currentTime, // <<< SET lastModifiedAt for new user
                        needsSync = true
                        // Ensure other User fields have appropriate defaults or are set here
                    )
                    userDao.upsertUser(newUser)
                    Log.d(
                        "ProfileSettingsVM",
                        "New user ${newUser.id} created in local DB. lastModifiedAt: ${currentTime.toDate()}"
                    )
                    _userProfile.postValue(newUser) // Optionally update LiveData
                }
            } else {
                Log.w(
                    "ProfileSettingsVM",
                    "Cannot save profile, userId or householdGroupId is null."
                )
                // TODO: Handle case where userId or householdGroupId is null
            }
        }
    }
        fun resetScoresAndHistory() {
            viewModelScope.launch {
                try {
                    // Delete locally first
                    taskHistoryRepository.deleteAllTaskHistory()
                    Log.d("ProfileSettingsVM", "Local task history deleted.")

                    // Then trigger remote sync for deleted history
                    firebaseSyncManager.syncDeletedTaskHistoryRemote()
                    Log.d("ProfileSettingsVM", "Remote sync for deleted task history triggered.")

                    _resetScoresEvent.send(Unit) // Signal success to Fragment
                } catch (e: Exception) {
                    Log.e("ProfileSettingsVM", "Error resetting scores and history", e)
                    // TODO: Handle error (e.g., show an error message to the user)
                }
            }
        }
    }
