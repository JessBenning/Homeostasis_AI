package com.homeostasis.app.ui.tasks

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.homeostasis.app.data.local.TaskDao
import com.homeostasis.app.data.local.TaskHistoryDao
import com.homeostasis.app.data.local.UserDao
import com.homeostasis.app.data.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.homeostasis.app.data.remote.UserRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import com.homeostasis.app.data.model.Group
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map // Added for transforming the flow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.flow.combine // <<< IMPORT THIS

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val taskRepository: com.homeostasis.app.data.remote.TaskRepository, // Keep your existing TaskRepository
    private val userRepository: UserRepository,
    private val taskHistoryDao: TaskHistoryDao,
    private val taskDao: TaskDao,
    private val groupRepository: com.homeostasis.app.data.remote.GroupRepository, // Inject GroupRepository
    private val userDao: UserDao // Inject UserDao
) : ViewModel() {

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    // val tasks: StateFlow<List<Task>> = _tasks // Expose if needed

    private val _displayTasks = MutableStateFlow<List<DisplayTask>>(emptyList())
    val displayTasks: StateFlow<List<DisplayTask>> = _displayTasks

    // ADD THIS TRIGGER
    private val taskHistoryChangeTrigger = MutableStateFlow(0)

    private val TAG = "TaskListViewModel"

    init {
        setupDisplayTasksFlow() // Call the new combined flow setup

        viewModelScope.launch {
            _displayTasks.collect { displayTaskList ->
                if (displayTaskList.isNotEmpty()) {
                    Log.d(TAG, "Display tasks loaded/updated (${displayTaskList.size}) with last completed info.")
                } else {
                    Log.d(TAG, "Display tasks list is currently empty.")
                }
            }
        }
    }

    private fun formatDate(timestamp: Timestamp?): String? {
        return timestamp?.toDate()?.let { date ->
            val sdf = SimpleDateFormat("HH:mm MMM dd", Locale.getDefault()) // Corrected typo HH:MM to HH:mm
            sdf.format(date)
        }
    }

    private fun setupDisplayTasksFlow() {
        viewModelScope.launch {
            // Get the current user's ID
            val currentUserId = userRepository.getCurrentUserId()

            if (currentUserId == null) {
                Log.w(TAG, "Cannot set up display tasks flow: Current user ID is null.")
                _displayTasks.value = emptyList() // Emit empty list if no user
                return@launch // Exit the coroutine if no user
            }

            // Observe the current user's householdGroupId from the local database
            userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId)
                .map { user ->
                    // Provide the householdGroupId from the user object, or default if null/empty
                    user?.householdGroupId?.takeIf { it.isNotEmpty() } ?: com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
                }
                .distinctUntilChanged() // Only react when the householdGroupId actually changes
                .flatMapLatest { householdGroupId ->
                    Log.d(TAG, "flatMapLatest: new householdGroupId: $householdGroupId. Combining TaskDao and HistoryTrigger.")
                    if (householdGroupId != com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID) { // Only combine if in a specific group
                        combine(
                            taskDao.getActiveVisibleTasks(householdGroupId), // Flow<List<Task>> filtered by group
                            taskHistoryChangeTrigger // Flow<Int> - our trigger
                        ) { tasksFromDb, triggerValue -> // tasksFromDb is List<Task>, triggerValue is the Int (not directly used but signals change)
                            Log.d(TAG, "Combine triggered: Tasks count = ${tasksFromDb.size}, Trigger value = $triggerValue. Processing for display...")
                            _tasks.value = tasksFromDb // Update raw task list if needed

                            val sortedTasksFromDb = tasksFromDb.sortedBy { it.title.lowercase() } // Sort by lowercase title for case-insensitive sort

                            sortedTasksFromDb.map { task ->
                                async(viewModelScope.coroutineContext) {
                                    // Fetch history for this task within the current household group
                                    val latestHistory = taskHistoryDao.getLatestTaskHistoryForTask(task.id, householdGroupId)
                                    var lastCompletedByName: String? = null // Default to null, then "N/A" if still null after check
                                    if (latestHistory?.userId != null) {
                                        // Fetch user for history entry within the current household group
                                        val user = userDao.getUserById(latestHistory.userId) // getUser now handles householdGroupId internally
                                        lastCompletedByName = user?.name ?: "Unknown User"
                                    } else {
                                        lastCompletedByName = "N/A"
                                    }
                                    Log.v(TAG, "Task '${task.title}' (processing): LastHistoryUser=${latestHistory?.userId}, CompletedAt=${formatDate(latestHistory?.completedAt)}, By=${lastCompletedByName}")

                                    DisplayTask(
                                        task = task,
                                        lastCompletedByDisplay = lastCompletedByName,
                                        lastCompletedDateDisplay = formatDate(latestHistory?.completedAt)
                                    )
                                }
                            }.awaitAll()
                        }
                    } else {
                        Log.d(TAG, "flatMapLatest: householdGroupId is default, emitting empty list for displayTasks.")
                        flowOf(emptyList<DisplayTask>()) // Emit empty if no specific household ID
                    }
                }
                .distinctUntilChanged() // Only update _displayTasks if the content actually changes
                .collect { processedDisplayTasks ->
                    _displayTasks.value = processedDisplayTasks
                    Log.d(TAG, "Collect: _displayTasks updated with ${processedDisplayTasks.size} items post-combine.")
                }
        }
    }

    fun addTask(task: Task) {
        viewModelScope.launch {
            val currentUserId = userRepository.getCurrentUserId()
            val currentHouseholdId = if (currentUserId != null) {
                userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId).first()?.householdGroupId?.takeIf { it.isNotEmpty() } ?: com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
            } else {
                com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
            }

            val taskToInsertLocally = task.copy(
                isDeleted = false,
                lastModifiedAt = Timestamp.now(),
                needsSync = true,
                isDeletedLocally = false,
                householdGroupId = currentHouseholdId
            )
            taskDao.upsertTask(taskToInsertLocally)
            Log.d(TAG, "Task '${task.title}' added to DAO. Flow should trigger reprocessing.")
            // No need to poke taskHistoryChangeTrigger here unless adding a task also adds a history item.
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            val currentUserId = userRepository.getCurrentUserId()
            val currentHouseholdId = if (currentUserId != null) {
                userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId).first()?.householdGroupId?.takeIf { it.isNotEmpty() } ?: com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
            } else {
                com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
            }
            val taskToUpdateLocally = task.copy(
                lastModifiedAt = Timestamp.now(),
                needsSync = true,
                householdGroupId = currentHouseholdId
            )
            taskDao.upsertTask(taskToUpdateLocally)
            Log.d(TAG, "Task '${task.title}' updated in DAO. Flow should trigger reprocessing.")
            // No need to poke taskHistoryChangeTrigger here.
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            if (task.id.isNotBlank()) {
                val currentUserId = userRepository.getCurrentUserId()
                val currentHouseholdId = if (currentUserId != null) {
                    userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId).first()?.householdGroupId?.takeIf { it.isNotEmpty() } ?: com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
                } else {
                    com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
                }
                Log.d(TAG, "Marking task for local deletion and sync: ${task.id}")
                val taskToMarkAsDeleted = task.copy(
                    isDeletedLocally = true,
                    needsSync = true,
                    lastModifiedAt = Timestamp.now(),
                    householdGroupId = currentHouseholdId
                )
                taskDao.upsertTask(taskToMarkAsDeleted)
                Log.d(TAG, "Task '${task.title}' marked for deletion in DAO. Flow should trigger reprocessing.")
                // Deleting a task might implicitly mean its "last done" info is no longer relevant for that task
                // but the task list itself changes, which triggers the combine.
                // If there's a specific history cleanup that affects other tasks' display, then trigger.
                // For now, assuming taskDao change is sufficient.
            } else {
                Log.e(TAG, "Task ID is blank, cannot mark as deleted.")
            }
        }
    }

    suspend fun undoTaskCompletionSuspend(taskHistoryId: String): Boolean {
        val successfulUndo = try {
            val currentUserId = userRepository.getCurrentUserId()
            val householdGroupId = if (currentUserId != null) {
                userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId).first()?.householdGroupId?.takeIf { it.isNotEmpty() } ?: com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
            } else {
                com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
            }
            val taskHistory = taskHistoryDao.getTaskHistoryById(taskHistoryId, householdGroupId)

            if (taskHistory != null && !taskHistory.isDeleted && !taskHistory.isDeletedLocally) {
                taskHistoryDao.markTaskHistoryAsDeletedLocally(taskHistoryId, householdGroupId)
                Log.d(TAG, "SUCCESS: TaskHistory entry marked as deleted locally. ID: $taskHistoryId")
                // POKE THE TRIGGER after successful local DB modification
                taskHistoryChangeTrigger.value++ // Or .value-- , the change is what matters
                Log.d(TAG, "UndoTaskCompletion: taskHistoryChangeTrigger updated to ${taskHistoryChangeTrigger.value}")
                true
            } else {
                if (taskHistory == null) {
                    Log.e(TAG, "ERROR: Could not find TaskHistory entry with ID $taskHistoryId to undo.")
                } else {
                    Log.w(TAG, "INFO: TaskHistory entry with ID $taskHistoryId was already deleted or not undoable.")
                }
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "ERROR undoing TaskHistory completion: ${e.message}", e)
            false
        }
        return successfulUndo
    }

    suspend fun getLatestTaskHistoryIdForTaskAndUser(taskId: String, userId: String): String? {
        val currentUserId = userRepository.getCurrentUserId()
        val householdGroupId = if (currentUserId != null) {
            userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId).first()?.householdGroupId?.takeIf { it.isNotEmpty() } ?: com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
        } else {
            com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
        }
        val latestHistory = taskHistoryDao.getLatestTaskHistoryForTaskAndUser(taskId, userId, householdGroupId)
        return latestHistory?.id
    }

    fun recordTaskCompletion(task: Task, userId: String, completedAtTimestamp: Timestamp? = null) {
        viewModelScope.launch {
            val currentUserId = userRepository.getCurrentUserId()
            val currentHouseholdId = if (currentUserId != null) {
                userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId).first()?.householdGroupId?.takeIf { it.isNotEmpty() } ?: com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
            } else {
                com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
            }
            val effectiveCompletedAt = completedAtTimestamp ?: Timestamp.now()
            val documentId = java.util.UUID.randomUUID().toString()

            Log.d(TAG, "RecordTaskCompletion: Task '${task.title}', User '$userId', Timestamp: ${effectiveCompletedAt.toDate()}")

            val taskHistory = com.homeostasis.app.data.model.TaskHistory(
                id = documentId,
                taskId = task.id,
                userId = userId,
                completedAt = effectiveCompletedAt,
                pointValue = task.points,
                isDeleted = false,
                isDeletedLocally = false,
                needsSync = true, // Ensure your TaskHistory model has this if you sync it
                householdGroupId = currentHouseholdId // Ensure your TaskHistory model has this
            )

            try {
                // IMPORTANT: Ensure this is your actual DAO method to insert/save a TaskHistory item
                taskHistoryDao.insertOrUpdate(taskHistory)
                Log.d(TAG, "RecordTaskCompletion: TaskHistory saved successfully for task ${task.id}. History ID: $documentId")

                // POKE THE TRIGGER after successful local DB modification
                taskHistoryChangeTrigger.value++
                Log.d(TAG, "RecordTaskCompletion: taskHistoryChangeTrigger incremented to ${taskHistoryChangeTrigger.value}")

            } catch (e: Exception) {
                Log.e(TAG, "RecordTaskCompletion: Error saving TaskHistory for task ${task.id}", e)
                // Optionally, you might want to revert the trigger or handle the error in the UI
            }
        }


    }
    fun getCurrentUserId(): String? {
        return userRepository.getCurrentUserId()
    }

    suspend fun getCurrentHouseholdGroupId(): String? {
        val currentUserId = userRepository.getCurrentUserId()
        return if (currentUserId != null) {
            userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId).first()?.householdGroupId?.takeIf { it.isNotEmpty() } ?: com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
        } else {
            com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
        }
    }

    suspend fun getCurrentGroupOwnerId(): String? {
        val householdGroupId = getCurrentHouseholdGroupId()
        return if (householdGroupId != null && householdGroupId.isNotEmpty()) {
            groupRepository.getGroupById(householdGroupId).first()?.ownerId
        } else {
            null
        }
    }

    suspend fun getGroupById(groupId: String): Group? {
        return groupRepository.getGroupById(groupId).first()
    }
}