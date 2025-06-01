package com.homeostasis.app.ui.tasks
import kotlinx.coroutines.runBlocking
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.homeostasis.app.R
import com.homeostasis.app.data.model.Task
import java.util.Date

/**
 * Adapter for displaying tasks in a RecyclerView.
 */
class TaskAdapter(
    private var tasks: List<Task>, // Your list of Task objects from the ViewModel
    private val onTaskClickListener: OnTaskClickListener
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    // Map to store completion counts for the current session
    // Key: Task ID (String), Value: Completion Count (Int)
    private val sessionCompletionCounts = mutableMapOf<String, Int>()

    interface OnTaskClickListener {
        fun onTaskClick(task: Task)
        fun onCompletionDateClick(task: Task, position: Int) // Consider if this is still needed or how it relates to completion
        fun onTaskMarkedComplete(task: Task) // New listener method for when a task is "completed"
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        Log.d("TaskAdapter", "onCreateViewHolder called for viewType: $viewType")
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        // Pass the adapter instance to the ViewHolder if it needs to trigger updates
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        // ... (boundary checks as you have) ...
        val task = tasks[position]
        // Get the completion count for this task from the map, default to 0 if not found
        val currentCompletionCount = sessionCompletionCounts.getOrDefault(task.id, 0)
        holder.bind(task, currentCompletionCount, onTaskClickListener) // Pass listener
    }


    override fun getItemCount(): Int {
        val currentSize = tasks.size
        //Log.d("TaskAdapter", "getItemCount() called. Current tasks.size: $currentSize")

        return currentSize
    }
    
    /**
     * Resets the appearance of all task items to their default state.
     * Called when leaving the fragment to ensure a clean state when returning.
     */
    fun resetItemAppearance() {
        notifyDataSetChanged()
    }

    fun hideAllBadges() {
        notifyDataSetChanged()
    }

    fun setTasks(tasks: List<Task>) {
        this.tasks = tasks
        notifyDataSetChanged()
    }
    
    //        private fun hasTaskHistory(task: Task): Boolean {
    //            return runBlocking {
    //                this@TaskAdapter.getTaskHistoryByTaskId(task.id).isNotEmpty()
    //            }
    //        }

    // Method to be called when a task is marked as complete by the user
    fun incrementCompletionCount(taskId: String) {
        val currentCount = sessionCompletionCounts.getOrDefault(taskId, 0)
        sessionCompletionCounts[taskId] = currentCount + 1

        // Find the position of the task to notify item change for UI update
        val position = tasks.indexOfFirst { it.id == taskId }
        if (position != -1) {
            notifyItemChanged(position)
        }
    }

    // Method to be called when a task is marked as complete by the user
    fun decrementCompletionCount(taskId: String): Boolean {
        val currentCount = sessionCompletionCounts.getOrDefault(taskId, 0)
        if (currentCount > 0)
            sessionCompletionCounts[taskId] = currentCount -1
        else return false

        // Find the position of the task to notify item change for UI update
        val position = tasks.indexOfFirst { it.id == taskId }
        if (position != -1) {
            notifyItemChanged(position)
        }
        return true
    }

    fun resetAllCompletionCounts() {
        sessionCompletionCounts.clear()
        notifyDataSetChanged() // Redraw all items
    }


    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: com.google.android.material.card.MaterialCardView = itemView.findViewById(R.id.task_container)
        private val titleTextView: TextView = itemView.findViewById(R.id.task_title)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.task_description)
        private val categoryTextView: TextView = itemView.findViewById(R.id.task_category)
        private val pointsTextView: TextView = itemView.findViewById(R.id.task_points)
        private val lastDoneTextView: TextView = itemView.findViewById(R.id.task_last_done)
        private val completionCounterTextView: TextView = itemView.findViewById(R.id.completion_counter)
        private var completionCount: Int = 0

    fun bind(
            task: Task,
            completionCount: Int, // Receive the count from onBindViewHolder
            listener: OnTaskClickListener
        ) {
            titleTextView.text = task.title
            descriptionTextView.text = task.description
            categoryTextView.text = task.categoryId
            pointsTextView.text = "${task.points} pts"



            Log.d("TaskAdapter","counter= ${completionCount}, title='${task.title}'")

            // Update the completion counter
            if (completionCount == 1) {
                completionCounterTextView.visibility = View.VISIBLE
                completionCounterTextView.text = "\u2713"
            }
            else if (completionCount > 1) {
                completionCounterTextView.visibility = View.VISIBLE
                completionCounterTextView.text = "\u2713"+" x "+completionCount.toString()
            }
            else{
                completionCounterTextView.visibility = View.GONE
            }

            // Set click listeners
            itemView.setOnClickListener {
                // listener.onTaskClick(task) // Remove or call this too if needed
                listener.onTaskMarkedComplete(task) // THIS WILL TRIGGER THE COUNT INCREMENT
            }

            lastDoneTextView.setOnClickListener {
                onTaskClickListener.onCompletionDateClick(task, position)
            }
            
        }
    

    }
}