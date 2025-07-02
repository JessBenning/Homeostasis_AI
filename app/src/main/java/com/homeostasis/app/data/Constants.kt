package com.homeostasis.app.data

import java.io.File

object Constants {
    const val DEFAULT_GROUP_ID = "default_group"

        // For ProfileSettingsViewModel Image Handling
        const val PROFILE_IMAGE_MAX_DIM_PX = 250 // Max dim for resized profile image
        const val PROFILE_IMAGE_COMPRESSION_QUALITY = 85 // JPEG compression quality (0-100)
        const val PROFILE_IMAGE_MAX_SIZE_BYTES = 1 * 1024 * 1024 // 1MB max size for compressed image bytes (adjust as needed)
        const val PROFILE_IMAGE_LOCAL_FILENAME_PREFIX = "profile_picture_"
        const val PROFILE_IMAGE_LOCAL_FILENAME_SUFFIX = ".jpg"
        const val PROFILE_IMAGE_LOCAL_FILENAME_DIR = "profile_images"



    fun determineLocalFileName(userId: String): String {
        return "$PROFILE_IMAGE_LOCAL_FILENAME_PREFIX${userId}$PROFILE_IMAGE_LOCAL_FILENAME_SUFFIX"
    }

    /**
     * Gets the absolute local file path for a user's profile picture.
     * Ensures the profile_images subdirectory is included.
     *
     * @param context Context to access filesDir.
     * @param userId The ID of the user.
     * @return The absolute String path to the local profile image file.
     */
    fun getProfileImageFile(context: android.content.Context, userId: String): File {
        val profileImagesDir = File(context.filesDir, PROFILE_IMAGE_LOCAL_FILENAME_DIR)
        val localFileName = determineLocalFileName(userId)
        return File(profileImagesDir, localFileName)
    }

}

