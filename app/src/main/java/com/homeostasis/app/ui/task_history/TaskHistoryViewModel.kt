package com.homeostasis.app.ui.task_history

import android.util.Log // For logging
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
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
import com.homeostasis.app.data.HouseholdGroupIdProvider // Import HouseholdGroupIdProvider
import kotlinx.coroutines.flow.first // Import first
import java.io.File

@HiltViewModel
class TaskHistoryViewModel @Inject constructor(
    private val taskHistoryDao: TaskHistoryDao,
    private val taskDao: TaskDao,
    private val userDao: UserDao,
    private val householdGroupIdProvider: HouseholdGroupIdProvider, // Inject HouseholdGroupIdProvider
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context // Inject ApplicationContext
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
            householdGroupIdProvider.getHouseholdGroupId()
                .flatMapLatest { householdGroupId ->
                    householdGroupId?.let { id ->
                        // Get Flows for both task history and users
                        val taskHistoryFlow = taskHistoryDao.getAllTaskHistoryFlow(id)
                        val usersFlow = userDao.getAllUsersFlow(id) // Use the new Flow

                        // Combine the two flows
                        combine(taskHistoryFlow, usersFlow) { rawHistoryList, usersList ->
                            Log.i("TaskHistoryVM", "Combine: Received ${rawHistoryList.size} history entries and ${usersList.size} users.")

                            // Create a map of users for easy lookup by ID
                            val usersMap = usersList.associateBy { it.id }

                            // Step 1: Calculate User Scores and transform them into UserScoreSummaryItem
                            val userScoreSummaryItems = calculateAndFormatUserScores(rawHistoryList, usersMap) // Pass usersMap
                            Log.d("TaskHistoryVM", "Calculated ${userScoreSummaryItems.size} UserScoreSummaryItems.")

                            // Step 2: Transform raw history entries into TaskHistoryItem (log items)
                            val taskHistoryLogItems = rawHistoryList.map { history ->
                                val user = usersMap[history.userId] // Get user from the map
                                val task = taskDao.getTaskById(history.taskId, id) // Still need to fetch task individually

                                TaskHistoryFeedItem.TaskHistoryItem(
                                    historyId = history.id,
                                    taskTitle = task?.title ?: "Unknown Task",
                                    points = history.pointValue,
                                    completedByUserName = user?.name ?: "Unknown User",
                                    completedAt = history.completedAt,
                                    completedByUserProfilePicUrl = user?.let { File(context.filesDir, "profile_picture_${it.id}.jpg").absolutePath } ?: ""
                                )
                            }.sortedByDescending { it.completedAt } // Sort log items by completion time

                            Log.d("TaskHistoryVM", "Mapped to ${taskHistoryLogItems.size} TaskHistoryLogItems.")

                            // Step 3: Combine them. User scores usually go on top.
                            val combinedList: List<TaskHistoryFeedItem> = userScoreSummaryItems + taskHistoryLogItems
                            Log.d("TaskHistoryVM", "Combined list has ${combinedList.size} items.")
                            combinedList
                        }
                        .onStart {
                            _isLoading.value = true
                            _error.value = null
                            _feedItems.value = emptyList() // Clear previous items
                            Log.d("TaskHistoryVM", "Flow.onStart: Loading started.")
                        }
                        .catch { exception ->
                            Log.e("TaskHistoryVM", "CATCH (Flow): Error in Flow chain", exception)
                            _error.value = "Failed to load feed: ${exception.message}"
                            _feedItems.value = emptyList() // Clear items on error
                            _isLoading.value = false
                        }
                    } ?: flowOf(emptyList()) // Emit empty list if householdGroupId is null
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
    private fun calculateAndFormatUserScores( // No longer suspend
        historyItems: List<TaskHistory>,
        usersMap: Map<String, com.homeostasis.app.data.model.User> // Accept usersMap
    ): List<TaskHistoryFeedItem.UserScoreSummaryItem> {

        if (historyItems.isEmpty()) {
            Log.d("TaskHistoryVM", "calculateAndFormatUserScores: No history items.")
            return emptyList()
        }

        val userPoints = mutableMapOf<String, Int>() // UserId to Points
        val userLastActivity = mutableMapOf<String, Long>() // UserId to Timestamp

        for (history in historyItems) {
            userPoints[history.userId] = (userPoints[history.userId] ?: 0) + history.pointValue
            val currentLastActivity = userLastActivity[history.userId] ?: 0L
            if (history.completedAt.seconds > currentLastActivity) {
                userLastActivity[history.userId] = history.completedAt.seconds
            }
        }

        val scoreSummaryList = mutableListOf<TaskHistoryFeedItem.UserScoreSummaryItem>()
        for ((userId, points) in userPoints) {
            val user = usersMap[userId] // Get user from the map
            scoreSummaryList.add(
                TaskHistoryFeedItem.UserScoreSummaryItem(
                    userId = userId,
                    userName = user?.name ?: "User $userId",
                    userProfilePicUrl = user?.let { File(context.filesDir, "profile_picture_${it.id}.jpg").absolutePath },
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
