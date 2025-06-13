package com.homeostasis.app.ui.tasks

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
//import androidx.compose.ui.semantics.getOrNull
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
// import androidx.lifecycle.observe // Not needed for Flow collection
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.homeostasis.app.R
import com.homeostasis.app.data.model.Task
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

/**
 * Fragment for displaying the list of tasks.
 * Manages UI interactions and observes DisplayTask objects from the ViewModel.
 */
@AndroidEntryPoint
class TaskListFragment : Fragment(), AddModTaskDialogFragment.AddModTaskListener, TaskAdapter.OnTaskClickListener {

    private val viewModel: TaskListViewModel by viewModels()
    @Inject lateinit var userRepository: com.homeostasis.app.data.remote.UserRepository

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var addTaskButton: FloatingActionButton
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private lateinit var swipeCallback: TaskSwipeCallback

    private val TAG = "TaskListFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_task_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.task_recycler_view)
        emptyView = view.findViewById(R.id.empty_view)
        addTaskButton = view.findViewById(R.id.add_task_button)

        setupRecyclerView()
        observeDisplayTasks() // Changed from observeTasks

        addTaskButton.setOnClickListener {
            showAddTaskDialog()
        }
    }

    // This interface method from TaskAdapter.OnTaskClickListener is now more complex due to session counts
    override fun onTaskMarkedComplete(task: Task, isNewSessionCompletion: Boolean) {
        Log.d(TAG, "onTaskMarkedComplete (from adapter click) for: ${task.title}. Is new for session: $isNewSessionCompletion")
        val currentUserId = userRepository.getCurrentUserId()

        if (currentUserId != null) {
            // 1. Increment session count in adapter
            // The adapter's click listener already determined if it was a "new" completion for the session *before* calling this.
            // So, we just tell the adapter to increment its internal count.
            taskAdapter.incrementSessionCompletionCount(task.id)
            val position = taskAdapter.displayTasks.indexOfFirst { it.task.id == task.id }
            if (position != -1) {
                taskAdapter.notifyItemChanged(position) // Update UI for session count change
            }

            // 2. Record completion in ViewModel (for TaskHistory and "Last done by/on")
            viewModel.recordTaskCompletion(task, currentUserId, Timestamp.now())

            // 3. Snackbar confirmation
            val currentSessionCount = taskAdapter.getSessionCompletionCount(task.id)
            showSnackbar("'${task.title}' completed! (Session: $currentSessionCount)")

            // 4. Update score (if applicable, possibly only for the first completion in session)
//            if (isNewSessionCompletion || task.points != 0) { // Example: always give points, or only if new
//                updateUserScore(task.points)
//            }
        } else {
            showSnackbar("Error: User not logged in. Cannot record task completion.")
        }
    }


    private fun observeDisplayTasks() { // Changed from observeTasks
        lifecycleScope.launch {
            viewModel.displayTasks.collectLatest { displayTasks -> // Observe displayTasks
                updateUIWithDisplayTasks(displayTasks) // Pass List<DisplayTask>
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called. Resetting session completion counts in adapter.")
        taskAdapter.resetAllSessionCompletionCounts() // Reset session counts
        taskAdapter.hideAllBadges() // If you have badges other than session counts
        swipeCallback.hideCurrentlyShownActions()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called.")
        // Data will be re-collected via observeDisplayTasks if it changed while paused.
        // Session counts are reset in onPause, so they will be fresh.
    }

    private fun setupRecyclerView() {
        swipeCallback = TaskSwipeCallback(
            requireContext(),
            onSwipeRight = { position -> completeTaskBySwipe(position) },
            onSwipeLeft = { position -> undoTaskCompletionBySwipe(position) },
            onEditClick = { position -> editTaskBySwipe(position) },
            onDeleteClick = { position -> confirmDeleteTaskBySwipe(position) }
        )

        itemTouchHelper = ItemTouchHelper(swipeCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
        swipeCallback.attachToRecyclerView(recyclerView)

        // Initialize adapter with an empty list of DisplayTask and the swipeCallback
        taskAdapter = TaskAdapter(emptyList(), this, swipeCallback)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = taskAdapter
    }


    private fun showAddTaskDialog() {
        val dialogFragment = AddModTaskDialogFragment.newInstance()
        dialogFragment.setAddModTaskListener(this)
        dialogFragment.show(parentFragmentManager, AddModTaskDialogFragment.TAG)
    }

    override fun onTaskAdded(task: Task) {
        val taskWithId = if (task.id.isEmpty()) {
            task.copy(id = java.util.UUID.randomUUID().toString())
        } else {
            task
        }
        viewModel.addTask(taskWithId)
        showSnackbar("Task '${taskWithId.title}' added successfully!")
    }

    override fun onTaskModified(task: Task) {
        viewModel.updateTask(task)
        showSnackbar("Task '${task.title}' updated successfully!")
    }

    // This is for direct clicks on the item if not handled by swipe or specific buttons
    override fun onTaskClick(task: Task) {
        Toast.makeText(requireContext(), "Task clicked: ${task.title}", Toast.LENGTH_SHORT).show()
        // If a general click should also mark as complete:
        // val isNewForSession = taskAdapter.getSessionCompletionCount(task.id) == 0
        // onTaskMarkedComplete(task, isNewForSession)
    }

    override fun onCompletionDateClick(task: Task, position: Int) { // Position might still be useful for quick access
        showDatePickerDialogForTask(task, position)
    }

    private fun showDatePickerDialogForTask(task: Task, position: Int) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(year, month, dayOfMonth)
                val selectedTimestamp = Timestamp(Date(selectedCalendar.timeInMillis))
                completeTaskWithSelectedDate(task, selectedTimestamp, position)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun editTaskBySwipe(position: Int) {
        val displayTask = taskAdapter.displayTasks.getOrNull(position) ?: return
        val task = displayTask.task
        val dialogFragment = AddModTaskDialogFragment.newInstance(task)
        dialogFragment.setAddModTaskListener(this)
        dialogFragment.show(parentFragmentManager, AddModTaskDialogFragment.TAG)
    }

    private fun confirmDeleteTaskBySwipe(position: Int) {
        val displayTask = taskAdapter.displayTasks.getOrNull(position) ?: return
        val task = displayTask.task
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Task")
            .setMessage("Are you sure you want to delete '${task.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                deleteTaskFromViewModel(task)
            }
            .setNegativeButton("Cancel") { _, _ ->
                taskAdapter.notifyItemChanged(position) // Reset swipe state
            }
            .setOnDismissListener {
                taskAdapter.notifyItemChanged(position) // Ensure swipe is reset if dialog is dismissed
            }
            .show()
    }

    private fun deleteTaskFromViewModel(task: Task) {
        viewModel.deleteTask(task)
        // Snackbar with UNDO for local-only deletion is tricky because the item might disappear fast.
        // The current setup: ViewModel marks for deletion, sync manager handles actual deletion.
        // UI updates reactively.
        showSnackbar("Task '${task.title}' marked for deletion.")
    }

    // Triggered by SWIPE RIGHT
    private fun completeTaskBySwipe(position: Int) {
        val displayTask = taskAdapter.displayTasks.getOrNull(position) ?: run {
            taskAdapter.notifyItemChanged(position) // Reset swipe if item not found
            return
        }
        val task = displayTask.task
        Log.d(TAG, "completeTaskBySwipe: ${task.title}")

        val isNewForSession = taskAdapter.getSessionCompletionCount(task.id) == 0
        // Delegate to the common completion logic
        onTaskMarkedComplete(task, isNewForSession)
        // No direct notifyItemChanged(position) here, as onTaskMarkedComplete handles it.
    }


    // Triggered by selecting a date in DatePickerDialog
    private fun completeTaskWithSelectedDate(task: Task, completedAt: Timestamp, position: Int) {
        val currentUserId = userRepository.getCurrentUserId()
        if (currentUserId != null) {
            // 1. Increment session count in adapter
            taskAdapter.incrementSessionCompletionCount(task.id)
            if (position != -1) { // Check if position is valid
                taskAdapter.notifyItemChanged(position) // Update UI for session count
            }

            // 2. Record in ViewModel with specific date
            viewModel.recordTaskCompletion(task, currentUserId, completedAt)

            val currentSessionCount = taskAdapter.getSessionCompletionCount(task.id)
            showSnackbar("'${task.title}' marked complete on ${completedAt.toDate( )}! (Session: $currentSessionCount)")
            //updateUserScore(task.points)
        } else {
            showSnackbar("Error: User not logged in.")
            if (position != -1) taskAdapter.notifyItemChanged(position) // Reset UI if needed
        }
    }

    // Triggered by SWIPE LEFT
    private fun undoTaskCompletionBySwipe(position: Int) {
        val displayTask = taskAdapter.displayTasks.getOrNull(position) ?: run {
            taskAdapter.notifyItemChanged(position) // Reset swipe if item not found
            return
        }
        val task = displayTask.task
        Log.d(TAG, "undoTaskCompletionBySwipe for: ${task.title}")

        val currentSessionCompletions = taskAdapter.getSessionCompletionCount(task.id)

        if (currentSessionCompletions > 0) {
            // 1. UI: Decrement session count first for immediate feedback
            taskAdapter.decrementSessionCompletionCount(task.id)
            taskAdapter.notifyItemChanged(position) // Update UI checkmark/counter

            // 2. DB: Proceed to undo the LATEST persisted completion
            lifecycleScope.launch {
                val currentUserId = userRepository.getCurrentUserId()
                if (currentUserId == null) {
                    showSnackbar("Error: User not logged in. Cannot undo database record.")
                    // Revert session count decrement if DB operation can't proceed
                    taskAdapter.incrementSessionCompletionCount(task.id)
                    taskAdapter.notifyItemChanged(position)
                    return@launch
                }

                val latestTaskHistoryId = viewModel.getLatestTaskHistoryIdForTaskAndUser(task.id, currentUserId)

                if (latestTaskHistoryId != null) {
                    val undoSuccessful = viewModel.undoTaskCompletionSuspend(latestTaskHistoryId)
                    if (undoSuccessful) {
                        showUndoCompletionSnackbar(task) // Includes points
                       // updateUserScore(-task.points) // Deduct points
                        // "Last done by/on" will update when displayTasks flow re-emits.
                    } else {
                        showSnackbar("Failed to undo the last recorded completion in database.")
                        // Revert session count decrement as DB undo failed
                        taskAdapter.incrementSessionCompletionCount(task.id)
                        taskAdapter.notifyItemChanged(position)
                    }
                } else {
                    // No persisted completion found in DB to match the UI session undo.
                    showSnackbar("No recorded completion found in database to undo for this task.")
                    // Revert session count decrement as there's no corresponding DB action.
                    taskAdapter.incrementSessionCompletionCount(task.id)
                    taskAdapter.notifyItemChanged(position)
                }
            }
        } else {
            showSnackbar("No completions in this session to undo for '${task.title}'.")
            taskAdapter.notifyItemChanged(position) // Reset swipe
        }
    }



    private fun showCompletionSnackbar(task: Task) { // Generic, used by swipe
        val message = "Task '${task.title}' completed: +${task.points} points"
        Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT).show()
    }

    private fun showUndoCompletionSnackbar(task: Task) {
        Snackbar.make(
            requireView(),
            "Completion of '${task.title}' undone: -${task.points} points",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT).show()
    }

//    private fun updateUserScore(points: Int) {
//        // TODO: Implement actual score update logic (e.g., in ViewModel or UserRepository)
//        Log.i(TAG, "User score updated by: $points points. (Current total: Not tracked in fragment)")
//        // Example: viewModel.updateUserScore(points)
//    }

    // Changed to accept List<DisplayTask>
    private fun updateUIWithDisplayTasks(displayTasks: List<DisplayTask>) {
        if (displayTasks.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            emptyView.text = getString(R.string.tasks_empty_message, "tasks") // Keep context for "tasks"
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
        taskAdapter.setTasks(displayTasks) // Pass List<DisplayTask>
    }
}