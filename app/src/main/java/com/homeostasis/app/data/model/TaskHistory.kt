package com.homeostasis.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import java.util.Date

/**
 * Data class representing a task completion history entry in the Homeostasis app.
 */
data class TaskHistory(
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
    val customCompletionDate: Date? = null,
    
    @PropertyName("isDeleted")
    val isDeleted: Boolean = false,
    
    @PropertyName("isArchived")
    val isArchived: Boolean = false,
    
    @PropertyName("archivedInResetId")
    val archivedInResetId: String? = null,
    
    @PropertyName("lastModifiedAt")
    val lastModifiedAt: Timestamp = Timestamp.now()
) {
    // Empty constructor for Firestore
    constructor() : this(
        id = "",
        taskId = "",
        userId = "",
        completedAt = Timestamp.now(),
        pointValue = 0,
        customCompletionDate = null,
        isDeleted = false,
        isArchived = false,
        archivedInResetId = null,
        lastModifiedAt = Timestamp.now()
    )
    
    companion object {
        const val COLLECTION = "taskHistory"
    }
}