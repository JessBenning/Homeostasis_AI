package com.homeostasis.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Data class representing a task completion event.
 */
data class TaskCompletion(
    val completedAt: Timestamp = Timestamp.now(),
    val completedBy: String = "",
    val points: Int = 0
) {
    // Empty constructor for Firestore
    constructor() : this(
        completedAt = Timestamp.now(),
        completedBy = "",
        points = 0
    )
}

/**
 * Data class representing a task in the Homeostasis app.
 */
@Entity(tableName = "tasks")
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
    
    @PropertyName("createdAt")
    val createdAt: Timestamp = Timestamp.now(),
    
    @PropertyName("lastModifiedAt")
    val lastModifiedAt: Timestamp = Timestamp.now(),
    
    @PropertyName("isDeleted")
    val isDeleted: Boolean = false,
    
    @PropertyName("completionCount")
    val completionCount: Int = 0,
    
    @PropertyName("lastCompletedAt")
    val lastCompletedAt: Timestamp? = null,
    
    @PropertyName("lastCompletedBy")
    val lastCompletedBy: String = "",
    
    @PropertyName("completionHistory")
    val completionHistory: List<TaskCompletion> = listOf()
) {
    // Empty constructor for Firestore
    constructor() : this(
        id = "",
        title = "",
        description = "",
        points = 0,
        categoryId = "",
        createdBy = "",
        createdAt = Timestamp.now(),
        lastModifiedAt = Timestamp.now(),
        isDeleted = false,
        completionCount = 0,
        lastCompletedAt = null,
        lastCompletedBy = "",
        completionHistory = listOf()
    )
    
    /**
     * Creates a copy of this task with updated completion information.
     */
    fun complete(userId: String, completedAt: Timestamp = Timestamp.now()): Task {
        val newCompletion = TaskCompletion(
            completedAt = completedAt,
            completedBy = userId,
            points = points
        )
        
        val updatedHistory = completionHistory.toMutableList().apply {
            add(newCompletion)
        }
        
        return copy(
            completionCount = completionCount + 1,
            lastCompletedAt = completedAt,
            lastCompletedBy = userId,
            completionHistory = updatedHistory,
            lastModifiedAt = Timestamp.now()
        )
    }
    
    /**
     * Creates a copy of this task with the most recent completion undone.
     * Returns null if there are no completions to undo.
     */
    fun undoLastCompletion(): Task? {
        if (completionCount <= 0) return null
        
        val updatedHistory = completionHistory.toMutableList()
        if (updatedHistory.isNotEmpty()) {
            updatedHistory.removeAt(updatedHistory.size - 1)
        }
        
        val lastCompletion = updatedHistory.lastOrNull()
        
        return copy(
            completionCount = completionCount - 1,
            lastCompletedAt = lastCompletion?.completedAt,
            lastCompletedBy = lastCompletion?.completedBy ?: "",
            completionHistory = updatedHistory,
            lastModifiedAt = Timestamp.now()
        )
    }
    
    /**
     * Returns true if this task has been completed at least once.
     */
    fun isCompleted(): Boolean {
        return completionCount > 0
    }
    
    companion object {
        const val COLLECTION = "tasks"
    }
}