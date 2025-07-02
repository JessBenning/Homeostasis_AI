package com.homeostasis.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude // Import Exclude
import com.google.firebase.firestore.FieldValue // Import FieldValue
import java.util.UUID

@Entity(tableName = "groups")
data class Group(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "ownerId") val ownerId: String, // ID of the user who owns the group
    @ColumnInfo(name = "createdAt") val createdAt: Timestamp = Timestamp.now(),
    @ColumnInfo(name = "lastModifiedAt") val lastModifiedAt: Timestamp = Timestamp.now(), // For local Room entity

    // needsSync is purely local, should not go to Firestore via toFirestoreMap
    // Use @get:Exclude if you want Firestore to completely ignore it during automatic deserialization.
    // However, for toFirestoreMap, we simply won't include it.
    // Making it a var if LocalToRemoteSyncHandler needs to modify it after fetching from DB.
    @ColumnInfo(name = "needsSync") @get:Exclude var needsSync: Boolean = false // Flag for local-to-remote sync
) {
    // No-argument constructor required by Firestore and Room (if all args have defaults)
    constructor() : this(
        id = "",
        name = "",
        ownerId = "",
        createdAt = Timestamp.now(),
        lastModifiedAt = Timestamp.now(),
        needsSync = false
    )

    companion object {
        const val COLLECTION = "groups" // Firestore collection name
    }
}
    // --- Helper to convert Group to Map for Firestore ---
// Place this inside GroupRepository or make it an extension function on your Group model
// Ensure this aligns with your Group data class fields.
    internal fun Group.toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "name" to name,
            "ownerId" to ownerId,
            "createdAt" to createdAt, // This should be the original client-side Timestamp
            "lastModifiedAt" to FieldValue.serverTimestamp(), // Firestore will set this
            // DO NOT include 'needsSync' or 'isDeletedLocally' (or similar local-only fields)
            //"isDeleted" to isDeleted // if you have a soft delete flag for Firestore
        ).filterValues { it != null }
    }



