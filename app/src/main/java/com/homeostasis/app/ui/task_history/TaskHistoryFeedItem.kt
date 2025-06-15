// File: TaskHistoryFeedItem.kt
package com.homeostasis.app.ui.task_history // Or your chosen package

import com.google.firebase.Timestamp

sealed interface TaskHistoryFeedItem {
    /**
     * Represents a summary of a user's score.
     */
    data class UserScoreSummaryItem(
        val userId: String,
        val userName: String,
        val userProfilePicLocalPath: String?, // This will store the local file path
        val totalScore: Int,
        val userProfilePicSignature: String // New: To be used as Glide signature (e.g., lastModifiedAt.toEpochMilli().toString())
    ) : TaskHistoryFeedItem

    /**
     * Represents a single task history event in the feed.
     */
    data class TaskHistoryItem(
        val historyId: String,
        val taskTitle: String,
        val completedAt: Timestamp,
        val points: Int,
        val completedByUserId: String, // It's good to have the ID
        val completedByUserName: String,
        val completedByUserProfilePicLocalPath: String?, // This will store the local file path
        val completedByUserProfilePicSignature: String // New: Signature for the completer's profile pic
    ) : TaskHistoryFeedItem
}