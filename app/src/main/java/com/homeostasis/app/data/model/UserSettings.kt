package com.homeostasis.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Data class representing user-specific settings in the Homeostasis app.
 */
data class UserSettings(
    @DocumentId
    val id: String = "", // This will be the user ID
    
    @PropertyName("userId")
    val userId: String = "",
    
    @PropertyName("notificationsEnabled")
    val notificationsEnabled: Boolean = true,
    
    @PropertyName("theme")
    val theme: String = "system", // "light", "dark", "system"
    
    @PropertyName("cloudBackup")
    val cloudBackup: CloudBackup = CloudBackup(),
    
    @PropertyName("lastModifiedAt")
    val lastModifiedAt: Timestamp = Timestamp.now()
) {
    // Empty constructor for Firestore
    constructor() : this(
        id = "",
        userId = "",
        notificationsEnabled = true,
        theme = "system",
        cloudBackup = CloudBackup(),
        lastModifiedAt = Timestamp.now()
    )
    
    /**
     * Cloud backup settings for the user.
     */
    data class CloudBackup(
        @PropertyName("enabled")
        val enabled: Boolean = true,
        
        @PropertyName("lastBackupTime")
        val lastBackupTime: Timestamp? = null,
        
        @PropertyName("autoBackup")
        val autoBackup: Boolean = true
    ) {
        // Empty constructor for Firestore
        constructor() : this(
            enabled = true,
            lastBackupTime = null,
            autoBackup = true
        )
    }
    
    companion object {
        const val COLLECTION = "userSettings"
    }
}