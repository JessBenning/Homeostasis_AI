package com.homeostasis.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.google.firebase.Timestamp
import java.util.UUID

@Entity(tableName = "groups")
data class Group(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "ownerId") val ownerId: String, // ID of the user who owns the group
    @ColumnInfo(name = "createdAt") val createdAt: Timestamp = Timestamp.now(),
    @ColumnInfo(name = "lastModifiedAt") val lastModifiedAt: Timestamp = Timestamp.now(),
    @ColumnInfo(name = "needsSync") val needsSync: Boolean = false // Flag for local-to-remote sync
) {
    // No-argument constructor required by Firestore
    constructor() : this(
        id = "", // Default value for id
        name = "", // Default value for name
        ownerId = "", // Default value for ownerId
        createdAt = Timestamp.now(), // Default value for createdAt
        lastModifiedAt = Timestamp.now(), // Default value for lastModifiedAt
        needsSync = false // Default value for needsSync
    )

    companion object {
        const val COLLECTION = "groups" // Firestore collection name
    }
}