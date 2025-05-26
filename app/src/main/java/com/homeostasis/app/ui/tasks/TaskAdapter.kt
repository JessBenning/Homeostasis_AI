package com.homeostasis.app.ui.tasks

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
    private val tasks: List<Task>,
    private val onTaskClickListener: OnTaskClickListener
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    interface OnTaskClickListener {
        fun onTaskClick(task: Task)
        fun onCompletionDateClick(task: Task, position: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        Log.d("TaskAdapter", "onCreateViewHolder called for viewType: $viewType")
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        val viewHolder = TaskViewHolder(view)
        Log.d("TaskAdapter", "onCreateViewHolder created: ${viewHolder.hashCode()}") // Log instance
        return viewHolder
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        Log.d("TaskAdapter", "onBindViewHolder called for position: $position, holder: ${holder.hashCode()}")
        if (position >= tasks.size || position < 0) {
            Log.e("TaskAdapter", "!!!! CRITICAL: onBindViewHolder attempting to bind for position $position, but tasks.size is ${tasks.size}. This will likely crash or lead to inconsistency.")
            // You might even throw an exception here during debug to catch this state earlier:
            // throw IndexOutOfBoundsException("onBindViewHolder: position $position is out of bounds for tasks list size ${tasks.size}")
            return // Avoid further processing if out of bounds
        }
        val task = tasks[position]
        Log.d("TaskAdapter", "  Binding task: id=${task.id}, title='${task.title}' to holder ${holder.hashCode()}")
        holder.bind(task, position)
    }

//    override fun getItemCount(): Int {
//        return tasks.size;
//    }

    override fun getItemCount(): Int {
        val currentSize = tasks.size
        Log.d("TaskAdapter", "getItemCount() called. Current tasks.size: $currentSize")

        // --- TEMPORARY DEBUGGING ---
        // This can be verbose, use with caution and remove after debugging.
        if (currentSize > 0 && currentSize < 5) { // Log only for small lists to avoid spam
            tasks.forEachIndexed { index, task ->
                Log.d("TaskAdapterDebug", "  Task at index $index: id=${task.id}, title='${task.title}', isCompleted=${task.isCompleted()}")
                // Add any other relevant properties of 'Task' you want to check
            }
        } else if (currentSize == 0) {
            Log.w("TaskAdapterDebug", "getItemCount(): tasks list is empty.")
        }
        // --- END TEMPORARY DEBUGGING ---

        return currentSize
    }
    
    /**
     * Resets the appearance of all task items to their default state.
     * Called when leaving the fragment to ensure a clean state when returning.
     */
    fun resetItemAppearance() {
        notifyDataSetChanged()
    }

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: com.google.android.material.card.MaterialCardView = itemView.findViewById(R.id.task_container)
        private val titleTextView: TextView = itemView.findViewById(R.id.task_title)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.task_description)
        private val categoryTextView: TextView = itemView.findViewById(R.id.task_category)
        private val pointsTextView: TextView = itemView.findViewById(R.id.task_points)
        private val lastDoneTextView: TextView = itemView.findViewById(R.id.task_last_done)
        private val completionCounterTextView: TextView = itemView.findViewById(R.id.completion_counter)

        fun bind(task: Task, position: Int) {
            titleTextView.text = task.title
            descriptionTextView.text = task.description
            categoryTextView.text = task.categoryId // Using categoryId as name for now
            pointsTextView.text = "${task.points} pts"

            
            // Set completion status
            updateCompletionStatus(task)
            
            // Set click listeners
            itemView.setOnClickListener {
                onTaskClickListener.onTaskClick(task)
            }
            
            lastDoneTextView.setOnClickListener {
                onTaskClickListener.onCompletionDateClick(task, position)
            }
        }
        
        private fun updateCompletionStatus(task: Task) {
            // Update the task item background based on completion status
            container.isActivated = task.isCompleted()
            
            // Update the last done text
            if (task.isCompleted() && task.lastCompletedAt != null) {
                val dateFormat = DateFormat.getDateFormat(itemView.context)
                val formattedDate = dateFormat.format(Date(task.lastCompletedAt!!.seconds * 1000))
                lastDoneTextView.text = itemView.context.getString(
                    R.string.task_last_done,
                    formattedDate,
                    task.lastCompletedBy.takeIf { it.isNotEmpty() } ?: "Unknown"
                )
            } else {
                lastDoneTextView.text = itemView.context.getString(R.string.task_never_done)
            }
            
            // Update the completion counter
            if (task.completionCount == 1) {
                completionCounterTextView.visibility = View.VISIBLE
                completionCounterTextView.text = "\u2713"
            }
            else if (task.completionCount > 1) {
                completionCounterTextView.visibility = View.VISIBLE
                completionCounterTextView.text = "\u2713"+" x "+task.completionCount.toString()
            }
            else{
                completionCounterTextView.visibility = View.GONE
            }
        }
    }
}