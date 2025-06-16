package com.homeostasis.app.ui.groups

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.homeostasis.app.R
import com.homeostasis.app.data.model.Group
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID

@AndroidEntryPoint
class CreateGroupDialogFragment : DialogFragment() {

    private val viewModel: GroupViewModel by viewModels() // Assuming a GroupViewModel exists
    private lateinit var groupNameEditText: TextInputEditText
    private lateinit var createButton: MaterialButton
    private lateinit var cancelButton: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_create_group, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        groupNameEditText = view.findViewById(R.id.group_name_edit_text)
        createButton = view.findViewById(R.id.create_group_button)
        cancelButton = view.findViewById(R.id.cancel_create_group_button)

        createButton.setOnClickListener {
            val groupName = groupNameEditText.text.toString().trim()
            if (groupName.isNotEmpty()) {
                lifecycleScope.launch {
                    val currentUserId = viewModel.getCurrentUserId() // Assuming ViewModel has this method
                    if (currentUserId != null) {
                        viewModel.createGroup(groupName, currentUserId)
                        Toast.makeText(requireContext(), "Group created successfully", Toast.LENGTH_SHORT).show()
                        dismiss()
                    } else {
                        Toast.makeText(requireContext(), "Error: User not logged in", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                groupNameEditText.error = "Group name cannot be empty"
            }
        }

        cancelButton.setOnClickListener {
            dismiss()
        }
    }

    companion object {
        const val TAG = "CreateGroupDialogFragment"
    }
}