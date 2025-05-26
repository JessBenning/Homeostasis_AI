package com.homeostasis.app.data.remote

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

/**
 * Repository for Firebase Storage operations.
 */
class FirebaseStorageRepository {
    
    private val storage: FirebaseStorage by lazy {
        FirebaseStorage.getInstance()
    }
    
    private val storageRef: StorageReference by lazy {
        storage.reference
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
            val fileRef = storageRef.child("$path/$name")
            
            val uploadTask = fileRef.putFile(uri).await()
            fileRef.downloadUrl.await().toString()
        } catch (e: Exception) {
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
        return try {
            val name = fileName ?: UUID.randomUUID().toString()
            val fileRef = storageRef.child("$path/$name")
            
            val uploadTask = fileRef.putFile(Uri.fromFile(file)).await()
            fileRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            null
        }
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
            val fileRef = storageRef.child("$path/$name")
            
            val metadata = contentType?.let {
                com.google.firebase.storage.StorageMetadata.Builder()
                    .setContentType(it)
                    .build()
            }
            
            val uploadTask = if (metadata != null) {
                fileRef.putBytes(bytes, metadata).await()
            } else {
                fileRef.putBytes(bytes).await()
            }
            
            fileRef.downloadUrl.await().toString()
        } catch (e: Exception) {
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
            val fileRef = storageRef.child("$path/$fileName")
            fileRef.getFile(destinationFile).await()
            true
        } catch (e: Exception) {
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
            val fileRef = storageRef.child("$path/$fileName")
            fileRef.getBytes(maxSize).await()
        } catch (e: Exception) {
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
            val fileRef = storageRef.child("$path/$fileName")
            fileRef.delete().await()
            true
        } catch (e: Exception) {
            false
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
            val fileRef = storageRef.child("$path/$fileName")
            fileRef.downloadUrl.await().toString()
        } catch (e: Exception) {
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
            val fileRef = storageRef.child("$path/$fileName")
            fileRef.metadata.await()
            true
        } catch (e: Exception) {
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
            val directoryRef = storageRef.child(path)
            val result = directoryRef.listAll().await()
            result.items.map { it.name }
        } catch (e: Exception) {
            emptyList()
        }
    }
}