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
import com.homeostasis.app.data.HouseholdGroupIdProvider // Import HouseholdGroupIdProvider
import java.io.File
import com.google.firebase.Timestamp // Ensure Timestamp is imported

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

    // Default signature if user or lastModifiedAt is somehow null
    private val defaultUserSignature = "0"

    init {
        Log.d("TaskHistoryVM", "ViewModel initialized by Hilt")
        loadCombinedFeed()
    }

    private fun loadCombinedFeed() {
        Log.d("TaskHistoryVM", "loadCombinedFeed() called")
        viewModelScope.launch {
            householdGroupIdProvider.getHouseholdGroupId()
                .flatMapLatest { householdGroupId ->
                    householdGroupId?.let { currentHouseholdId ->
                        val taskHistoryFlow = taskHistoryDao.getAllTaskHistoryFlow(currentHouseholdId)
                            .distinctUntilChanged()
                        val usersFlow = userDao.getAllUsersFlow(currentHouseholdId)
                            .distinctUntilChanged()

                        combine(taskHistoryFlow, usersFlow) { rawHistoryList, usersList ->
                            Log.i("TaskHistoryVM_Combine", "Combine triggered. Received ${rawHistoryList.size} history entries and ${usersList.size} users.")

                            // --- START: New Logging for usersList ---
                            if (usersList.isNotEmpty()) {
                                Log.d("TaskHistoryVM_Combine", "--- User Details from usersList ---")
                                usersList.forEach { user ->
                                    Log.d("TaskHistoryVM_Combine", "User ID: ${user.id}, Name: ${user.name}, lastModifiedAt: ${user.lastModifiedAt.toDate()} (seconds: ${user.lastModifiedAt.seconds})")
                                }
                                Log.d("TaskHistoryVM_Combine", "--- End User Details ---")
                            }
                            // --- END: New Logging for usersList ---

                            val usersMap = usersList.associateBy { it.id }

                            // Pass usersMap AND usersList for more detailed logging if needed in calculateAndFormatUserScores
                            val userScoreSummaryItems = calculateAndFormatUserScores(rawHistoryList, usersMap, usersList) // Modified to pass usersList
                            Log.d("TaskHistoryVM_Combine", "Calculated ${userScoreSummaryItems.size} UserScoreSummaryItems.")

                            val taskHistoryLogItems = rawHistoryList.mapNotNull { history ->
                                val user = usersMap[history.userId]
                                val task = taskDao.getTaskById(history.taskId, currentHouseholdId)

                                // --- START: New Logging for TaskHistoryItem user signature ---
                                val taskHistoryItemUserSignature = user?.lastModifiedAt?.seconds?.toString() ?: defaultUserSignature
                                if (user != null) {
                                    Log.d("TaskHistoryVM_FeedPrep", "TaskHistoryItem: User ID ${user.id}, Name: ${user.name}, PicSignature for item: $taskHistoryItemUserSignature (from user lastModifiedAt: ${user.lastModifiedAt.toDate()})")
                                }
                                // --- END: New Logging ---

                                TaskHistoryFeedItem.TaskHistoryItem(
                                    historyId = history.id,
                                    taskTitle = task?.title ?: "Unknown Task",
                                    points = history.pointValue,
                                    completedByUserId = history.userId,
                                    completedByUserName = user?.name ?: "Unknown User",
                                    completedAt = history.completedAt,
                                    completedByUserProfilePicLocalPath = user?.let {
                                        File(context.filesDir, "profile_picture_${it.id}.jpg").absolutePath
                                    },
                                    completedByUserProfilePicSignature = taskHistoryItemUserSignature // Use the logged variable
                                )
                            }.sortedByDescending { it.completedAt }
                            Log.d("TaskHistoryVM_Combine", "Mapped to ${taskHistoryLogItems.size} TaskHistoryLogItems.")

                            val combinedList: List<TaskHistoryFeedItem> = userScoreSummaryItems + taskHistoryLogItems
                            Log.d("TaskHistoryVM_Combine", "Combined list has ${combinedList.size} items.")
                            combinedList
                        }
                            .onStart {
                                _isLoading.value = true
                                _error.value = null
                                _feedItems.value = emptyList()
                                Log.d("TaskHistoryVM_Flow", "Flow.onStart: Loading started.")
                            }
                            .catch { exception ->
                                Log.e("TaskHistoryVM_Flow", "CATCH (Flow): Error in Flow chain", exception)
                                _error.value = "Failed to load feed: ${exception.message}"
                                _feedItems.value = emptyList()
                                _isLoading.value = false
                            }
                    } ?: flowOf(emptyList())
                }
                .collect { combinedFeedList ->
                    Log.i("TaskHistoryVM_Collect", "COLLECT: Collected ${combinedFeedList.size} combined feed items.")
                    _feedItems.value = combinedFeedList
                    if (_isLoading.value) {
                        _isLoading.value = false
                        Log.i("TaskHistoryVM_Collect", "COLLECT: Loading finished.")
                    }
                }
        }
    }

    private fun calculateAndFormatUserScores(
        historyItems: List<TaskHistory>,
        usersMap: Map<String, com.homeostasis.app.data.model.User>,
        usersListForLogging: List<com.homeostasis.app.data.model.User> // Added for logging purposes
    ): List<TaskHistoryFeedItem.UserScoreSummaryItem> {
        Log.d("TaskHistoryVM_Scores", "calculateAndFormatUserScores called.")
        // --- START: Detailed logging of users that will be used for scores ---
        if (usersListForLogging.isNotEmpty()) {
            Log.d("TaskHistoryVM_Scores", "--- User Details for Score Calculation (from usersListForLogging) ---")
            usersListForLogging.forEach { u ->
                Log.d("TaskHistoryVM_Scores", "User ID: ${u.id}, Name: ${u.name}, lastModifiedAt: ${u.lastModifiedAt.toDate()} (seconds: ${u.lastModifiedAt.seconds})")
            }
            Log.d("TaskHistoryVM_Scores", "--- End User Details ---")
        }
        // --- END: Detailed logging ---


        if (historyItems.isEmpty() && usersMap.isEmpty()) {
            Log.d("TaskHistoryVM_Scores", "No history items or users to calculate scores from.")
            return emptyList()
        }

        val userPoints = mutableMapOf<String, Int>()
        for (history in historyItems) {
            userPoints[history.userId] = (userPoints[history.userId] ?: 0) + history.pointValue
        }

        val scoreSummaryList = mutableListOf<TaskHistoryFeedItem.UserScoreSummaryItem>()
        for ((userId, points) in userPoints) {
            val user = usersMap[userId] // Get the user from the map

            // --- START: New Logging for UserScoreSummaryItem signature ---
            val userScorePicSignature = user?.lastModifiedAt?.seconds?.toString() ?: defaultUserSignature
            if (user != null) {
                Log.d("TaskHistoryVM_FeedPrep", "UserScoreSummaryItem: User ID ${user.id}, Name: ${user.name}, PicSignature for item: $userScorePicSignature (from user lastModifiedAt: ${user.lastModifiedAt.toDate()})")
            } else {
                Log.w("TaskHistoryVM_FeedPrep", "UserScoreSummaryItem: User not found in usersMap for ID $userId. Using default signature.")
            }
            // --- END: New Logging ---

            scoreSummaryList.add(
                TaskHistoryFeedItem.UserScoreSummaryItem(
                    userId = userId,
                    userName = user?.name ?: "User $userId",
                    userProfilePicLocalPath = user?.let {
                        File(context.filesDir, "profile_picture_${it.id}.jpg").absolutePath
                    },
                    totalScore = points,
                    userProfilePicSignature = userScorePicSignature // Use the logged variable
                )
            )
        }
        return scoreSummaryList.sortedByDescending { it.totalScore }
    }






    fun clearError() {
        _error.value = null
    }
}