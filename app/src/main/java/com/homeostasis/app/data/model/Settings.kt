package com.homeostasis.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Data class representing application-wide settings in the Homeostasis app.
 */
data class Settings(
    @DocumentId
    val id: String = "appSettings", // Fixed ID for the settings document
    
    @PropertyName("cloudSync")
    val cloudSync: CloudSync = CloudSync(),
    
    @PropertyName("scoreThreshold")
    val scoreThreshold: ScoreThreshold = ScoreThreshold(),
    
    @PropertyName("resetHistory")
    val resetHistory: ResetHistory = ResetHistory()
) {
    // Empty constructor for Firestore
    constructor() : this(
        id = "appSettings",
        cloudSync = CloudSync(),
        scoreThreshold = ScoreThreshold(),
        resetHistory = ResetHistory()
    )
    
    /**
     * Cloud synchronization settings.
     */
    data class CloudSync(
        @PropertyName("enabled")
        val enabled: Boolean = true,
        
        @PropertyName("lastSyncTime")
        val lastSyncTime: Timestamp? = null,
        
        @PropertyName("syncFrequency")
        val syncFrequency: String = "hourly" // "hourly", "daily", "manual"
    ) {
        // Empty constructor for Firestore
        constructor() : this(
            enabled = true,
            lastSyncTime = null,
            syncFrequency = "hourly"
        )
    }
    
    /**
     * Score threshold settings for automatic resets.
     */
    data class ScoreThreshold(
        @PropertyName("value")
        val value: Int = 100,
        
        @PropertyName("resetBehavior")
        val resetBehavior: String = "relative", // "zero", "relative", "percentage"
        
        @PropertyName("preserveScoreDifference")
        val preserveScoreDifference: Boolean = true
    ) {
        // Empty constructor for Firestore
        constructor() : this(
            value = 100,
            resetBehavior = "relative",
            preserveScoreDifference = true
        )
    }
    
    /**
     * Reset history information.
     */
    data class ResetHistory(
        @PropertyName("lastResetTime")
        val lastResetTime: Timestamp? = null,
        
        @PropertyName("resetBy")
        val resetBy: String? = null,
        
        @PropertyName("resetCount")
        val resetCount: Int = 0
    ) {
        // Empty constructor for Firestore
        constructor() : this(
            lastResetTime = null,
            resetBy = null,
            resetCount = 0
        )
    }
    
    companion object {
        const val COLLECTION = "settings"
        const val DOCUMENT_ID = "appSettings"
    }
}