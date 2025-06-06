package com.homeostasis.app.ui.task_history

import android.util.Log // For logging
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeostasis.app.data.TaskDao
import com.homeostasis.app.data.TaskHistoryDao
import com.homeostasis.app.data.UserDao
import com.homeostasis.app.data.model.TaskHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart

@HiltViewModel
class TaskHistoryViewModel @Inject constructor(
    private val taskHistoryDao: TaskHistoryDao,
    private val taskDao: TaskDao,
    private val userDao: UserDao
    // ... any other dependencies
) : ViewModel() {

    // For task history items
    private val _feedItems = MutableStateFlow<List<TaskHistoryFeedItem>>(emptyList())
    val feedItems: StateFlow<List<TaskHistoryFeedItem>> = _feedItems.asStateFlow()

    // For loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // For error messages
    private val _error = MutableStateFlow<String?>(null) // String? to allow for no error
    val error: StateFlow<String?> = _error.asStateFlow()



    init {
        Log.d("TaskHistoryVM", "ViewModel initialized by Hilt")
        loadCombinedFeed() // Renamed to reflect it loads scores and history
    }

    private fun loadCombinedFeed() {
        Log.d("TaskHistoryVM", "loadCombinedFeed() called")
        viewModelScope.launch {
            taskHistoryDao.getAllTaskHistoryFlow() // Emits List<TaskHistory> (raw data)
                .onStart {
                    _isLoading.value = true
                    _error.value = null
                    _feedItems.value = emptyList() // Clear previous items
                    Log.d("TaskHistoryVM", "Flow.onStart: Loading started.")
                }
                .map { rawHistoryList -> // rawHistoryList is List<TaskHistory>
                    Log.i("TaskHistoryVM", "Map 1: Received ${rawHistoryList.size} raw history entries.")

                    // Step 1: Calculate User Scores and transform them into UserScoreSummaryItem
                    val userScoreSummaryItems = calculateAndFormatUserScores(rawHistoryList, userDao)
                    Log.d("TaskHistoryVM", "Calculated ${userScoreSummaryItems.size} UserScoreSummaryItems.")

                    // Step 2: Transform raw history entries into TaskHistoryItem (log items)
                    val taskHistoryLogItems = rawHistoryList.map { history ->
                        val user = userDao.getUserById(history.userId) // Suspend
                        val task = taskDao.getTaskById(history.taskId) // Suspend
                        TaskHistoryFeedItem.TaskHistoryItem(
                            historyId = history.id,
                            taskTitle = task?.title ?: "Unknown Task",
                            points = history.pointValue,
                            completedByUserName = user?.name ?: "Unknown User",
                            completedAt = history.completedAt,
                            completedByUserProfilePicUrl = user?.profileImageUrl ?: ""
                        )
                    }.sortedByDescending { it.completedAt } // Sort log items by completion time

                    Log.d("TaskHistoryVM", "Mapped to ${taskHistoryLogItems.size} TaskHistoryLogItems.")

                    // Step 3: Combine them. User scores usually go on top.
                    val combinedList: List<TaskHistoryFeedItem> = userScoreSummaryItems + taskHistoryLogItems
                    Log.d("TaskHistoryVM", "Combined list has ${combinedList.size} items.")
                    combinedList
                }
                .catch { exception ->
                    Log.e("TaskHistoryVM", "CATCH (Flow): Error in Flow chain", exception)
                    _error.value = "Failed to load feed: ${exception.message}"
                    _feedItems.value = emptyList() // Clear items on error
                    _isLoading.value = false
                }
                .collect { combinedFeedList ->
                    Log.i("TaskHistoryVM", "COLLECT: Collected ${combinedFeedList.size} combined feed items.")
                    _feedItems.value = combinedFeedList

                    if (_isLoading.value) {
                        _isLoading.value = false
                        Log.i("TaskHistoryVM", "COLLECT: Loading finished.")
                    }
                }
        }
    }

    // Helper function to calculate scores and transform them to UserScoreSummaryItem
    private suspend fun calculateAndFormatUserScores(
        historyItems: List<TaskHistory>,
        userDao: UserDao
    ): List<TaskHistoryFeedItem.UserScoreSummaryItem> {
        if (historyItems.isEmpty()) {
            Log.d("TaskHistoryVM", "calculateAndFormatUserScores: No history items.")
            return emptyList()
        }

        val userPoints = mutableMapOf<String, Int>() // UserId to Points
        // Optional: Track last activity timestamp for each user if needed for UserScoreSummaryItem
        val userLastActivity = mutableMapOf<String, Long>() // UserId to Timestamp

        for (history in historyItems) {
            userPoints[history.userId] = (userPoints[history.userId] ?: 0) + history.pointValue
            val currentLastActivity = userLastActivity[history.userId] ?: 0L
            if (history.completedAt.seconds > currentLastActivity) { // Assuming completedAt is a Firebase Timestamp
                userLastActivity[history.userId] = history.completedAt.seconds
            }
        }

        val scoreSummaryList = mutableListOf<TaskHistoryFeedItem.UserScoreSummaryItem>()
        for ((userId, points) in userPoints) {
            val user = userDao.getUserById(userId) // Suspend function
            scoreSummaryList.add(
                TaskHistoryFeedItem.UserScoreSummaryItem(
                    userId = userId,
                    userName = user?.name ?: "User $userId",
                    userProfilePicUrl = user?.profileImageUrl,
                    totalScore = points
                )
            )
        }

        // Sort users by total score, descending
        return scoreSummaryList.sortedByDescending { it.totalScore }
    }
    fun clearError() {
        _error.value = null
    }

}
