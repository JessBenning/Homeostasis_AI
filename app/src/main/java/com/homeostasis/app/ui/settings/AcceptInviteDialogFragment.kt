package com.homeostasis.app.ui.settings

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.homeostasis.app.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AcceptInviteDialogFragment : DialogFragment() {

    private val viewModel: AcceptInviteViewModel by viewModels() // Will create this ViewModel next

    private lateinit var inviteLinkEditText: TextInputEditText
    private lateinit var validationMessageTextView: TextView
    private lateinit var acceptButton: Button

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.dialog_accept_invite, null)
        builder.setView(view)

        inviteLinkEditText = view.findViewById(R.id.invite_link_edit_text)
        validationMessageTextView = view.findViewById(R.id.validation_message_text_view)

        builder.setTitle("Accept Group Invite")
        builder.setPositiveButton("Accept") { dialog, _ ->
            // Handled in onViewCreated to keep dialog open on validation failure
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        val dialog = builder.create()

        // Override the positive button's click listener to prevent dismissal on validation failure
        dialog.setOnShowListener {
            acceptButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            acceptButton.setOnClickListener {
                val inviteLink = inviteLinkEditText.text.toString()
                validationMessageTextView.visibility = View.GONE // Hide previous messages
                viewModel.processInviteLink(inviteLink)
            }
        }

        // Observe the result from the ViewModel
        lifecycleScope.launch {
            viewModel.acceptInviteResult.collect { result ->
                when (result) {
                    is AcceptInviteViewModel.AcceptInviteResult.Success -> {
                        Toast.makeText(requireContext(), "Successfully joined group!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss() // Dismiss on success
                    }
                    is AcceptInviteViewModel.AcceptInviteResult.Error -> {
                        validationMessageTextView.text = "Error: ${result.message}"
                        validationMessageTextView.visibility = View.VISIBLE
                    }
                    is AcceptInviteViewModel.AcceptInviteResult.ValidationFailed -> {
                        validationMessageTextView.text = result.message
                        validationMessageTextView.visibility = View.VISIBLE
                    }
                }
            }
        }

        return dialog
    }

    companion object {
        const val TAG = "AcceptInviteDialogFragment"
    }
}