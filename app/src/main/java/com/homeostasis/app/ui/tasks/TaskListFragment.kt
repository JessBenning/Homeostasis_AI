package com.homeostasis.app.ui.tasks

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
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
import java.util.Calendar
import java.util.Date

/**
 * Fragment for displaying the list of tasks.
 */
@AndroidEntryPoint
class TaskListFragment : Fragment(), AddTaskDialogFragment.AddTaskListener, TaskAdapter.OnTaskClickListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var addTaskButton: FloatingActionButton
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private lateinit var swipeCallback: TaskSwipeCallback

    //TODO
    // List to store tasks (would normally be in ViewModel)
    private val tasks = mutableListOf<Task>()
    
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
        
        // Set up UI state
        updateUI()

        // Set up click listeners
        addTaskButton.setOnClickListener {
            showAddTaskDialog()
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Reset task item appearance when leaving the fragment
        taskAdapter.resetItemAppearance()
        
        // Hide any shown actions
        //swipeCallback.hideActions()
        swipeCallback.hideCurrentlyShownActions()
    }
    
    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(tasks, this)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = taskAdapter
        }
        
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

        //TEMP tasks for debug
        //TODO: load tasks from local DB
        val task = Task(
            title = "Load Dishwasher",
            description = "Load the damn dishwasher",
            points = 30,
            categoryId = "Household"
        )
        onTaskAdded(task)


    }
    
    private fun showAddTaskDialog() {
        val dialogFragment = AddTaskDialogFragment.newInstance()
        dialogFragment.setAddTaskListener(this)
        dialogFragment.show(parentFragmentManager, AddTaskDialogFragment.TAG)
    }

    override fun onTaskAdded(task: Task) {
        // Add the task to our list
        tasks.add(task)
        
        // Update the UI
        updateUI()
        
        // Show a success message
        showSnackbar("Task '${task.title}' added successfully!")
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
        if (position < 0 || position >= tasks.size) return
        
        val task = tasks[position]

        //TODO
        // In a real app, you would show a dialog to edit the task
        // For now, just show a toast
        Toast.makeText(
            requireContext(),
            "Edit task: ${task.title}",
            Toast.LENGTH_SHORT
        ).show()
    }
    
    private fun confirmDeleteTask(position: Int) {
        if (position < 0 || position >= tasks.size) return
        
        val task = tasks[position]
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Task")
            .setMessage("Are you sure you want to delete '${task.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                deleteTask(position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteTask(position: Int) {
        if (position < 0 || position >= tasks.size) return
        
        val task = tasks[position]
        
        // Remove the task from the list
        tasks.removeAt(position)
        
        // Update the UI
        taskAdapter.notifyItemRemoved(position)
        updateUI()
        
        // Show a snackbar with undo option
        Snackbar.make(requireView(), "Task '${task.title}' deleted", Snackbar.LENGTH_LONG)
            .setAction("UNDO") {
                // Add the task back to the list
                tasks.add(position, task)
                taskAdapter.notifyItemInserted(position)
                updateUI()
            }
            .show()
    }
    
    private fun completeTask(position: Int) {
        if (position < 0 || position >= tasks.size) return
        
        val task = tasks[position]
        val updatedTask = task.complete(currentUserId)
        
        // Update the task in the list
        tasks[position] = updatedTask
        
        // Update the UI
        taskAdapter.notifyItemChanged(position)
        
        // Show a snackbar with undo option
        showCompletionSnackbar(task, updatedTask.completionCount == 1)
        
        // Update user score (would normally be in ViewModel)
        updateUserScore(task.points)
    }
    
    private fun completeTaskWithDate(position: Int, completedAt: Timestamp) {
        if (position < 0 || position >= tasks.size) return
        
        val task = tasks[position]
        val updatedTask = task.complete(currentUserId, completedAt)
        
        // Update the task in the list
        tasks[position] = updatedTask
        
        // Update the UI
        taskAdapter.notifyItemChanged(position)
        
        // Show a snackbar with undo option
        showCompletionSnackbar(task, updatedTask.completionCount == 1)
        
        // Update user score (would normally be in ViewModel)
        updateUserScore(task.points)
    }
    
    private fun undoTaskCompletion(position: Int) {
        if (position < 0 || position >= tasks.size) return
        
        val task = tasks[position]
        
        // Only proceed if the task has been completed at least once
        if (!task.isCompleted()) return
        
        val updatedTask = task.undoLastCompletion() ?: return
        
        // Update the task in the list
        tasks[position] = updatedTask
        
        // Update the UI
        taskAdapter.notifyItemChanged(position)
        
        // Show a snackbar
        showUndoCompletionSnackbar(task)
        
        // Update user score (would normally be in ViewModel)
        updateUserScore(-task.points)
    }
    
    private fun showCompletionSnackbar(task: Task, isFirstCompletion: Boolean) {
        val message = if (isFirstCompletion) {
            "Task '${task.title}' completed: +${task.points} points"
        } else {
            "Task '${task.title}' completed again: +${task.points} points"
        }
        
        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
            .setAction("UNDO") {
                // Find the task in the list
                val position = tasks.indexOfFirst { it.id == task.id }
                if (position != -1) {
                    undoTaskCompletion(position)
                }
            }
            .show()
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
    
    private fun updateUI() {
        if (tasks.isEmpty()) {
            showEmptyState()
        } else {
            showTasks()
        }
    }

    private fun showEmptyState() {
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.VISIBLE
        emptyView.text = getString(R.string.tasks_empty_message, "tasks")
    }
    
    private fun showTasks() {
        recyclerView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        
        // Notify the adapter that the data has changed
        taskAdapter.notifyDataSetChanged()
    }
}