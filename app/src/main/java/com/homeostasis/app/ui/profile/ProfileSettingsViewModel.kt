package com.homeostasis.app.ui.profile

import androidx.lifecycle.ViewModel
import android.content.Context // Import Context
import androidx.lifecycle.viewModelScope
import com.homeostasis.app.data.UserDao // Import UserDao
import com.homeostasis.app.data.HouseholdGroupIdProvider // Import HouseholdGroupIdProvider
import com.homeostasis.app.data.remote.UserRepository
import com.homeostasis.app.utils.ImageUtils
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.homeostasis.app.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext // Import ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first // Import first
import java.io.File // Import File
import java.io.FileOutputStream // Import FileOutputStream
import java.io.IOException // Import IOException
import javax.inject.Inject

@HiltViewModel
class ProfileSettingsViewModel @Inject constructor(
    private val userRepository: UserRepository, // Keep UserRepository for loading data
    private val userDao: UserDao, // Inject UserDao
    private val householdGroupIdProvider: HouseholdGroupIdProvider, // Inject HouseholdGroupIdProvider
    private val imageUtils: ImageUtils, // Inject ImageUtils
    @ApplicationContext private val context: Context // Inject ApplicationContext
) : ViewModel() {

    private val _userProfile = MutableLiveData<User?>()
    val userProfile: LiveData<User?> get() = _userProfile

    private val _selectedImagePreview = MutableLiveData<ByteArray?>()
    val selectedImagePreview: LiveData<ByteArray?> get() = _selectedImagePreview

    private var selectedImageBytes: ByteArray? = null

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            userRepository.getCurrentUserId()?.let { userId ->
                val result = userRepository.getUser(userId)
                _userProfile.postValue(result)//.getOrNull())
            }
        }
    }

    /**
     * Handles the selected image URI, processes it, and stores the result.
     */
    fun handleImageSelection(context: android.content.Context, imageUri: android.net.Uri) {
        viewModelScope.launch {
            val bitmap = imageUtils.decodeUriToBitmap(context, imageUri)
            bitmap?.let {
                val resizedBitmap = ImageUtils.resizeBitmap(it, 250) // Minimum size 250x250
                val compressedBytes = ImageUtils.compressBitmapToByteArray(resizedBitmap, 1 * 1024 * 1024) // Max 1MB
                selectedImageBytes = compressedBytes
                _selectedImagePreview.postValue(compressedBytes) // Update LiveData for preview
            } ?: run {
                _selectedImagePreview.postValue(null) // Clear preview if decoding fails
            }
        }
    }


    /**
     * Saves the user profile with the given name and selected image to the local database.
     * The sync manager will handle pushing changes to the remote database.
     */
    fun saveProfile(name: String) {
        viewModelScope.launch {
            val userId = userRepository.getCurrentUserId()
            val householdGroupId = householdGroupIdProvider.getHouseholdGroupId().first()

            if (userId != null && householdGroupId != null) {
                // Get the current user from the local DB to preserve other fields
                val existingUser = userDao.getUserById(userId, householdGroupId)

                if (existingUser != null) {
                   // If a new image was selected, save it locally
                   selectedImageBytes?.let { bytes ->
                       try {
                           val file = File(context.filesDir, "profile_picture_$userId.jpg") // Consistent local file path
                           FileOutputStream(file).use { fos ->
                               fos.write(bytes)
                           }
                           selectedImageBytes = null // Clear temporary bytes
                           // TODO: Handle local file save success/failure
                       } catch (e: IOException) {
                           e.printStackTrace()
                           // TODO: Handle local file save error
                       }
                   }

                   // Update the local user with the new name and mark for sync.
                   // profileImageUrl remains the remote URL.
                   val updatedUser = existingUser.copy(
                       name = name,
                       needsSync = true // Mark for sync
                   )

                    userDao.upsertUser(updatedUser)
                    // TODO: Handle save result (success/failure) - check upsertUser result if needed
                } else {
                   // Local user data not found, create a new User object
                   // If a new image was selected, save it locally
                   selectedImageBytes?.let { bytes ->
                       try {
                           val file = File(context.filesDir, "profile_picture_$userId.jpg") // Consistent local file path
                           FileOutputStream(file).use { fos ->
                               fos.write(bytes)
                           }
                           selectedImageBytes = null // Clear temporary bytes
                           // TODO: Handle local file save success/failure
                       } catch (e: IOException) {
                           e.printStackTrace()
                           // TODO: Handle local file save error
                       }
                   }

                   val newUser = User(
                       id = userId,
                       name = name,
                       profileImageUrl = "", // profileImageUrl should be empty initially for a new user
                       householdGroupId = householdGroupId,
                       needsSync = true // Mark for sync
                       // Other fields will use their default values
                   )
                   userDao.upsertUser(newUser)
                   // TODO: Handle save result (success/failure)
               }
           } else {
               // TODO: Handle case where userId or householdGroupId is null (e.g., show error message)
           }
       }
   }
}