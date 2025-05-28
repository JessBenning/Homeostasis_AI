package com.homeostasis.app.ui.settings

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.homeostasis.app.R
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.google.firebase.auth.FirebaseAuth
import com.homeostasis.app.data.model.User
import com.homeostasis.app.data.UserDao
import android.content.Intent
import android.widget.TextView
import android.widget.Button
import android.net.Uri
import android.util.Log
import com.homeostasis.app.viewmodel.HouseholdGroupViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject



class InviteDialogFragment : DialogFragment() {
    private val viewModel: HouseholdGroupViewModel by viewModels()

    @Inject
    lateinit var auth: FirebaseAuth

    @Inject
    lateinit var userDao: UserDao

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_invite, null)
        builder.setView(view)

        val inviteLinkTextView = view.findViewById<TextView>(R.id.invite_link_text_view)
        val shareButton = view.findViewById<Button>(R.id.share_button)

        // Generate deep link
        val householdGroupId = "testHouseholdGroupId" // TODO: Get householdGroupId
        val encodedGroupId = Uri.encode(householdGroupId)
        val deepLink = "homeostasis://invite?groupId=$encodedGroupId"
        Log.d("InviteDialogFragment", "Deep Link: " + deepLink)
        inviteLinkTextView.text = deepLink

        shareButton.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, deepLink)
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        }

        builder.setPositiveButton("OK", null)
        return builder.create()
    }
}