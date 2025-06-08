package com.homeostasis.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName



/**
 * Data class representing a task in the Homeostasis app.
 */
@Entity(tableName = Task.COLLECTION)
data class Task(
    @PrimaryKey
    @DocumentId
    val id: String = "",

    val title: String = "",

    val description: String = "",

    val points: Int = 0,

    @PropertyName("categoryId")
    val categoryId: String = "",

    @PropertyName("createdBy")
    val createdBy: String = "",

    @PropertyName("isDeleted")
    val isDeleted: Boolean = false,

    @PropertyName("createdAt")
    val createdAt: Timestamp = Timestamp.now(),

    @PropertyName("lastModifiedAt")
    val lastModifiedAt: Timestamp = Timestamp.now(),

    var needsSync: Boolean = false,         // Default to false, set to true when local changes occur

    var isDeletedLocally: Boolean = false,

    @PropertyName("isCompleted") // Good practice for Firestore
    val isCompleted: Boolean = false,
    @PropertyName("householdGroupId")
    val householdGroupId: String = "",
) {
    // Empty constructor for Firestore
    constructor() : this(
        id = "",
        title = "",
        description = "",
        points = 0,
        categoryId = "",
        createdBy = "",
        isDeleted = false,
        createdAt = Timestamp.now(),
        lastModifiedAt = Timestamp.now(),
        needsSync = false,
        householdGroupId = ""
    )

    companion object {
        const val COLLECTION = "tasks"
    }
}

