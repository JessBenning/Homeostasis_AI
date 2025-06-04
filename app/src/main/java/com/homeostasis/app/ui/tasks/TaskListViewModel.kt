package com.homeostasis.app.ui.tasks

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.homeostasis.app.data.TaskDao
import com.homeostasis.app.data.TaskHistoryDao
import com.homeostasis.app.data.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.homeostasis.app.data.remote.TaskRepository
import kotlinx.coroutines.flow.distinctUntilChanged

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val taskRepository: TaskRepository, // Inject TaskRepository
    private val taskHistoryDao: TaskHistoryDao,
    private val taskDao: TaskDao
) : ViewModel() {

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks

    private val TAG = "TaskListViewModel" // Define a TAG


    init {
        loadActiveTasks() // Renamed for clarity

        // Your existing logging logic for when tasks are loaded
        viewModelScope.launch {
            tasks.collect { taskList ->
                if (taskList.isNotEmpty()) {
                    Log.d(TAG, "Active tasks loaded/updated (${taskList.size}):")
                    // You might not need to log every task here on every update
                    // Consider logging only on first load or significant changes
                } else {
                    Log.d(TAG, "Active tasks list is currently empty.")
                }
            }
        }
    }


    private fun loadActiveTasks() {
        viewModelScope.launch {
            // Observe tasks from the DAO that are NOT soft-deleted
            // The DAO's getActiveTasks() method should have the "WHERE isDeleted = 0" clause
            taskDao.getActiveVisibleTasks()
                .distinctUntilChanged() // Only emit when the list content actually changes
                .collect { activeTasksFromDb ->
                    _tasks.value = activeTasksFromDb
                    Log.d(TAG, "loadActiveTasks collected: ${activeTasksFromDb.size} tasks")
                }
        }
    }

    fun addTask(task: Task) {
        viewModelScope.launch {
            // Prepare the task for local insertion, mark for sync
            val taskToInsertLocally = task.copy(
                isDeleted = false, // Ensure it's not marked deleted if it's new
                lastModifiedAt = Timestamp.now(),
                needsSync = true, // Mark for synchronization
                isDeletedLocally = false
            )
            taskDao.upsertTask(taskToInsertLocally) // Or your equivalent DAO method
            // FirebaseSyncManager will handle calling taskRepository.createOrUpdateTaskInFirestore later
        }
    }


    fun updateTask(task: Task) {
        viewModelScope.launch {
            val taskToUpdateLocally = task.copy(
                lastModifiedAt = Timestamp.now(),
                needsSync = true
            )
            taskDao.upsertTask(taskToUpdateLocally) // Or your equivalent DAO method for updates
            // FirebaseSyncManager will handle calling taskRepository.createOrUpdateTaskInFirestore later
        }
    }

    /**
     * Marks a task as deleted locally. The actual deletion from Firestore
     * and subsequently from Room will be handled by FirebaseSyncManager.
     */
    fun deleteTask(task: Task) {
        viewModelScope.launch {
            if (task.id.isNotBlank()) {
                Log.d(TAG, "Marking task for local deletion and sync: ${task.id}")
                // 1. Mark as deleted locally AND needs sync
                val taskToMarkAsDeleted = task.copy(
                    isDeletedLocally = true, // Mark that it's deleted from the user's perspective locally
                    needsSync = true,        // Indicate that this deletion needs to be synced to Firestore
                    lastModifiedAt = Timestamp.now()
                )
                taskDao.upsertTask(taskToMarkAsDeleted) // Update the task in Room

                // The UI will update because it observes tasks from `taskDao.getActiveVisibleTasks()`
                // which should ideally filter out tasks where `isDeletedLocally = true`.
                // OR, your `getActiveVisibleTasks()` might only filter `isDeleted = false`,
                // and `FirebaseSyncManager` will later set `isDeleted = true` after successful Firestore delete.

                // FirebaseSyncManager will later:
                // - See `needsSync = true` and `isDeletedLocally = true`.
                // - Call `taskRepository.softDeleteTaskInFirestore(task.id)` or `hardDeleteTaskFromFirestore(task.id)`.
                // - If successful, potentially update the Room record:
                //   - Set `isDeleted = true` (the "official" synced deletion status).
                //   - Set `isDeletedLocally = false` (local deletion intent is processed).
                //   - Set `needsSync = false`.
                //   - Or, if hard deleting, remove the record from Room entirely.
            } else {
                Log.e(TAG, "Task ID is blank, cannot mark as deleted.")
            }
        }
    }

    fun recordTaskCompletion(task: Task, userId: String, completedAt: Timestamp? = null) {
        viewModelScope.launch {
            val documentId = java.util.UUID.randomUUID().toString()
            val taskHistory = com.homeostasis.app.data.model.TaskHistory(
                id = documentId,
                taskId = task.id,
                userId = userId,
                completedAt = completedAt ?: Timestamp.now(),
                pointValue = task.points,
                isDeleted = false,
                isArchived = false,
                archivedInResetId = null,
                needsSync = true,
                lastModifiedAt = Timestamp.now()
            )
            Log.d(TAG, "Attempting to insert TaskHistory into Room: $taskHistory") // Log BEFORE insert
            try {
                taskHistoryDao.insert(taskHistory)
                Log.d(TAG, "SUCCESS: TaskHistory entry inserted into Room. ID: ${taskHistory.id}") // Log AFTER successful insert
            } catch (e: Exception) {
                Log.e(TAG, "ERROR inserting TaskHistory into Room: ${e.message}", e) // Log any error
            }
        }
    }

}