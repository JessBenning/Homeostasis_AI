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
        val userProfilePicUrl: String?, // This will store the local file path
        val totalScore: Int
    ) : TaskHistoryFeedItem

    /**
     * Represents a single task history event in the feed.
     */
    data class TaskHistoryItem(
        val historyId: String,
        val taskTitle: String,
        val completedAt: Timestamp,
        val points: Int,
        val completedByUserName: String,
        val completedByUserProfilePicUrl: String? // This will store the local file path
    ) : TaskHistoryFeedItem

    // Optional: Add other item types if needed for this specific feed
    // data class DateSeparatorItem(val date: String) : TaskHistoryFeedItem
}