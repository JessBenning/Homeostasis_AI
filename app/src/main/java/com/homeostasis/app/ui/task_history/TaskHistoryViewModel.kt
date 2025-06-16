package com.homeostasis.app.ui.task_history

import com.homeostasis.app.data.Constants
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
import java.io.File
import kotlinx.coroutines.channels.Channel // Import Channel
import kotlinx.coroutines.flow.receiveAsFlow // Import receiveAsFlow
import com.homeostasis.app.data.model.Group // Import Group

@HiltViewModel
class TaskHistoryViewModel @Inject constructor(
    private val taskHistoryDao: TaskHistoryDao,
    private val taskDao: TaskDao,
    private val userDao: UserDao,
    private val userRepository: com.homeostasis.app.data.remote.UserRepository, // Inject UserRepository
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context, // Inject ApplicationContext
    private val groupRepository: com.homeostasis.app.data.remote.GroupRepository, // Inject GroupRepository
    private val firebaseSyncManager: com.homeostasis.app.data.FirebaseSyncManager // Inject FirebaseSyncManager
    // ... any other dependencies
) : ViewModel() {

    // For task history items
    private val _feedItems = MutableStateFlow<List<TaskHistoryFeedItem>>(emptyList())
    val feedItems: StateFlow<List<TaskHistoryFeedItem>> = _feedItems.asStateFlow()

    // For loading state
    private val _isLoading = MutableStateFlow(false)

    // For reset scores event
    private val _resetScoresEvent = Channel<Unit>(Channel.Factory.BUFFERED) // Channel for one-time events
    val resetScoresEvent = _resetScoresEvent.receiveAsFlow() // Expose as Flow

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
            // Step 1: Get the current user ID.
            // Assuming userRepository.getCurrentUserId() is a suspend function or can be called directly.
            // If it returns a Flow, you'd collect it first or integrate it differently.
            val currentUserId = userRepository.getCurrentUserId()

            // Step 2: Create a Flow that emits the User object or null based on the currentUserId
            val userObjectFlow: Flow<com.homeostasis.app.data.model.User?> = if (currentUserId != null) {
                Log.d("TaskHistoryVM_User", "User ID found: $currentUserId. Creating user flow.")
                userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId)
            } else {
                Log.w("TaskHistoryVM_User", "User ID is null. Emitting null for user flow.")
                flowOf(null) // Emit null if no user ID
            }

            // Step 3: flatMapLatest based on the emitted User object to get the householdGroupId
            // and then proceed to fetch and combine data.
            userObjectFlow.flatMapLatest { user -> // 'user' is the User object from the flow or null
                // Extract householdGroupId from the user object
                val householdGroupId = user?.householdGroupId?.takeIf { it.isNotEmpty() }
                Log.d("TaskHistoryVM_Debug", "flatMapLatest after userObjectFlow: Current householdGroupId: $householdGroupId")

                if (householdGroupId != null) {
                    // If householdGroupId is valid, proceed to fetch task history and users
                    val taskHistoryFlow = taskHistoryDao.getAllTaskHistoryFlow(householdGroupId)
                        .distinctUntilChanged()
                    val usersFlow = userDao.getAllUsersFlow(householdGroupId)
                        .distinctUntilChanged()

                    combine(taskHistoryFlow, usersFlow) { rawHistoryList, usersList ->
                        Log.i("TaskHistoryVM_Combine", "Combine triggered. Received ${rawHistoryList.size} history entries and ${usersList.size} users.")

                        if (usersList.isNotEmpty()) {
                            Log.d("TaskHistoryVM_Combine", "--- User Details from usersList ---")
                            usersList.forEach { u -> // Renamed to avoid conflict with outer 'user'
                                Log.d("TaskHistoryVM_Combine", "User ID: ${u.id}, Name: ${u.name}, lastModifiedAt: ${u.lastModifiedAt.toDate()} (seconds: ${u.lastModifiedAt.seconds})")
                            }
                            Log.d("TaskHistoryVM_Combine", "--- End User Details ---")
                        }

                        val usersMap = usersList.associateBy { it.id }

                        val userScoreSummaryItems = calculateAndFormatUserScores(rawHistoryList, usersMap, usersList)
                        Log.d("TaskHistoryVM_Combine", "Calculated ${userScoreSummaryItems.size} UserScoreSummaryItems.")

                        val taskHistoryLogItems = rawHistoryList.mapNotNull { history ->
                            val completedByUser = usersMap[history.userId] // Renamed to avoid conflict
                            val task = taskDao.getTaskById(history.taskId, householdGroupId) // currentHouseholdId is householdGroupId here

                            val taskHistoryItemUserSignature = completedByUser?.lastModifiedAt?.seconds?.toString() ?: defaultUserSignature
                            if (completedByUser != null) {
                                Log.d("TaskHistoryVM_FeedPrep", "TaskHistoryItem: User ID ${completedByUser.id}, Name: ${completedByUser.name}, PicSignature for item: $taskHistoryItemUserSignature (from user lastModifiedAt: ${completedByUser.lastModifiedAt.toDate()})")
                            }

                            TaskHistoryFeedItem.TaskHistoryItem(
                                historyId = history.id,
                                taskTitle = task?.title ?: "Unknown Task",
                                points = history.pointValue,
                                completedByUserId = history.userId,
                                completedByUserName = completedByUser?.name ?: "Unknown User",
                                completedAt = history.completedAt,
                                completedByUserProfilePicLocalPath = completedByUser?.let {
                                    File(context.filesDir, "profile_picture_${it.id}.jpg").absolutePath
                                },
                                completedByUserProfilePicSignature = taskHistoryItemUserSignature
                            )
                        }.sortedByDescending { it.completedAt }
                        Log.d("TaskHistoryVM_Combine", "Mapped to ${taskHistoryLogItems.size} TaskHistoryLogItems.")

                        val combinedList: List<TaskHistoryFeedItem> = userScoreSummaryItems + taskHistoryLogItems
                        Log.d("TaskHistoryVM_Combine", "Combined list has ${combinedList.size} items.")
                        combinedList // This is the List<TaskHistoryFeedItem> emitted by combine
                    }
                } else {
                    // If householdGroupId is null or empty, emit an empty list for the feed
                    Log.d("TaskHistoryVM_Debug", "householdGroupId is null or empty. Emitting empty list for the feed.")
                    flowOf(emptyList<TaskHistoryFeedItem>())
                }
            }
                .onStart {
                    _isLoading.value = true
                    _error.value = null
                    // _feedItems.value = emptyList() // Clearing here is okay, but the first emission from flowOf(emptyList()) or combine will also set it.
                    Log.d("TaskHistoryVM_Flow", "Flow.onStart: Loading started.")
                }
                .catch { exception ->
                    Log.e("TaskHistoryVM_Flow", "CATCH (Flow): Error in Flow chain", exception)
                    _error.value = "Failed to load feed: ${exception.message}"
                    _feedItems.value = emptyList() // Ensure feed is cleared on error
                    _isLoading.value = false
                }
                .collect { combinedFeedList ->
                    Log.i("TaskHistoryVM_Collect", "COLLECT: Collected ${combinedFeedList.size} combined feed items.")
                    _feedItems.value = combinedFeedList
                    // isLoading should be false if data is collected or an error occurred before collect.
                    // The catch block handles setting isLoading to false on error.
                    // If the flow completes successfully (even with an empty list), isLoading should be false.
                    if (_isLoading.value) { // This check might only be true if onStart was the last thing to set it.
                        _isLoading.value = false // Set loading to false once data is collected or flow completes
                    }
                    Log.i("TaskHistoryVM_Collect", "COLLECT: Loading finished (isLoading is now ${_isLoading.value}).")
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

    // Added methods to get household group ID and group by ID
    suspend fun getCurrentHouseholdGroupId(): String? {
        val currentUserId = userRepository.getCurrentUserId()
        return if (currentUserId != null) {
            userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId).first()?.householdGroupId?.takeIf { it.isNotEmpty() } ?: com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
        } else {
            com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
        }
    }

    suspend fun getGroupById(groupId: String): Group? {
        return groupRepository.getGroupById(groupId).first()
    }

    fun resetScoresAndHistory() {
        viewModelScope.launch {
            try {
                val currentUserId = userRepository.getCurrentUserId()
                val householdGroupId = if (currentUserId != null) {
                    userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId).first()?.householdGroupId?.takeIf { it.isNotEmpty() } ?: com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
                } else {
                    com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
                }

                if (householdGroupId != null && householdGroupId.isNotEmpty()) {
                    // Delete locally first
                    taskHistoryDao.deleteAllTaskHistory(householdGroupId) // Use DAO directly for local deletion
                    Log.d("TaskHistoryVM", "Local task history deleted for group: $householdGroupId")

                    // Then trigger remote sync for deleted history
                    firebaseSyncManager.syncDeletedTaskHistoryRemote()
                    Log.d("TaskHistoryVM", "Remote sync for deleted task history triggered.")

                    // Signal success to the Fragment
                    _resetScoresEvent.send(Unit)
                } else {
                    Log.w("TaskHistoryVM", "Cannot reset scores and history, householdGroupId is null or empty.")
                    // TODO: Handle case where householdGroupId is null or empty (e.g., show a message)
                }
            } catch (e: Exception) {
                Log.e("TaskHistoryVM", "Error resetting scores and history", e)
                // TODO: Handle error (e.g., show an error message to the user)
            }
        }
    }
}
