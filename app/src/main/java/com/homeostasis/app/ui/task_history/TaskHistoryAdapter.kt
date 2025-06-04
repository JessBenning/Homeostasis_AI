package com.homeostasis.app.ui.task_history

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.homeostasis.app.data.model.TaskHistory
import com.homeostasis.app.data.model.UserScore
import java.lang.IllegalArgumentException
import android.view.LayoutInflater
import com.homeostasis.app.R
import android.widget.ImageView

import com.homeostasis.app.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob // Import SupervisorJob
import kotlinx.coroutines.cancel // Import cancel
import kotlinx.coroutines.launch // Import launch
import kotlinx.coroutines.withContext
import com.homeostasis.app.data.model.Task

class TaskHistoryAdapter(val dataSet: MutableList<Any>, private val db: AppDatabase) :
RecyclerView.Adapter<RecyclerView.ViewHolder>() {


    // Define a CoroutineScope for the adapter
    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    class UserScoreViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userProfilePicture: android.widget.ImageView = view.findViewById(com.homeostasis.app.R.id.user_profile_picture)
        val userName: android.widget.TextView = view.findViewById(com.homeostasis.app.R.id.user_name)
        val userScore: android.widget.TextView = view.findViewById(com.homeostasis.app.R.id.user_score)
    }

    class TaskHistoryItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val taskName: android.widget.TextView = view.findViewById(com.homeostasis.app.R.id.task_name)
        val taskDescription: android.widget.TextView = view.findViewById(com.homeostasis.app.R.id.task_description)
        val taskCompletedDate: android.widget.TextView = view.findViewById(com.homeostasis.app.R.id.task_completed_date)
    }

    override fun getItemViewType(position: Int): Int {
        return when (dataSet[position]) {
            is UserScore -> VIEW_TYPE_USER_SCORE
            is TaskHistory -> VIEW_TYPE_TASK_HISTORY_ITEM
            else -> throw IllegalArgumentException("Invalid data type")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER_SCORE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(com.homeostasis.app.R.layout.user_score_item, parent, false)
                UserScoreViewHolder(view)
            }
            VIEW_TYPE_TASK_HISTORY_ITEM -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(com.homeostasis.app.R.layout.task_history_item, parent, false)
                TaskHistoryItemViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            VIEW_TYPE_USER_SCORE -> {
                val userScore = dataSet[position] as UserScore
                val userScoreViewHolder = holder as UserScoreViewHolder
                userScoreViewHolder.userName.text = userScore.userName
                userScoreViewHolder.userScore.text = "Score: ${userScore.score}"
            }

            VIEW_TYPE_TASK_HISTORY_ITEM -> {
                val taskHistoryItem = dataSet[position] as TaskHistory
                val taskHistoryItemViewHolder = holder as TaskHistoryItemViewHolder

                // Use the defined adapterScope to launch the coroutine
                adapterScope.launch {
                    // Switch to a background thread for database operations
                    val task = withContext(Dispatchers.IO) {
                        db.taskDao().getTaskById(taskHistoryItem.taskId)
                    }

                    // Update UI on the main thread
                    if (task != null) {
                        taskHistoryItemViewHolder.taskName.text = task.title
                        taskHistoryItemViewHolder.taskDescription.text = task.description
                        taskHistoryItemViewHolder.taskCompletedDate.text = taskHistoryItem.completedAt.toString()
                    } else {
                        taskHistoryItemViewHolder.taskName.text = "Task not found"
                        taskHistoryItemViewHolder.taskDescription.text = "Task not found"
                        taskHistoryItemViewHolder.taskCompletedDate.text = taskHistoryItem.completedAt.toString()
                    }
                }
            }
        }
    }

    override fun getItemCount() = dataSet.size

    // It's good practice to cancel the scope when the adapter is no longer needed.
    // However, RecyclerView.Adapter doesn't have a direct lifecycle callback for this.
    // If this adapter is tied to a Fragment or Activity lifecycle,
    // you should cancel the scope from there.
    // For example, in a Fragment's onDestroyView():
    // fun onDestroyView() {
    //     super.onDestroyView()
    //     adapter.cancelScope() // You'd need to add a public method to your adapter
    // }
    //
    // public fun cancelScope() {
    //     adapterScope.cancel()
    // }


    companion object {
        private const val VIEW_TYPE_USER_SCORE = 1
        private const val VIEW_TYPE_TASK_HISTORY_ITEM = 2
    }
}