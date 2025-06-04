package com.homeostasis.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import java.util.Date

/**
 * Data class representing a task completion history entry in the Homeostasis app.
 */
@Entity(tableName = "task_history")
data class TaskHistory(
    @PrimaryKey
    @DocumentId
    val id: String = "",
    
    @PropertyName("taskId")
    val taskId: String = "",
    
    @PropertyName("userId")
    val userId: String = "",
    
    @PropertyName("completedAt")
    val completedAt: Timestamp = Timestamp.now(),
    
    @PropertyName("pointValue")
    val pointValue: Int = 0,
    
    @PropertyName("customCompletionDate")
    val customCompletionDate: Timestamp? = null,
    
    @PropertyName("isDeleted")
    val isDeleted: Boolean = false,
    
    @PropertyName("isArchived")
    val isArchived: Boolean = false,
    
    @PropertyName("archivedInResetId")
    val archivedInResetId: String? = null,
    
    @PropertyName("lastModifiedAt")
    val lastModifiedAt: Timestamp = Timestamp.now(),

    val needsSync: Boolean = false,

    ) {
    // Empty constructor for Firestore
    constructor() : this(
        id = "",
        taskId = "",
        userId = "",
        completedAt = Timestamp.now(),
        pointValue = 0,
        customCompletionDate = Timestamp.now(),
        isDeleted = false,
        isArchived = false,
        archivedInResetId = null,
        needsSync = false,
        lastModifiedAt = Timestamp.now()
    )
    
    companion object {
        const val COLLECTION = "taskHistory"
    }
}