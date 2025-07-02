package com.homeostasis.app.ui.groups

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.homeostasis.app.R
import com.homeostasis.app.data.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class InviteDialogFragment : DialogFragment() {

    // Inject the ViewModel
    private val inviteViewModel: InviteViewModel by viewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_invite, null)
        builder.setView(view)

        val inviteLinkTextView = view.findViewById<TextView>(R.id.invite_link_text_view)
        val shareButton = view.findViewById<Button>(R.id.share_button)

        // Disable share button initially
        shareButton.isEnabled = false
        inviteLinkTextView.text = "Loading invite link..." // Initial text

        // Observe the household group ID from the ViewModel
        lifecycleScope.launch {
            inviteViewModel.householdGroupId.collectLatest { householdGroupId ->
                Log.d("InviteDialogFragment", "Observed householdGroupId: $householdGroupId")
                if (householdGroupId != null && householdGroupId.isNotEmpty() && householdGroupId != Constants.DEFAULT_GROUP_ID) {
                    // Generate deep link with the actual group ID
                    val encodedGroupId = Uri.encode(householdGroupId)
                    val deepLink = "homeostasis://invite?groupId=$encodedGroupId"
                    Log.d("InviteDialogFragment", "Generated Deep Link: " + deepLink)
                    inviteLinkTextView.text = deepLink
                    shareButton.isEnabled = true // Enable share button
                } else {
                    // Handle cases where group ID is not available or is the default
                    inviteLinkTextView.text = "Cannot generate invite link. Please join or create a group."
                    shareButton.isEnabled = false // Keep share button disabled
                    Log.w("InviteDialogFragment", "Household Group ID is null, empty, or default. Cannot generate invite link.")
                }
            }
        }

        shareButton.setOnClickListener {
            val deepLink = inviteLinkTextView.text.toString() // Get the current link from the TextView
            if (deepLink.isNotBlank() && deepLink != "Loading invite link..." && deepLink != "Cannot generate invite link. Please join or create a group.") {
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.type = "text/plain"
                shareIntent.putExtra(Intent.EXTRA_TEXT, deepLink)
                startActivity(Intent.createChooser(shareIntent, "Share via"))
            } else {
                Toast.makeText(requireContext(), "Invite link not available yet.", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setPositiveButton("OK", null)
        return builder.create()
    }
}