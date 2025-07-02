package com.homeostasis.app.data.remote

import android.net.Uri
import android.util.Log // Import Log

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID
import javax.inject.Inject // Assuming you might use Hilt
import javax.inject.Singleton // Assuming you might use Hilt

/**
 * Repository for Firebase Storage operations.
 */
@Singleton // Add if using Hilt
class FirebaseStorageRepository @Inject constructor() { // Add @Inject constructor() if using Hilt

    private val storage: FirebaseStorage by lazy {
        FirebaseStorage.getInstance()
    }

    private val storageRef: StorageReference by lazy {
        storage.reference
    }

    companion object { // Add companion object for constants and TAG //TODO move to constants
        private const val TAG = "FirebaseStorageRepo"
        private const val USER_PROFILES_STORAGE_PATH = "user_profile_images" // Base path in Storage for user profiles
    }

    /**
     * Upload a file to Firebase Storage.
     *
     * @param uri The URI of the file to upload
     * @param path The path in Firebase Storage where the file should be stored
     * @param fileName The name of the file (if null, a random UUID will be generated)
     * @return The download URL of the uploaded file, or null if the upload failed
     */
    suspend fun uploadFile(uri: Uri, path: String, fileName: String? = null): String? {
        return try {
            val name = fileName ?: UUID.randomUUID().toString()
            // Ensure path doesn't end with a slash and name doesn't start with one for clean concatenation
            val fullPath = if (path.endsWith("/")) "$path$name" else "$path/$name"
            val fileRef = storageRef.child(fullPath)
            Log.d(TAG, "Uploading URI to: ${fileRef.path}")

            fileRef.putFile(uri).await()
            val downloadUrl = fileRef.downloadUrl.await().toString()
            Log.i(TAG, "File uploaded via URI successfully. URL: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading file via URI to path '$path' with name '$fileName'", e)
            null
        }
    }

    /**
     * Upload a file to Firebase Storage.
     *
     * @param file The file to upload
     * @param path The path in Firebase Storage where the file should be stored
     * @param fileName The name of the file (if null, a random UUID will be generated)
     * @return The download URL of the uploaded file, or null if the upload failed
     */
    suspend fun uploadFile(file: File, path: String, fileName: String? = null): String? {
        if (!file.exists()) {
            Log.e(TAG, "File to upload does not exist: ${file.absolutePath}")
            return null
        }
        return try {
            val name = fileName ?: UUID.randomUUID().toString()
            // Ensure path doesn't end with a slash and name doesn't start with one for clean concatenation
            val fullPath = if (path.endsWith("/")) "$path$name" else "$path/$name"
            val fileRef = storageRef.child(fullPath)
            Log.d(TAG, "Uploading File to: ${fileRef.path}")

            fileRef.putFile(Uri.fromFile(file)).await()
            val downloadUrl = fileRef.downloadUrl.await().toString()
            Log.i(TAG, "File uploaded successfully. URL: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading file '${file.name}' to path '$path' with remote name '$fileName'", e)
            null
        }
    }

    /**
     * Uploads a user's profile image to a designated path in Firebase Storage.
     * This method uses the generic uploadFile method.
     *
     * @param userId The ID of the user. This will be used to create a subfolder for the user.
     * @param localFile The local image File to upload.
     * @return The public download URL string of the uploaded image, or null if upload fails.
     */
    suspend fun uploadUserProfileImage(userId: String, localFile: File): String? {
        if (!localFile.exists()) {
            Log.e(TAG, "Local file for user profile image does not exist: ${localFile.absolutePath}")
            return null
        }

        // Determine the remote file name, e.g., "profile.jpg" or include userId for uniqueness if needed.
        // Using a consistent name like "profile.jpg" within the user's folder is often fine.
        val fileExtension = localFile.extension.ifBlank { "jpg" } // Default to jpg if no extension
        val remoteFileName = "profile.$fileExtension" // e.g., profile.jpg or profile.png

        // Construct the specific path for this user's profile image
        // e.g., "user_profile_images/USER_ID/"
        // The generic uploadFile will append remoteFileName to this path.
        val userSpecificPath = "$USER_PROFILES_STORAGE_PATH/$userId"

        Log.d(TAG, "Attempting to upload user profile image for user '$userId' from '${localFile.path}' to Storage path '$userSpecificPath' with name '$remoteFileName'")

        // Call the existing generic uploadFile method
        return uploadFile(file = localFile, path = userSpecificPath, fileName = remoteFileName)
    }


    /**
     * Deletes a user's profile image from Firebase Storage.
     * This uses the generic deleteFile method.
     *
     * @param userId The ID of the user.
     * @param fileExtension The extension of the profile file (e.g., "jpg", "png"). Used to reconstruct the filename.
     * @return True if deletion was successful or file didn't exist, false otherwise.
     */
    suspend fun deleteUserProfileImage(userId: String, fileExtension: String = "jpg"): Boolean {
        val remoteFileName = "profile.$fileExtension"
        val userSpecificPath = "$USER_PROFILES_STORAGE_PATH/$userId"

        Log.d(TAG, "Attempting to delete user profile image for user '$userId' at Storage path '$userSpecificPath' with name '$remoteFileName'")

        // Before calling delete, you might want to check if the file exists if `deleteFile`
        // doesn't already handle "object not found" gracefully as a non-error.
        // Your current deleteFile returns false on exception, so it's okay.
        return deleteFile(path = userSpecificPath, fileName = remoteFileName)
    }


    /**
     * Upload a byte array to Firebase Storage.
     *
     * @param bytes The byte array to upload
     * @param path The path in Firebase Storage where the file should be stored
     * @param fileName The name of the file (if null, a random UUID will be generated)
     * @param contentType The MIME type of the file
     * @return The download URL of the uploaded file, or null if the upload failed
     */
    suspend fun uploadBytes(
        bytes: ByteArray,
        path: String,
        fileName: String? = null,
        contentType: String? = null
    ): String? {
        return try {
            val name = fileName ?: UUID.randomUUID().toString()
            val fullPath = if (path.endsWith("/")) "$path$name" else "$path/$name"
            val fileRef = storageRef.child(fullPath)
            Log.d(TAG, "Uploading Bytes to: ${fileRef.path}")


            val metadata = contentType?.let {
                com.google.firebase.storage.StorageMetadata.Builder()
                    .setContentType(it)
                    .build()
            }

            if (metadata != null) {
                fileRef.putBytes(bytes, metadata).await()
            } else {
                fileRef.putBytes(bytes).await()
            }

            val downloadUrl = fileRef.downloadUrl.await().toString()
            Log.i(TAG, "Bytes uploaded successfully. URL: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading bytes to path '$path' with name '$fileName'", e)
            null
        }
    }

    /**
     * Download a file from Firebase Storage.
     *
     * @param path The path in Firebase Storage where the file is stored
     * @param fileName The name of the file
     * @param destinationFile The local file where the downloaded file should be saved
     * @return True if the download was successful, false otherwise
     */
    suspend fun downloadFile(path: String, fileName: String, destinationFile: File): Boolean {
        return try {
            val fullPath = if (path.endsWith("/")) "$path$fileName" else "$path/$fileName"
            val fileRef = storageRef.child(fullPath)
            Log.d(TAG, "Downloading file from: ${fileRef.path} to ${destinationFile.absolutePath}")
            fileRef.getFile(destinationFile).await()
            Log.i(TAG, "File downloaded successfully to ${destinationFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file from path '$path', name '$fileName'", e)
            false
        }
    }

    /**
     * Download a file from Firebase Storage as a byte array.
     *
     * @param path The path in Firebase Storage where the file is stored
     * @param fileName The name of the file
     * @param maxSize The maximum size of the file in bytes (default: 10MB)
     * @return The byte array of the downloaded file, or null if the download failed
     */
    suspend fun downloadBytes(path: String, fileName: String, maxSize: Long = 10 * 1024 * 1024): ByteArray? {
        return try {
            val fullPath = if (path.endsWith("/")) "$path$fileName" else "$path/$fileName"
            val fileRef = storageRef.child(fullPath)
            Log.d(TAG, "Downloading bytes from: ${fileRef.path}")
            val bytes = fileRef.getBytes(maxSize).await()
            Log.i(TAG, "Bytes downloaded successfully. Size: ${bytes?.size}")
            bytes
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading bytes from path '$path', name '$fileName'", e)
            null
        }
    }

    /**
     * Delete a file from Firebase Storage.
     *
     * @param path The path in Firebase Storage where the file is stored
     * @param fileName The name of the file
     * @return True if the deletion was successful, false otherwise
     */
    suspend fun deleteFile(path: String, fileName: String): Boolean {
        return try {
            val fullPath = if (path.endsWith("/")) "$path$fileName" else "$path/$fileName"
            val fileRef = storageRef.child(fullPath)
            Log.d(TAG, "Deleting file from: ${fileRef.path}")
            fileRef.delete().await()
            Log.i(TAG, "File deleted successfully: ${fileRef.path}")
            true
        } catch (e: Exception) {
            // Check if the error is "object not found" which can be treated as success for delete
            if (e is com.google.firebase.storage.StorageException &&
                e.errorCode == com.google.firebase.storage.StorageException.ERROR_OBJECT_NOT_FOUND) {

                //Log.w(TAG, "File not found for deletion (already deleted or never existed): ${fileRef}")
                true // Consider "not found" as a successful deletion scenario
            } else {
                Log.e(TAG, "Error deleting file from path '$path', name '$fileName'", e)
                false
            }
        }
    }

    /**
     * Get the download URL of a file in Firebase Storage.
     *
     * @param path The path in Firebase Storage where the file is stored
     * @param fileName The name of the file
     * @return The download URL of the file, or null if the file doesn't exist
     */
    suspend fun getDownloadUrl(path: String, fileName: String): String? {
        return try {
            val fullPath = if (path.endsWith("/")) "$path$fileName" else "$path/$fileName"
            val fileRef = storageRef.child(fullPath)
            Log.d(TAG, "Getting download URL for: ${fileRef.path}")
            val downloadUrl = fileRef.downloadUrl.await().toString()
            Log.i(TAG, "Download URL retrieved: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error getting download URL for path '$path', name '$fileName'", e)
            null
        }
    }

    /**
     * Check if a file exists in Firebase Storage.
     *
     * @param path The path in Firebase Storage where the file is stored
     * @param fileName The name of the file
     * @return True if the file exists, false otherwise
     */
    suspend fun fileExists(path: String, fileName: String): Boolean {
        return try {
            val fullPath = if (path.endsWith("/")) "$path$fileName" else "$path/$fileName"
            val fileRef = storageRef.child(fullPath)
            fileRef.metadata.await() // Getting metadata will fail if the file doesn't exist
            Log.d(TAG, "File exists at: ${fileRef.path}")
            true
        } catch (e: Exception) {
            Log.d(TAG, "File does not exist at path '$path', name '$fileName'. Error: ${e.message}")
            false
        }
    }

    /**
     * List all files in a directory in Firebase Storage.
     *
     * @param path The path in Firebase Storage to list
     * @return A list of file names in the directory, or an empty list if the directory doesn't exist
     */
    suspend fun listFiles(path: String): List<String> {
        return try {
            // Ensure path doesn't end with a slash for consistency if listing a "folder"
            val directoryPath = if (path.endsWith("/")) path.dropLast(1) else path
            val directoryRef = storageRef.child(directoryPath)
            Log.d(TAG, "Listing files in directory: ${directoryRef.path}")
            val result = directoryRef.listAll().await()
            val fileNames = result.items.map { it.name }
            Log.i(TAG, "Found ${fileNames.size} files in ${directoryRef.path}")
            fileNames
        } catch (e: Exception) {
            Log.e(TAG, "Error listing files in directory '$path'", e)
            emptyList()
        }
    }
}