package com.homeostasis.app.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.homeostasis.app.data.Constants
import com.homeostasis.app.data.local.UserDao
import com.homeostasis.app.data.model.User // Assume User model *no longer needs* clearRemoteProfileImage for this VM's logic
// It will still need needsProfileImageUpload
import com.homeostasis.app.data.remote.UserRepository
import com.homeostasis.app.utils.ImageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileSettingsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val userDao: UserDao,
    private val imageUtils: ImageUtils,
    @ApplicationContext private val applicationContext: Context
) : ViewModel() {

    private val _currentUserId = MutableLiveData<String?>()

    val userProfile: LiveData<User?> = _currentUserId.asFlow()
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf<User?>(null)
            } else {
                userDao.getUserByIdWithoutHouseholdIdFlow(userId)
                    .flatMapLatest { userWithHIdObject ->
                        val householdGroupId = userWithHIdObject?.householdGroupId?.takeIf { it.isNotEmpty() }
                        if (householdGroupId != null) {
                            userDao.getUserByIdFlow(userId, householdGroupId)
                        } else {
                            Log.w("ProfileSettingsVM", "User $userId has no valid householdGroupId. Profile data might be limited but user object will be provided.")
                            flowOf(userWithHIdObject)
                        }
                    }
            }
        }.asLiveData(viewModelScope.coroutineContext)

    private val _selectedImagePreview = MutableLiveData<ByteArray?>()
    val selectedImagePreview: LiveData<ByteArray?> get() = _selectedImagePreview

    private val _saveProfileStatus = Channel<Boolean>(Channel.Factory.BUFFERED)
    val saveProfileStatus = _saveProfileStatus.receiveAsFlow()

    private var pendingImageUri: Uri? = null

    init {
        viewModelScope.launch {
            userRepository.observeFirebaseAuthUid().collect { uid ->
                _currentUserId.value = uid
                if (uid == null) {
                    clearPendingImageData()
                }
            }
        }
    }

    fun handleImageSelection(imageUri: Uri) {
        pendingImageUri = imageUri
        viewModelScope.launch {
            try {
                val bitmap = imageUtils.decodeUriToBitmap(applicationContext, imageUri)
                bitmap?.let {
                    val resizedBitmap = ImageUtils.resizeBitmap(
                        it,
                        Constants.PROFILE_IMAGE_MAX_DIM_PX
                    )
                    val compressedBytes = ImageUtils.compressBitmapToByteArray(
                        resizedBitmap,
                        Constants.PROFILE_IMAGE_MAX_SIZE_BYTES,
                        Constants.PROFILE_IMAGE_COMPRESSION_QUALITY
                    )
                    _selectedImagePreview.postValue(compressedBytes)
                } ?: run {
                    Log.e("ProfileSettingsVM", "Failed to decode bitmap from URI.")
                    clearPendingImageData()
                }
            } catch (e: Exception) {
                Log.e("ProfileSettingsVM", "Error processing image selection", e)
                clearPendingImageData()
                _saveProfileStatus.trySend(false)
            }
        }
    }

    fun saveProfile(name: String) {
        viewModelScope.launch {
            val userId = _currentUserId.value
            if (userId == null) {
                Log.w("ProfileSettingsVM", "Cannot save profile, user ID is null.")
                _saveProfileStatus.trySend(false)
                return@launch
            }

            val currentUserState = userProfile.value
            if (currentUserState == null) {
                Log.e("ProfileSettingsVM", "Cannot save profile for user $userId, existing user data not loaded.")
                _saveProfileStatus.trySend(false)
                return@launch
            }

            var newImageSavedLocallySuccess = false
            var newLocalImagePath: String? = null // Store the path if saved

            if (pendingImageUri != null && _selectedImagePreview.value != null) {
                val imageBytesToSave = _selectedImagePreview.value!!
                try {
                    val localFile = Constants.getProfileImageFile(applicationContext, userId)
                    FileOutputStream(localFile).use { fos ->
                        fos.write(imageBytesToSave)
                    }
                    newImageSavedLocallySuccess = true
                    newLocalImagePath = localFile.absolutePath // Keep track of the new local path
                    Log.d("ProfileSettingsVM", "New profile image for user $userId saved locally to: $newLocalImagePath")
                } catch (e: IOException) {
                    Log.e("ProfileSettingsVM", "Error saving new profile image locally for user $userId", e)
                    // newImageSavedLocallySuccess remains false
                }
            }

            val needsUpload = newImageSavedLocallySuccess

            // profileImageUrl in Room remains the *current remote URL* or empty.
            // If a new image is uploaded, SyncManager will update this URL.
            // If no new image is selected/saved, it remains currentUserState.profileImageUrl
            val profileImageUrlForDb = currentUserState.profileImageUrl

            val updatedUser = currentUserState.copy(
                name = name.trim(),
                profileImageUrl = profileImageUrlForDb, // Stays as current remote URL; SyncManager handles updates
                lastModifiedAt = Timestamp.now(),
                needsSync = true, // Set to true if name changed or if a new image needs upload
                needsProfileImageUpload = needsUpload,
                // clearRemoteProfileImage is removed from consideration here
                // Reset hash signature if a new image is being uploaded, otherwise keep current
                profileImageHashSignature = if (needsUpload) null else currentUserState.profileImageHashSignature
            )

            try {
                userDao.upsertUser(updatedUser)
                Log.d("ProfileSettingsVM", "User profile for $userId updated in local DB. NeedsUpload: $needsUpload. ImagePath in DB: ${updatedUser.profileImageUrl}")
                _saveProfileStatus.trySend(true)
            } catch (dbException: Exception) {
                Log.e("ProfileSettingsVM", "Failed to upsert user to local DB for $userId", dbException)
                _saveProfileStatus.trySend(false)
            } finally {
                clearPendingImageData() // Clear pending URI and preview after save attempt
            }
        }
    }

    private fun clearPendingImageData() {
        pendingImageUri = null
        _selectedImagePreview.postValue(null)
    }

    /**
     * Public method to allow UI to explicitly clear the new image selection/preview
     * if the user changes their mind *before* saving.
     */
    fun cancelImageSelection() {
        clearPendingImageData()
    }
}