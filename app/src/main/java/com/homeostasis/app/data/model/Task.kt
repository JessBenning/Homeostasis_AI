package com.homeostasis.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.Exclude // Import Exclude

/**
 * Data class representing a task in the Homeostasis app.
 */
@Entity(tableName = Task.COLLECTION)
data class Task(
    @PrimaryKey
    @DocumentId
    var id: String = "", // Typically val is fine if ID is immutable after creation, but var is safer for Firestore if it ever needs to set it.

    var title: String = "",

    var description: String = "", // Keep as var if editable

    var points: Int = 0, // Keep as var if editable

    @get:PropertyName("categoryId") @set:PropertyName("categoryId") // Explicit for clarity with var
    var categoryId: String = "",

    @get:PropertyName("ownerId") @set:PropertyName("ownerId") // Add ownerId field
    var ownerId: String = "",

    @get:PropertyName("isDeleted") @set:PropertyName("isDeleted")
    var isDeleted: Boolean = false, // CHANGED TO VAR

    @get:PropertyName("createdAt") @set:PropertyName("createdAt")
    var createdAt: Timestamp? = Timestamp.now(), // CHANGED TO VAR and Nullable

    @get:PropertyName("lastModifiedAt") @set:PropertyName("lastModifiedAt")
    var lastModifiedAt: Timestamp? = Timestamp.now(), // CHANGED TO VAR and Nullable

    @get:PropertyName("isCompleted") @set:PropertyName("isCompleted")
    var isCompleted: Boolean = false, // CHANGED TO VAR

    @get:PropertyName("householdGroupId") @set:PropertyName("householdGroupId")
    var householdGroupId: String = "",

    // --- Local-only fields ---
    // These should NOT be persisted to Firestore.
    // Use @Exclude for Firestore, and potentially @Ignore for Room if not stored.
    // If they are part of the primary constructor and you use a no-arg constructor for Firestore,
    // they might get default values written if not excluded.
    @get:Exclude @set:Exclude // Exclude from Firestore
    var needsSync: Boolean = false,

    @get:Exclude @set:Exclude // Exclude from Firestore
    var isDeletedLocally: Boolean = false
) {
    // Empty constructor for Firestore.
    // Firestore will use this and then set properties using setters (if they are var) or direct field access.
    constructor() : this(
        id = "",
        title = "",
        description = "", //can be removed
        points = 0,
        categoryId = "",
        ownerId = "", // Add ownerId to secondary constructor
        isDeleted = false,
        createdAt = Timestamp.now(), // Default for new local object
        lastModifiedAt = Timestamp.now(), // Default for new local object
        isCompleted = false, //can be removed
        householdGroupId = "",
        needsSync = false,         // Default for local-only field
        isDeletedLocally = false   // Default for local-only field
    )

    companion object {
        const val COLLECTION = "tasks"
    }
}