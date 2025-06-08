package com.homeostasis.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Data class representing a user in the Homeostasis app.
 */
@Entity(tableName = "user")
data class User(
    @DocumentId
    @PrimaryKey
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
    val resetCount: Int = 0,
    
    @PropertyName("householdGroupId")
    val householdGroupId: String = ""
) {
    // Empty constructor for Firestore
    constructor() : this(
        id = "",
        name = "",
        profileImageUrl = "",
        createdAt = Timestamp.now(),
        lastActive = Timestamp.now(),
        lastResetScore = 0,
        resetCount = 0,
        householdGroupId = ""
    )
    
    companion object {
        const val COLLECTION = "users"
    }
}