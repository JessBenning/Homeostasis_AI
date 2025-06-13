package com.homeostasis.app.ui.tasks

// import kotlinx.coroutines.runBlocking // Not used
// import android.text.format.DateFormat // Not directly used here, but keep if DisplayTask or model uses it indirectly
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
// import androidx.constraintlayout.widget.ConstraintLayout // Not directly used in this snippet
import androidx.recyclerview.widget.RecyclerView
import com.homeostasis.app.R
import com.homeostasis.app.data.model.Task // Still needed for the Task object within DisplayTask
// import java.util.Date // Not directly used here

/**
 * Adapter for displaying tasks (as DisplayTask objects) in a RecyclerView.
 * Manages session-based completion counts.
 */
class TaskAdapter(
    // The primary data source is now List<DisplayTask>
    var displayTasks: List<DisplayTask>,
    private val onTaskClickListener: OnTaskClickListener,
    private val taskSwipeCallback: TaskSwipeCallback
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private val TAG = "TaskAdapter"

    // Map to store completion counts for the current session
    // Key: Task ID (String), Value: Completion Count (Int)
    private val sessionCompletionCounts = mutableMapOf<String, Int>()

    interface OnTaskClickListener {
        fun onTaskClick(task: Task) // When the whole item is clicked (not for completion)
        fun onCompletionDateClick(task: Task, position: Int) // For "Last done" click
        // Updated: isNewSessionCompletion indicates if this is the first completion in this session for this task.
        fun onTaskMarkedComplete(task: Task, isNewSessionCompletion: Boolean)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        // Log.d(TAG, "onCreateViewHolder called for viewType: $viewType") // Keep for debugging if useful
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        if (position < 0 || position >= displayTasks.size) {
            Log.e(TAG, "onBindViewHolder: Invalid position $position, displayTasks size is ${displayTasks.size}")
            return
        }
        val displayTask = displayTasks[position]
        val task = displayTask.task // Get the underlying Task object

        // Get the session completion count for this task
        val currentSessionCompletionCount = sessionCompletionCounts.getOrDefault(task.id, 0)
        holder.bind(displayTask, currentSessionCompletionCount, onTaskClickListener, position)
    }

    override fun getItemCount(): Int {
        return displayTasks.size
    }

    /**
     * Resets the visual appearance of all task items.
     * This is generic and might not be needed if session counts are handled explicitly.
     */
    fun resetItemAppearance() {
        Log.d(TAG, "resetItemAppearance called, redrawing all items.")
        notifyDataSetChanged()
    }

    /**
     * Hides all badges. Typically, this means resetting session completion counts and redrawing.
     */
    fun hideAllBadges() {
        Log.d(TAG, "hideAllBadges called, clearing session counts and redrawing.")
        sessionCompletionCounts.clear() // Assuming "badges" are primarily the session counters
        notifyDataSetChanged()
    }

    /**
     * Updates the list of DisplayTask objects and refreshes the RecyclerView.
     * Optionally resets session completion counts if specified.
     */
    fun setTasks(newDisplayTasks: List<DisplayTask>, resetSessionCounts: Boolean = false) {
        Log.d(TAG, "setTasks called with ${newDisplayTasks.size} items. Reset session counts: $resetSessionCounts")
        this.displayTasks = newDisplayTasks
        if (resetSessionCounts) {
            sessionCompletionCounts.clear()
            Log.d(TAG, "Session counts cleared due to setTasks with reset flag.")
        }
        notifyDataSetChanged() // Consider DiffUtil for better performance with large lists
    }

    // --- Session Completion Count Management ---

    fun getSessionCompletionCount(taskId: String): Int {
        return sessionCompletionCounts.getOrDefault(taskId, 0)
    }

    /**
     * Increments the session completion count for a given task.
     * @return true if this was the first completion in the session, false otherwise.
     */
    fun incrementSessionCompletionCount(taskId: String): Boolean {
        val currentCount = sessionCompletionCounts.getOrDefault(taskId, 0)
        sessionCompletionCounts[taskId] = currentCount + 1
        Log.d(TAG, "Incremented session count for $taskId to ${sessionCompletionCounts[taskId]}")
        // No direct notifyItemChanged here; the caller (Fragment) should decide when to update UI.
        return currentCount == 0 // Returns true if it *was* 0 before incrementing
    }

    /**
     * Decrements the session completion count for a given task.
     * @return true if count was successfully decremented (was > 0), false otherwise.
     */
    fun decrementSessionCompletionCount(taskId: String): Boolean {
        val currentCount = sessionCompletionCounts.getOrDefault(taskId, 0)
        if (currentCount > 0) {
            sessionCompletionCounts[taskId] = currentCount - 1
            Log.d(TAG, "Decremented session count for $taskId to ${sessionCompletionCounts[taskId]}")
            // No direct notifyItemChanged here.
            return true
        }
        Log.d(TAG, "Session count for $taskId is already 0, cannot decrement.")
        return false
    }

    /**
     * Resets all session completion counts and redraws all items.
     * Typically called when leaving a screen or starting a new "session".
     */
    fun resetAllSessionCompletionCounts() {
        if (sessionCompletionCounts.isNotEmpty()) {
            Log.d(TAG, "resetAllSessionCompletionCounts called. Clearing ${sessionCompletionCounts.size} counts.")
            sessionCompletionCounts.clear()
            notifyDataSetChanged() // Redraw all items to remove checkmarks/counts
        } else {
            Log.d(TAG, "resetAllSessionCompletionCounts called, but no counts to clear.")
        }
    }

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // private val container: com.google.android.material.card.MaterialCardView = itemView.findViewById(R.id.task_container) // Keep if needed
        private val titleTextView: TextView = itemView.findViewById(R.id.task_title)
//        private val categoryTextView: TextView = itemView.findViewById(R.id.task_category)
        private val pointsTextView: TextView = itemView.findViewById(R.id.task_points)
        private val lastDoneTextView: TextView = itemView.findViewById(R.id.task_last_done)
        private val completionCounterTextView: TextView = itemView.findViewById(R.id.completion_counter)
        private val optionsMenuButton: ImageButton = itemView.findViewById(R.id.task_options_menu)

        fun bind(
            displayTask: DisplayTask,
            sessionCompletionCount: Int,
            listener: OnTaskClickListener,
            currentPosition: Int // Use adapterPosition for safety
        ) {
            val task = displayTask.task // Extract the actual Task object

            titleTextView.text = task.title
          //  categoryTextView.text = task.categoryId // Assuming categoryId is what you want to show
            pointsTextView.text = "${task.points} pts"

            // Use formatted values from DisplayTask
            val lastDoneText = if (displayTask.lastCompletedByDisplay != null && displayTask.lastCompletedDateDisplay != null) {
                "Last done: ${displayTask.lastCompletedByDisplay} on ${displayTask.lastCompletedDateDisplay}"
            } else {
                "Last done: N/A"
            }
            // Log.d(TAG, "ViewHolder bind for '${task.title}': LastDone='$lastDoneText', SessionCount=$sessionCompletionCount")
            lastDoneTextView.text = lastDoneText
            lastDoneTextView.visibility = View.VISIBLE // Always visible, text shows N/A if no data


            // Update the session completion counter TextView
            if (sessionCompletionCount == 1) {
                completionCounterTextView.visibility = View.VISIBLE
                completionCounterTextView.text = "\u2713" // Checkmark
            } else if (sessionCompletionCount > 1) {
                completionCounterTextView.visibility = View.VISIBLE
                completionCounterTextView.text = "\u2713 x $sessionCompletionCount"
            } else {
                completionCounterTextView.visibility = View.GONE
            }

            itemView.isClickable = true
            itemView.setOnClickListener {
                if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener

                if (!taskSwipeCallback.isActionsShownForAnyItem()) {
                    // Determine if this click represents a *new* completion for the session
                    val isNewSessionCompletion = getSessionCompletionCount(task.id) == 0
                    // Inform the fragment/listener about the completion attempt
                    listener.onTaskMarkedComplete(task, isNewSessionCompletion)
                    // The fragment will then call adapter.incrementSessionCompletionCount and notifyItemChanged if successful
                } else {
                    taskSwipeCallback.hideCurrentlyShownActions() // Close swipe actions
                }
            }

            lastDoneTextView.setOnClickListener {
                if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                listener.onCompletionDateClick(task, currentPosition)
            }

            optionsMenuButton.setOnClickListener {
                if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                taskSwipeCallback.toggleActionsForItem(currentPosition)
            }
        }
    }
}