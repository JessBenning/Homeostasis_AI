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

/**
 * Fragment for displaying the list of tasks.
 */
@AndroidEntryPoint
class TaskListFragment : Fragment(), AddModTaskDialogFragment.AddModTaskListener, TaskAdapter.OnTaskClickListener {

    private val viewModel: TaskListViewModel by viewModels()

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var addTaskButton: FloatingActionButton
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private lateinit var swipeCallback: TaskSwipeCallback

    // Current user ID (would normally come from authentication)
    private val currentUserId = "current_user"

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

        taskAdapter = TaskAdapter(emptyList(), this)
        recyclerView.adapter = taskAdapter

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
        viewModel.recordTaskCompletion(task, currentUserId)


//        androidx.compose.material3.Snackbar.make(requireView(), "Task '${task.title}' marked complete!",
//            androidx.compose.material3.Snackbar.LENGTH_SHORT).show()
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
        taskAdapter = TaskAdapter(viewModel.tasks.value, this)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = taskAdapter

        // Set up swipe gestures and long press
        swipeCallback = TaskSwipeCallback(
            requireContext(),
            onSwipeRight = { position -> completeTask(position) },
            onSwipeLeft = { position -> undoTaskCompletion(position) },
            onEditClick = { position -> editTask(position) },
            onDeleteClick = { position -> confirmDeleteTask(position) }
        )

        itemTouchHelper = ItemTouchHelper(swipeCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // Attach gesture detector for long press
        swipeCallback.attachGestureDetector(recyclerView)
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

        // Update the UI
        taskAdapter.notifyItemChanged(position)

        // Show a snackbar with undo option
        showCompletionSnackbar(task)

        // Update user score (would normally be in ViewModel)
        updateUserScore(task.points)

        // Record task completion in local DB
        viewModel.recordTaskCompletion(task, currentUserId)

        taskAdapter.incrementCompletionCount(task.id)

        //completeTaskWithDate(position)
    }

    private fun completeTaskWithDate(position: Int, completedAt: Timestamp? = null) {
        // Get the task from the list
        val task = viewModel.tasks.value[position]

        // Update the UI
        taskAdapter.notifyItemChanged(position)

        // Show a snackbar with undo option
        showCompletionSnackbar(task)

        // Update user score (would normally be in ViewModel)
        updateUserScore(task.points)

        // Record task completion in local DB
        viewModel.recordTaskCompletion(task, currentUserId, completedAt)

        taskAdapter.incrementCompletionCount(task.id)
    }



    private fun undoTaskCompletion(position: Int) {
        // Get the task from the list
        val task = viewModel.tasks.value[position]

        if (taskAdapter.decrementCompletionCount(task.id)) {
            //ok to undo, counter >0


            // Update user score (would normally be in ViewModel)
            updateUserScore(-task.points)

            // Show a snackbar
            showUndoCompletionSnackbar(task)

            // Update user score (would normally be in ViewModel)
            updateUserScore(-task.points)

        }else
            showCantUndoSnackbar()
        // Update the UI
        taskAdapter.notifyItemChanged(position)

    }

    private fun showCompletionSnackbar(task: Task) {
        val message = "Task '${task.title}' completed: +${task.points} points"

        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
            .setAction("UNDO") {
                // Find the task in the list
                //val position = tasks.indexOfFirst { it.id == task.id }
                //if (position != -1) {
                //    completeTask(position)
                //}
            }
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

    private fun showCantUndoSnackbar() {
        Snackbar.make(
            requireView(),
            "Can't undo, you need to complete the task first",
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