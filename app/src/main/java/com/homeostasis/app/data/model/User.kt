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
    val id: String = "",           // Default: ""

    val name: String = "",           // Default: ""

    @PropertyName("profileImageUrl")
    val profileImageUrl: String = "", // Default: "" - Non-nullable

    @PropertyName("createdAt")
    val createdAt: Timestamp = Timestamp.now(), // Default: current time

    @PropertyName("lastActive")
    val lastActive: Timestamp = Timestamp.now(), // Default: current time

    @PropertyName("lastResetScore")
    val lastResetScore: Int = 0,     // Default: 0

    @PropertyName("resetCount")
    val resetCount: Int = 0,         // Default: 0

    @PropertyName("householdGroupId")
    val householdGroupId: String = "", // Default: "" - Non-nullable

    var needsSync: Boolean = false,  // Default: false

    var isDeletedLocally: Boolean = false, // Default: false

    @PropertyName("lastModifiedAt")
    val lastModifiedAt: Timestamp = Timestamp.now(), // Default: current time

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
        householdGroupId = "", // Firestore constructor also defaults householdGroupId to ""
        // needsSync and isDeletedLocally are NOT in the secondary constructor here.
        // This means when Firestore creates a User object using this constructor,
        // needsSync will be false and isDeletedLocally will be false (due to primary constructor defaults).
        // This is generally okay as these flags are primarily for local state.
        lastModifiedAt = Timestamp.now()
    )

    companion object {
        const val COLLECTION = "users"
    }
}