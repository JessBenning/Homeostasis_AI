package com.homeostasis.app.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.homeostasis.app.R

class SettingsAdapter(private val settingsOptions: List<String>, private val onItemClick: (String) -> Unit) :
    RecyclerView.Adapter<SettingsAdapter.SettingsViewHolder>() {

    var fragmentManager: androidx.fragment.app.FragmentManager? = null

    class SettingsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val settingNameTextView: TextView = itemView.findViewById(R.id.setting_name_text_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_setting, parent, false)
        return SettingsViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: SettingsViewHolder, position: Int) {
        val settingName = settingsOptions[position]
        holder.settingNameTextView.text = settingName
        holder.itemView.setOnClickListener {
            if (settingName == "Invite Members") {
                val inviteDialogFragment = InviteDialogFragment()
                inviteDialogFragment.show(fragmentManager!!, "InviteDialogFragment")
            } else {
                onItemClick(settingName)
            }
        }
    }

    override fun getItemCount() = settingsOptions.size
}