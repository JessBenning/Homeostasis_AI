package com.homeostasis.app.data.remote

import com.google.firebase.Timestamp
import com.homeostasis.app.data.model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await

/**
 * Repository for app-wide settings operations.
 */
class SettingsRepository : FirebaseRepository<Settings>() {
    
    override val collectionName: String = Settings.COLLECTION
    
    /**
     * Get the app settings.
     */
    suspend fun getAppSettings(): Settings {
        return try {
            val settings = collection.document(Settings.DOCUMENT_ID).get().await()
                .toObject(Settings::class.java)
            
            settings ?: createDefaultSettings()
        } catch (e: Exception) {
            createDefaultSettings()
        }
    }
    
    /**
     * Get the app settings as a Flow.
     */
    fun getAppSettingsAsFlow(): Flow<Settings> = callbackFlow {
        val listenerRegistration = collection.document(Settings.DOCUMENT_ID)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.exists()) {
                    val settings = snapshot.toObject(Settings::class.java)
                    if (settings != null) {
                        trySend(settings)
                    }
                } else {
                    // If settings don't exist, create default settings
                    val defaultSettings = Settings()
                    trySend(defaultSettings)
                }
            }
        
        awaitClose { listenerRegistration.remove() }
    }
    
    /**
     * Create default settings.
     */
    private suspend fun createDefaultSettings(): Settings {
        val defaultSettings = Settings()
        set(Settings.DOCUMENT_ID, defaultSettings)
        return defaultSettings
    }
    
    /**
     * Update cloud sync settings.
     */
    suspend fun updateCloudSyncSettings(
        enabled: Boolean,
        syncFrequency: String
    ): Boolean {
        return try {
            val updates = mapOf(
                "cloudSync.enabled" to enabled,
                "cloudSync.syncFrequency" to syncFrequency,
                "cloudSync.lastSyncTime" to Timestamp.now()
            )
            
            collection.document(Settings.DOCUMENT_ID).update(updates).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Update score threshold settings.
     */
    suspend fun updateScoreThresholdSettings(
        value: Int,
        resetBehavior: String,
        preserveScoreDifference: Boolean
    ): Boolean {
        return try {
            val updates = mapOf(
                "scoreThreshold.value" to value,
                "scoreThreshold.resetBehavior" to resetBehavior,
                "scoreThreshold.preserveScoreDifference" to preserveScoreDifference
            )
            
            collection.document(Settings.DOCUMENT_ID).update(updates).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Record a reset event.
     */
    suspend fun recordResetEvent(userId: String): Boolean {
        return try {
            val settings = getAppSettings()
            
            val updates = mapOf(
                "resetHistory.lastResetTime" to Timestamp.now(),
                "resetHistory.resetBy" to userId,
                "resetHistory.resetCount" to (settings.resetHistory.resetCount + 1)
            )
            
            collection.document(Settings.DOCUMENT_ID).update(updates).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getModelClass(): Class<Settings> = Settings::class.java
}