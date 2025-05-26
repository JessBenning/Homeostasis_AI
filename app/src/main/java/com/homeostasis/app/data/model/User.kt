package com.homeostasis.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Data class representing a user in the Homeostasis app.
 */
data class User(
    @DocumentId
    val id: String = "",
    
    val name: String = "",
    
    @PropertyName("profileImageUrl")
    val profileImageUrl: String = "",
    
    @PropertyName("createdAt")
    val createdAt: Timestamp = Timestamp.now(),
    
    @PropertyName("lastActive")
    val lastActive: Timestamp = Timestamp.now(),
    
    @PropertyName("lastResetScore")
    val lastResetScore: Int = 0,
    
    @PropertyName("resetCount")
    val resetCount: Int = 0
) {
    // Empty constructor for Firestore
    constructor() : this(
        id = "",
        name = "",
        profileImageUrl = "",
        createdAt = Timestamp.now(),
        lastActive = Timestamp.now(),
        lastResetScore = 0,
        resetCount = 0
    )
    
    companion object {
        const val COLLECTION = "users"
    }
}