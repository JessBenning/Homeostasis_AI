package com.homeostasis.app.ui.task_history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter // Import ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // Assuming you'll use Glide for image loading
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.homeostasis.app.R
import com.homeostasis.app.ui.task_history.TaskHistoryFeedItem.TaskHistoryItem
import com.homeostasis.app.ui.task_history.TaskHistoryFeedItem.UserScoreSummaryItem
import com.homeostasis.app.data.Converters
import java.io.File

// Remove unused imports like AppDatabase, Task, coroutine related ones if not directly used here for now

// Define your TaskHistoryFeedItem sealed class (if not already in a separate file)
// This should be in its own file, e.g., data/model/TaskHistoryFeedItem.kt
// sealed class TaskHistoryFeedItem {
//     data class UserScoreSummaryItem(
//         val userId: String,
//         val userName: String,
//         val profilePictureUrl: String?,
//         val totalScore: Int,
//         val lastActivityTimestamp: Long // For sorting or display
//     ) : TaskHistoryFeedItem()

//     data class TaskHistoryLogItem(
//         val logId: String, // Unique ID for this log entry
//         val taskId: String,
//         val taskName: String, // Denormalized for direct display
//         val taskDescription: String?, // Denormalized
//         val pointsAwarded: Int,
//         val completedByUserId: String,
//         val completedByUserName: String, // Denormalized
//         val completedAtTimestamp: Long, // Use Long for easier sorting/conversion
//         val completedAtFormatted: String // Pre-formatted date string
//     ) : TaskHistoryFeedItem()
// }

class TaskHistoryAdapter :
    ListAdapter<TaskHistoryFeedItem, RecyclerView.ViewHolder>(TaskHistoryFeedItemDiffCallback()) {

    class UserScoreSummaryViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val userProfilePicture: ImageView = view.findViewById(R.id.user_profile_picture)
            private val userName: TextView = view.findViewById(R.id.user_name)
            private val userScore: TextView = view.findViewById(R.id.user_score)

            fun bind(item: UserScoreSummaryItem) {
                userName.text = item.userName
                userScore.text = itemView.context.getString(R.string.profile_current_score, item.totalScore)

                // Assuming item.userProfilePicSignature is now the file hash or a reliable unique key.
                // And item.userProfilePicLocalPath is the path to the local image file.

                item.userProfilePicLocalPath?.let { path ->
                    val imageFile = File(path)
                    if (imageFile.exists()) {
                        Glide.with(itemView.context)
                            .load(imageFile) // Load the File object directly
                            .signature(ObjectKey(item.userProfilePicSignature ?: System.currentTimeMillis().toString())) // Use hash as signature; fallback to time)
                            .diskCacheStrategy(DiskCacheStrategy.DATA) // Cache the decoded image data. DATA is good when transformations (like circleCrop) are applied.
                            // Or DiskCacheStrategy.RESOURCE if you want to cache the transformed resource.
                            // Or DiskCacheStrategy.AUTOMATIC.
                            .skipMemoryCache(false) // Allow Glide to use its memory cache.
                            .placeholder(R.drawable.ic_default_profile)
                            .error(R.drawable.ic_profile_load_error) // Shown if file exists but Glide fails to load/decode
                            .circleCrop() // Apply transformation
                            .into(userProfilePicture)
                    } else {
                        // The path was provided, but the file doesn't exist locally.
                        // This could happen if the local cache was cleared or the file was manually deleted.
                        Glide.with(itemView.context)
                            .load(R.drawable.ic_profile_load_error) // Show an error or specific "file missing" placeholder
                            .circleCrop()
                            .into(userProfilePicture)
                        // Optionally, log this situation:
                        // Log.w("UserScoreSummaryVH", "Profile picture file missing at path: $path for user ${item.userName}")
                    }
                } ?: run {
                    // Fallback if userProfilePicLocalPath is null (meaning no profile picture is set for the user)
                    Glide.with(itemView.context)
                        .load(R.drawable.ic_default_profile) // Load default placeholder
                        .circleCrop()
                        .into(userProfilePicture)
                }
            }
        }




    class TaskHistoryLogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val taskName: TextView = view.findViewById(R.id.log_item_task_name)
        private val points: TextView = view.findViewById(R.id.log_item_points)
        private val completedBy: TextView = view.findViewById(R.id.log_item_completed_by)
        private val completedDate: TextView = view.findViewById(R.id.log_item_completed_date)
        private val completedByUserProfilePicture: ImageView =
            view.findViewById(R.id.log_item_completed_by_user_profile_picture)

        fun bind(item: TaskHistoryItem) {
            taskName.text = item.taskTitle
            points.text = itemView.context.getString(R.string.task_history_points, item.points)
            completedBy.text =
                itemView.context.getString(R.string.task_history_completed_by, item.completedByUserName)
            // Assuming item.completedAt is a com.google.firebase.Timestamp
            completedDate.text = Converters.formatTimestampToString(item.completedAt)


            // Ensure completedByUserProfilePicLocalPath and completedByUserProfilePicSignature are in your TaskHistoryItem
            item.completedByUserProfilePicLocalPath?.let { path ->
                Glide.with(itemView.context)
                    .load(path) // Load from the local file path
                    .signature(
                        ObjectKey(
                            item.completedByUserProfilePicSignature ?: System.currentTimeMillis()
                                .toString()
                        )
                    ) // Add signature
                    .diskCacheStrategy(DiskCacheStrategy.NONE) // Do not cache to disk based on path alone
                    .skipMemoryCache(true) // Do not cache in memory based on path alone
                    .placeholder(R.drawable.ic_default_profile)
                    .error(R.drawable.ic_profile_load_error)
                    .circleCrop()
                    .into(completedByUserProfilePicture)
            } ?: run {
                // Fallback if local path is null
                Glide.with(itemView.context)
                    .load(R.drawable.ic_default_profile)
                    .circleCrop()
                    .into(completedByUserProfilePicture)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) { // Use getItem(position)
            is UserScoreSummaryItem -> VIEW_TYPE_USER_SCORE_SUMMARY
            is TaskHistoryItem -> VIEW_TYPE_TASK_HISTORY_LOG
            // null can happen if the list is being updated, handle gracefully if needed,
            // though ListAdapter often handles this timing.
            // Consider if you need a null check if getItem(position) could be null during diffing.
            else -> throw IllegalArgumentException("Unknown view type at position $position")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER_SCORE_SUMMARY -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.user_score_item, parent, false) // Ensure this XML is correct
                UserScoreSummaryViewHolder(view)
            }
            VIEW_TYPE_TASK_HISTORY_LOG -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_task_history, parent, false) // Ensure this XML is correct
                TaskHistoryLogViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position) // Use getItem(position)
        when (holder) {
            is UserScoreSummaryViewHolder -> {
                if (item is UserScoreSummaryItem) holder.bind(item)
            }
            is TaskHistoryLogViewHolder -> {
                if (item is TaskHistoryItem) holder.bind(item)
            }
        }
    }

    // REMOVE: getItemCount() - ListAdapter handles this
    // REMOVE: updateData() - Use submitList() from Fragment/ViewModel
    // REMOVE: historyItems property

    // CoroutineScope: If you need to launch coroutines from the adapter (e.g., for complex calculations
    // not related to view binding, or click listeners that do async work), you still might keep this.
    // However, for typical Glide/image loading or simple data binding, it's often not needed directly in onBindViewHolder.
    // If you do keep it, ensure it's cancelled appropriately (e.g., via a public method called from Fragment's onDestroyView).
    // For now, let's assume it's not strictly necessary for basic binding.
    // private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    // public fun cancelScope() { adapterScope.cancel() }


    companion object {
        private const val VIEW_TYPE_USER_SCORE_SUMMARY = 1
        private const val VIEW_TYPE_TASK_HISTORY_LOG = 2
    }
}

// DiffUtil.ItemCallback implementation
class TaskHistoryFeedItemDiffCallback : DiffUtil.ItemCallback<TaskHistoryFeedItem>() {
    override fun areItemsTheSame(oldItem: TaskHistoryFeedItem, newItem: TaskHistoryFeedItem): Boolean {
        // Check if items represent the same logical entity (e.g., by unique ID)
        return when {
            oldItem is UserScoreSummaryItem && newItem is UserScoreSummaryItem -> oldItem.userId == newItem.userId
            oldItem is TaskHistoryItem && newItem is TaskHistoryItem -> oldItem.historyId == newItem.historyId
            else -> false // Different types are never the same item
        }
    }

    override fun areContentsTheSame(oldItem: TaskHistoryFeedItem, newItem: TaskHistoryFeedItem): Boolean {
        // Check if the content of the items is the same (all fields)
        // This is called only if areItemsTheSame returns true.
        return oldItem == newItem // Relies on data classes' generated equals()
    }
}