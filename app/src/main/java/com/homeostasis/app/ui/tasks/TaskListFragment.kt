package com.homeostasis.app.ui.tasks

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
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
 */
@AndroidEntryPoint
class TaskListFragment : Fragment(), AddModTaskDialogFragment.AddModTaskListener, TaskAdapter.OnTaskClickListener {

    private val viewModel: TaskListViewModel by viewModels()
    @Inject lateinit var userRepository: com.homeostasis.app.data.remote.UserRepository // Inject UserRepository

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var addTaskButton: FloatingActionButton
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private lateinit var swipeCallback: TaskSwipeCallback

    // Current user ID (would normally come from authentication)
// private val currentUserId = "current_user" // Removed hardcoded user ID

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_task_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize UI components
        recyclerView = view.findViewById(R.id.task_recycler_view)
        emptyView = view.findViewById(R.id.empty_view)
        addTaskButton = view.findViewById(R.id.add_task_button)

        // Set up RecyclerView
        setupRecyclerView()

        // Observe tasks from ViewModel
        observeTasks()

        // Set up click listeners
        addTaskButton.setOnClickListener {
            showAddTaskDialog()
        }

        // taskAdapter = TaskAdapter(emptyList(), this) // Removed: Initialized in setupRecyclerView instead
        // recyclerView.adapter = taskAdapter // Removed: Set in setupRecyclerView instead

//        viewModel.tasks.observe(viewLifecycleOwner) { tasks ->
//            taskAdapter.setTasks(tasks)
            // Decide if you want to reset counts when tasks are reloaded from ViewModel
            // taskAdapter.resetAllCompletionCounts() // Optional: if full reload should clear session counts
     //   }
    }

    override fun onTaskMarkedComplete(task: Task) {
        // TODO: Implement what should happen when a task is marked complete.
        // For example, you might want to:
        // - Update the task's status in your ViewModel or repository
        // - Show a Snackbar or Toast message
        // - Update the UI to reflect the change
        taskAdapter.incrementCompletionCount(task.id)

        // Get the current user ID
        val currentUserId = userRepository.getCurrentUserId()

        if (currentUserId != null) {
            viewModel.recordTaskCompletion(task, currentUserId) // Pass the actual user ID
        } else {
            // Handle case where user is not logged in
            showSnackbar("Error: User not logged in. Cannot record task completion.")
        }

        // Removed the redundant call to viewModel.recordTaskCompletion with hardcoded ID
        // viewModel.recordTaskCompletion(task, currentUserId)


        // Removed the commented out Snackbar code
        // //        androidx.compose.material3.Snackbar.make(requireView(), "Task '${task.title}' marked complete!",
        // //            androidx.compose.material3.Snackbar.LENGTH_SHORT).show()
    }

    private fun observeTasks() {
        lifecycleScope.launch {
            viewModel.tasks.collectLatest { tasks ->
                updateUI(tasks)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Reset task item appearance when leaving the fragment
        taskAdapter.hideAllBadges()

        // Hide any shown actions
        swipeCallback.hideCurrentlyShownActions()
    }

    private fun setupRecyclerView() {
        // Set up swipe gestures and long press FIRST
        swipeCallback = TaskSwipeCallback(
            requireContext(),
            onSwipeRight = { position -> completeTask(position) },
            onSwipeLeft = { position -> undoTaskCompletion(position) },
            onEditClick = { position -> editTask(position) },
            onDeleteClick = { position -> confirmDeleteTask(position) }
        )

        itemTouchHelper = ItemTouchHelper(swipeCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // Attach the RecyclerView to the swipe callback
        swipeCallback.attachToRecyclerView(recyclerView) // Add this line

        // Attach gesture detector for long press
        // swipeCallback.attachGestureDetector(recyclerView) // This was commented out previously

        // Then Set up RecyclerView and pass the initialized swipeCallback
        taskAdapter = TaskAdapter(viewModel.tasks.value, this, swipeCallback) // Pass swipeCallback
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = taskAdapter
    }


    private fun showAddTaskDialog() {
        val dialogFragment = AddModTaskDialogFragment.newInstance()
        dialogFragment.setAddModTaskListener(this)
        dialogFragment.show(parentFragmentManager, AddModTaskDialogFragment.TAG)
    }

    override fun onTaskAdded(task: Task) {
        // Generate a unique ID for the task if it doesn't have one
        val taskWithId = if (task.id.isEmpty()) {
            task.copy(id = java.util.UUID.randomUUID().toString())
        } else {
            task
        }

        // Add the task to ViewModel
        viewModel.addTask(taskWithId)

        // Show a success message
        showSnackbar("Task '${taskWithId.title}' added successfully!")
    }

    override fun onTaskModified(task: Task) {
        // Update the task in ViewModel
        viewModel.updateTask(task)

        // Show a success message
        showSnackbar("Task '${task.title}' updated successfully!")
    }

    override fun onTaskClick(task: Task) {
        // Handle task click (e.g., show task details)
        Toast.makeText(
            requireContext(),
            "Task clicked: ${task.title}",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onCompletionDateClick(task: Task, position: Int) {
        showDatePickerDialog(task, position)
    }

    private fun showDatePickerDialog(task: Task, position: Int) {
        val calendar = Calendar.getInstance()

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(year, month, dayOfMonth)

                // Complete the task with the selected date
                completeTaskWithDate(position, Timestamp(Date(selectedCalendar.timeInMillis)))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        datePickerDialog.show()
    }

    private fun editTask(position: Int) {
        // Get the task from the list
        val task = viewModel.tasks.value[position]

        // Show the dialog to edit the task
        val dialogFragment = AddModTaskDialogFragment.newInstance(task)
        dialogFragment.setAddModTaskListener(this)
        dialogFragment.show(parentFragmentManager, AddModTaskDialogFragment.TAG)
    }

    private fun confirmDeleteTask(position: Int) {
        // Get the task from the list
        val task = viewModel.tasks.value[position]

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Task")
            .setMessage("Are you sure you want to delete '${task.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                deleteTask(task)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteTask(task: Task) {
        // Delete the task from ViewModel
        viewModel.deleteTask(task)

        // Show a snackbar with undo option
        Snackbar.make(requireView(), "Task '${task.title}' deleted", Snackbar.LENGTH_LONG)
            .setAction("UNDO") {
                //TODO: implement undo
            }
            .show()
    }



//    private fun recordTaskCompletion(task: Task, userId: String, completedAt: Timestamp? = null) {
//        lifecycleScope.launch {
//            taskHistoryRepository.get().recordTaskCompletion(task.id, userId, task.points, completedAt?.toDate(), requireContext())
//        }
//    }
    private fun completeTask(position: Int) {
        // Get the task from the list
        val task = viewModel.tasks.value[position]

        // Get the current user ID
        val currentUserId = userRepository.getCurrentUserId()

        if (currentUserId != null) {
            // Update the UI
            taskAdapter.notifyItemChanged(position)

            // Show a snackbar with undo option
            showCompletionSnackbar(task)

            // Update user score (would normally be in ViewModel)
            updateUserScore(task.points) // This should ideally be in ViewModel

            // Record task completion in local DB
            viewModel.recordTaskCompletion(task, currentUserId) // Pass the actual user ID

            taskAdapter.incrementCompletionCount(task.id)

            //completeTaskWithDate(position)
        } else {
            // Handle case where user is not logged in
            showSnackbar("Error: User not logged in. Cannot complete task.")
        }
    }

    private fun completeTaskWithDate(position: Int, completedAt: Timestamp? = null) {
        // Get the task from the list
        val task = viewModel.tasks.value[position]

        // Get the current user ID
        val currentUserId = userRepository.getCurrentUserId()

        if (currentUserId != null) {
            // Update the UI
            taskAdapter.notifyItemChanged(position)

            // Show a snackbar with undo option
            showCompletionSnackbar(task)

            // Update user score (would normally be in ViewModel)
            updateUserScore(task.points) // This should ideally be in ViewModel

            // Record task completion in local DB
            viewModel.recordTaskCompletion(task, currentUserId, completedAt) // Pass the actual user ID

            taskAdapter.incrementCompletionCount(task.id)
        } else {
            // Handle case where user is not logged in
            showSnackbar("Error: User not logged in. Cannot complete task.")
        }
    }



    private fun undoTaskCompletion(position: Int) {
        // Get the task from the list
        val task = viewModel.tasks.value.getOrNull(position) ?: return // Safeguard
        val currentSessionCompletions = taskAdapter.getSessionCompletionCount(task.id)

        if (currentSessionCompletions > 0) {
            // There's a session completion to undo from the UI perspective
            taskAdapter.decrementCompletionCount(task.id) // This will update the UI checkmark/counter

            // Now, proceed to undo the LATEST persisted completion in the database
            lifecycleScope.launch {
                val currentUserId = userRepository.getCurrentUserId()
                if (currentUserId == null) {
                    showSnackbar("Error: User not logged in. Cannot undo task completion.")
                    // Optional: If user isn't logged in, but we "undid" a session count,
                    // we might want to increment it back if the DB operation fails.
                    // However, decrementing first gives immediate UI feedback.
                    // taskAdapter.incrementCompletionCount(task.id) // Revert UI if DB undo fails due to no user
                    return@launch
                }

                val latestTaskHistoryId = viewModel.getLatestTaskHistoryIdForTaskAndUser(task.id, currentUserId)

                if (latestTaskHistoryId != null) {
                    val undoSuccessful = viewModel.undoTaskCompletionSuspend(latestTaskHistoryId)
                    if (undoSuccessful) {
                        showUndoCompletionSnackbar(task)
                        // UI already updated by taskAdapter.decrementCompletionCount
                    } else {
                        // DB undo failed (e.g., history record already deleted, or some other issue)
                        // Since we already decremented the session count, we should revert that UI change
                        // if the DB operation wasn't successful, to keep UI consistent with what's undoable.
                        taskAdapter.incrementCompletionCount(task.id) // Revert UI change
                        showSnackbar("Failed to undo the last recorded completion.") // More specific
                    }
                } else {
                    // No persisted completion found in DB to match the UI session undo.
                    // This is an interesting state: UI showed it was completed in session, but DB has nothing.
                    // This could happen if completions were only in session and app was restarted before DB sync.
                    // Or if all DB completions were already undone through other means.
                    // Revert the UI session decrement as there's no corresponding DB action.
                    taskAdapter.incrementCompletionCount(task.id) // Revert UI change
                    showSnackbar("No recorded completion found to undo.")
                }
            }
        } else {
            // No completions in the current session for this task.
            // The swipe-left-to-undo shouldn't do anything or should indicate it can't be done.
           // showCantUndoSnackbar() // "Nothing to undo for this task in the current session."
            // We also need to tell the ItemTouchHelper to reset the item's swipe state
            // because the swipe was initiated but we determined no action should be taken.
            showSnackbar("You need to complete the task before it can be undone.")
            taskAdapter.notifyItemChanged(position) // This resets the swipe
        }
    }

    private fun showCompletionSnackbar(task: Task) {
        val message = "Task '${task.title}' completed: +${task.points} points"

        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
            .show()
    }

    private fun showUnabaleToUndoSnackbar(task: Task) {
        Snackbar.make(
            requireView(),
            "Task '${task.title}' completion undone: -${task.points} points",
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun showUndoCompletionSnackbar(task: Task) {
        Snackbar.make(
            requireView(),
            "Task '${task.title}' completion undone: -${task.points} points",
            Snackbar.LENGTH_LONG
        ).show()
    }



    private fun showSnackbar(message: String) {
        Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT).show()
    }

    private fun updateUserScore(points: Int) {
        //TODO
        // This would normally update the user's score in a repository or ViewModel
        // For now, just log the score change
        println("User score updated: $points points")
    }

    private fun updateUI(tasks: List<Task>) {
        if (tasks.isEmpty()) {
            showEmptyState()
        } else {
            showTasks(tasks)
        }
    }

    private fun showEmptyState() {
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.VISIBLE
        emptyView.text = getString(R.string.tasks_empty_message, "tasks")
    }

    private fun showTasks(tasks: List<Task>) {
        recyclerView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE

        // Notify the adapter that the data has changed
        taskAdapter.setTasks(tasks)
    }
}