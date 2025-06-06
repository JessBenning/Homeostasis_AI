package com.homeostasis.app.ui.task_history

import android.util.Log // For logging
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeostasis.app.data.TaskDao
import com.homeostasis.app.data.TaskHistoryDao
import com.homeostasis.app.data.UserDao
import com.homeostasis.app.data.model.TaskHistory
import com.homeostasis.app.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class TaskHistoryViewModel @Inject constructor(
    private val taskHistoryDao: TaskHistoryDao,
    private val taskDao: TaskDao,
    private val userDao: UserDao
    // ... any other dependencies
) : ViewModel() {

    // For task history items
    private val _taskHistoryFeedItems = MutableStateFlow<List<TaskHistoryFeedItem>>(emptyList()) // Assuming you now emit TaskHistoryFeedItem
    val taskHistoryItems: StateFlow<List<TaskHistoryFeedItem>> = _taskHistoryFeedItems.asStateFlow()

    // For loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // For error messages
    private val _error = MutableStateFlow<String?>(null) // String? to allow for no error
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        Log.d("TaskHistoryVM", "ViewModel initialized by Hilt")
        loadTaskHistory() // This function should now update _isLoading and _error
    }

    private fun loadTaskHistory() {
        Log.d("TaskHistoryVM", "loadTaskHistoryData() called")
        viewModelScope.launch {
            _isLoading.value = true // Set loading to true
            _error.value = null    // Clear previous errors
            try {
                // Your existing logic to fetch and transform data
                // For example:
                taskHistoryDao.getAllTaskHistoryFlow()
                    .map { historyList ->
                        // Transform List<TaskHistory> to List<TaskHistoryFeedItem>
                        // This is just a placeholder for your actual transformation logic
                        historyList.map { history ->
                            val user = userDao.getUserById(history.userId) // Example: Fetch user
                            val task = taskDao.getTaskById(history.taskId) // Example: Fetch task
                            TaskHistoryFeedItem.TaskHistoryItem(
                                historyId = history.id,

                                taskTitle = task?.title ?: "Unknown Task",
                                points= history.pointValue,
                                completedByUserName = user?.name ?: "Unknown User",
                                completedAt = history.completedAt,
                                completedByUserProfilePicUrl = user?.profileImageUrl ?: ""

                            )
                            // You'll also need to handle UserScoreSummaryItem creation and merging
                        }
                        // You might need more complex logic to combine TaskHistoryLogItems and UserScoreSummaryItems
                    }
                    .catch { exception ->
                        Log.e("TaskHistoryVM", "Error collecting task history from DAO", exception)
                        _error.value = "Failed to load history: ${exception.message}"
                        _taskHistoryFeedItems.value = emptyList()
                    }
                    .collect { feedItemsList ->
                        Log.d("TaskHistoryVM", "Collected ${feedItemsList.size} feed items from DB")
                        _taskHistoryFeedItems.value = feedItemsList
                    }
            } catch (e: Exception) {
                Log.e("TaskHistoryVM", "Exception in loadTaskHistoryData coroutine: ${e.message}", e)
                _error.value = "An unexpected error occurred: ${e.message}"
                _taskHistoryFeedItems.value = emptyList()
            } finally {
                _isLoading.value = false // Set loading to false when done
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

//    // Make sure formatTimestamp is accessible or defined here if needed
//    private fun formatTimestamp(timestamp: com.google.firebase.Timestamp): String {
//        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy hh:mm a", java.util.Locale.getDefault())
//        return sdf.format(timestamp.toDate())
//    }
}

//val taskHistoryItems: StateFlow<List<TaskHistoryFeedItem>> =
//    _taskHistory.map { historyList ->
//        historyList.map { taskHistory ->
//            // Transformation logic here
//            TaskHistoryFeedItem(
//                // Map fields from taskHistory to TaskHistoryFeedItem
//                // Example:
//                id = taskHistory.id,
//                description = "Task: ${taskHistory.taskName}", // Or whatever your TaskHistoryFeedItem needs
//                timestamp = taskHistory.completedAt, // Or similar
//                // ... other fields
//            )
//        }
//    }.stateIn(
//        scope = viewModelScope,
//        started = SharingStarted.WhileSubscribed(5000),
//        initialValue = emptyList()
//    )


//    private fun loadTaskHistory() {
//        Log.d("TaskHistoryVM", "loadTaskHistoryData() called")
//        viewModelScope.launch {
//            try {
//                // Use the Flow-based DAO method for reactive updates
//                taskHistoryDao.getAllTaskHistoryFlow()
//                    .catch { exception ->
//                        Log.e("TaskHistoryVM", "Error collecting task history from DAO", exception)
//                        _taskHistoryItems.value = emptyList() // Emit empty list on error
//                    }
//                    .collect { historyList: List<TaskHistory> ->
//                        Log.d("TaskHistoryVM", "Collected ${historyList.size} TaskHistory items from DB")
//                        _taskHistoryItems.value = historyList // Update the StateFlow
//
//                        // If you were transforming to DisplayableTaskHistoryItem, you'd do it here or in a .map before collect
//                        // For example:
//                        // val displayableList = historyList.map { transformToDisplayable(it) }
//                        // _displayableItems.value = displayableList
//                    }
//            } catch (e: Exception) {
//                Log.e("TaskHistoryVM", "Exception in loadTaskHistoryData coroutine: ${e.message}", e)
//                _taskHistoryItems.value = emptyList() // Ensure UI gets an empty list on unexpected error
//            }
//        }
//    }

//    private fun loadTaskHistory() {
//        Log.d("TaskHistoryVM", "loadTaskHistory() called")
//        viewModelScope.launch {
//            taskHistoryDao.getAllTaskHistoryFlow()
//                .map { historyList ->
//                    historyList.mapNotNull { historyEntry ->
//                        val task = taskDao.getTaskById(historyEntry.taskId)
//                        // Fetch the user
//                        // Assumes historyEntry.userId is the ID of the user who completed it.
//                        // And that User model has a 'name' or 'displayName' field.
//                        val user: User? = if (historyEntry.userId != null) {
//                            // Use firstOrNull() to get a single User object or null
//                            userDao.getUserById(historyEntry.userId).firstOrNull()
//                        } else {
//                            null
//                        }
//
//                        if (task != null) {
//                            DisplayableTaskHistoryItem(
//                                historyId = historyEntry.id,
//                                taskTitle = task.title,
//                                completedAt = historyEntry.completedAt,
//                                userName = user?.name ?: "Unknown User" // Adjust field based on your User model
//                            )
//                        } else {
//                            // Log if a task is not found for a history entry, can be helpful
//                            Log.w("TaskHistoryVM", "Task not found for history ID: ${historyEntry.id}, Task ID: ${historyEntry.taskId}")
//                            null
//                        }
//                    }
//                }
//                .catch { exception ->
//                    Log.e("TaskHistoryVM", "Error loading task history", exception)
//                    emit(emptyList<DisplayableTaskHistoryItem>())
//                }
//                .collect { displayableItems ->
//                    _taskHistoryItems.value = displayableItems
//                }
//        }
//    }
//}
