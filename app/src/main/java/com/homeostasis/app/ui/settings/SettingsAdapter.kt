package com.homeostasis.app.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.homeostasis.app.R
import com.homeostasis.app.ui.groups.InviteDialogFragment

class SettingsAdapter(private val settingsListItems: List<SettingsListItem>, private val onItemClick: (String) -> Unit) :
    RecyclerView.Adapter<SettingsAdapter.SettingsViewHolder>() {

    var fragmentManager: androidx.fragment.app.FragmentManager? = null

    // Define view types
    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_SETTING = 1
    }

    class SettingsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val settingNameTextView: TextView = itemView.findViewById(R.id.setting_name_text_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_setting, parent, false) // Use the same layout for now
        return SettingsViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: SettingsViewHolder, position: Int) {
        when (val item = settingsListItems[position]) {
            is SettingsListItem.Header -> {
                holder.settingNameTextView.text = item.title
                // Style as a header (e.g., bold, larger text, no click listener)
                holder.settingNameTextView.setTypeface(null, android.graphics.Typeface.BOLD)
                holder.settingNameTextView.textSize = 18f // Example size
                holder.itemView.isClickable = false // Headers are not clickable
                holder.itemView.setOnClickListener(null) // Remove click listener
                // Remove padding for header if needed (adjust item_setting.xml or programmatically)
                // For now, let's keep the padding from item_setting.xml
            }
            is SettingsListItem.Setting -> {
                holder.settingNameTextView.text = item.name
                // Style as a regular setting item
                holder.settingNameTextView.setTypeface(null, android.graphics.Typeface.NORMAL)
                holder.settingNameTextView.textSize = 16f // Example size
                holder.itemView.isClickable = true // Setting items are clickable
                holder.itemView.setOnClickListener {
                    // Handle specific actions or call the general onItemClick lambda
                    if (item.name == "Invite Members") {
                        val inviteDialogFragment = InviteDialogFragment()
                        // Ensure fragmentManager is not null before showing dialog
                        fragmentManager?.let {
                            inviteDialogFragment.show(it, "InviteDialogFragment")
                        } ?: run {
                            // Log an error or show a message if fragmentManager is null
                            // This shouldn't happen if setup is correct, but good for debugging
                        }
                    } else {
                        onItemClick(item.name)
                    }
                }
                // Add indentation for setting items if needed (adjust item_setting.xml or programmatically)
                // For now, let's keep the padding from item_setting.xml
            }
        }
    }

    override fun getItemCount() = settingsListItems.size

    override fun getItemViewType(position: Int): Int {
        return when (settingsListItems[position]) {
            is SettingsListItem.Header -> VIEW_TYPE_HEADER
            is SettingsListItem.Setting -> VIEW_TYPE_SETTING
        }
    }
}