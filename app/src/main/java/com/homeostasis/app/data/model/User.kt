package com.homeostasis.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.PropertyName // Keep if used, though @ServerTimestamp might be preferred for some

/**
 * Data class representing a user in the Homeostasis app.
 */
@Entity(tableName = "user") // Changed from "users" to "user" to match your @Entity annotation
data class User(
    @DocumentId
    @PrimaryKey // This 'id' is your document ID from Firestore and primary key for Room
    val id: String = "",

    val name: String = "", // Assuming this can be empty but not null

    @get:PropertyName("profileImageUrl") // Use @get: for Firestore mapping if it's a val
    @set:PropertyName("profileImageUrl") // Use @set: if it's a var and you want Firestore to set it
    var profileImageUrl: String = "",    // Changed to var to allow updates, non-nullable

    @get:PropertyName("profileImageHashSignature")
    @set:PropertyName("profileImageHashSignature")
    var profileImageHashSignature: String? = null, // Can be null if no image or not synced

    // Timestamps
    // For createdAt, if it's set once and never changes, val is fine.
    // Defaulting to Timestamp.now() is for initial local object creation.
    // Firestore @ServerTimestamp is better for server-side truth.
    @get:PropertyName("createdAt")
    val createdAt: Timestamp = Timestamp.now(),

    @get:PropertyName("lastModifiedAt") // This will be updated by Firestore with server timestamp
    var lastModifiedAt: Timestamp? = null,  // VAR to allow updates from Firestore, Nullable initially

    @get:PropertyName("lastActive")
    var lastActive: Timestamp = Timestamp.now(), // VAR if this is updated frequently

    // Other app-specific fields
    @get:PropertyName("lastResetScore")
    val lastResetScore: Int = 0,

    @get:PropertyName("resetCount")
    val resetCount: Int = 0,

    @get:PropertyName("householdGroupId")
    @set:PropertyName("householdGroupId")
    var householdGroupId: String = "", // VAR to allow updates

    // --- Local State Flags (Not typically synced to Firestore directly) ---
    // These should NOT have @PropertyName if you don't want them in Firestore documents
    // If they are only for Room, they don't need Firestore annotations.
    // @get:Exclude ensures Firestore getter/setter generation ignores it.

    @get:Exclude // Correct: Excludes from Firestore serialization
    var needsSync: Boolean = false,  // Local flag for Room

    @get:Exclude // Correct: Excludes from Firestore serialization
    var isDeletedLocally: Boolean = false, // Local flag for Room

    // NEW Local flag for Room, also excluded from Firestore
    @get:Exclude
    var needsProfileImageUpload: Boolean = false // Default to false

) {
    // Empty constructor for Firestore deserialization
    constructor() : this(
        id = "",
        name = "",
        profileImageUrl = "",
        profileImageHashSignature = null,
        createdAt = Timestamp.now(), // Default for constructor
        lastModifiedAt = null,       // Default for constructor
        lastActive = Timestamp.now(),  // Default for constructor
        lastResetScore = 0,
        resetCount = 0,
        householdGroupId = "",
        needsSync = false,             // Ensure defaults here if primary constructor has them
        isDeletedLocally = false,
        needsProfileImageUpload = false
    )

    companion object {
        const val COLLECTION = "user" // Matching your @Entity tableName

        // Field name constants for Firestore (good practice)
        const val FIELD_ID = "id" // Though often DocumentId handles this
        const val FIELD_NAME = "name"
        const val FIELD_PROFILE_IMAGE_URL = "profileImageUrl"
        const val FIELD_PROFILE_IMAGE_HASH_SIGNATURE = "profileImageHashSignature"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_LAST_MODIFIED_AT = "lastModifiedAt"
        const val FIELD_LAST_ACTIVE = "lastActive"
        const val FIELD_LAST_RESET_SCORE = "lastResetScore"
        const val FIELD_RESET_COUNT = "resetCount"
        const val FIELD_HOUSEHOLD_GROUP_ID = "householdGroupId"
        // No constants for needsSync, isDeletedLocally, needsProfileImageUpload as they are @Exclude'd
    }
}

/**
 * Extension function to convert a User object to a Map suitable for Firestore.
 * Local-only flags like 'needsSync', 'isDeletedLocally', 'needsProfileImageUpload' are excluded.
 * The 'id' is also excluded as it's typically used as the document ID in Firestore.
 */
fun User.toFirestoreMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>(
        User.FIELD_NAME to name,
        User.FIELD_PROFILE_IMAGE_URL to profileImageUrl, // Sync current URL
        // User.FIELD_CREATED_AT to createdAt, // Usually set once by server or client, then not changed
        // If you want to ensure it's set on create:
        // if (isNewUser) map[User.FIELD_CREATED_AT] = FieldValue.serverTimestamp()
        // else map[User.FIELD_CREATED_AT] = createdAt (to preserve existing)
        // For simplicity, if createdAt is client-set on create and then fixed:
        User.FIELD_CREATED_AT to createdAt,


        User.FIELD_LAST_ACTIVE to lastActive, // Or FieldValue.serverTimestamp() if lastActive is "now" on server
        User.FIELD_LAST_RESET_SCORE to lastResetScore,
        User.FIELD_RESET_COUNT to resetCount,
        User.FIELD_HOUSEHOLD_GROUP_ID to householdGroupId,
        User.FIELD_PROFILE_IMAGE_HASH_SIGNATURE to profileImageHashSignature, // Sync the hash

        // IMPORTANT: For Firestore, use FieldValue.serverTimestamp() for lastModifiedAt
        User.FIELD_LAST_MODIFIED_AT to FieldValue.serverTimestamp()
    )

    // Firestore generally handles nulls by not writing the field.
    // If a field should explicitly be removed if null (e.g. profileImageHashSignature),
    // you might need more specific logic or ensure your @Exclude and nullability are correct.
    // .filterValues { it != null || it is FieldValue } // This is generally a safe approach

    return map.filterValues { value -> // More robust filtering
        value != null || value is FieldValue // Keep actual values and server timestamp placeholders
    }
}