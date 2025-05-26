package com.homeostasis.app.data.remote

import com.google.firebase.Timestamp
import com.homeostasis.app.data.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await

/**
 * Repository for user-specific settings operations.
 */
class UserSettingsRepository : FirebaseRepository<UserSettings>() {
    
    override val collectionName: String = UserSettings.COLLECTION
    
    /**
     * Get settings for a specific user.
     */
    suspend fun getUserSettings(userId: String): UserSettings {
        return try {
            val userSettings = collection.document(userId).get().await()
                .toObject(UserSettings::class.java)
            
            userSettings ?: createDefaultUserSettings(userId)
        } catch (e: Exception) {
            createDefaultUserSettings(userId)
        }
    }
    
    /**
     * Get settings for a specific user as a Flow.
     */
    fun getUserSettingsAsFlow(userId: String): Flow<UserSettings> = callbackFlow {
        val listenerRegistration = collection.document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.exists()) {
                    val userSettings = snapshot.toObject(UserSettings::class.java)
                    if (userSettings != null) {
                        trySend(userSettings)
                    }
                } else {
                    // If user settings don't exist, create default settings
                    val defaultUserSettings = UserSettings(id = userId, userId = userId)
                    trySend(defaultUserSettings)
                }
            }
        
        awaitClose { listenerRegistration.remove() }
    }
    
    /**
     * Create default settings for a user.
     */
    private suspend fun createDefaultUserSettings(userId: String): UserSettings {
        val defaultUserSettings = UserSettings(id = userId, userId = userId)
        set(userId, defaultUserSettings)
        return defaultUserSettings
    }
    
    /**
     * Update notification settings.
     */
    suspend fun updateNotificationSettings(userId: String, enabled: Boolean): Boolean {
        return try {
            val updates = mapOf(
                "notificationsEnabled" to enabled,
                "lastModifiedAt" to Timestamp.now()
            )
            
            collection.document(userId).update(updates).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Update theme settings.
     */
    suspend fun updateThemeSettings(userId: String, theme: String): Boolean {
        return try {
            val updates = mapOf(
                "theme" to theme,
                "lastModifiedAt" to Timestamp.now()
            )
            
            collection.document(userId).update(updates).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Update cloud backup settings.
     */
    suspend fun updateCloudBackupSettings(
        userId: String,
        enabled: Boolean,
        autoBackup: Boolean
    ): Boolean {
        return try {
            val updates = mapOf(
                "cloudBackup.enabled" to enabled,
                "cloudBackup.autoBackup" to autoBackup,
                "lastModifiedAt" to Timestamp.now()
            )
            
            collection.document(userId).update(updates).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Record a backup event.
     */
    suspend fun recordBackupEvent(userId: String): Boolean {
        return try {
            val updates = mapOf(
                "cloudBackup.lastBackupTime" to Timestamp.now(),
                "lastModifiedAt" to Timestamp.now()
            )
            
            collection.document(userId).update(updates).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getModelClass(): Class<UserSettings> = UserSettings::class.java
}