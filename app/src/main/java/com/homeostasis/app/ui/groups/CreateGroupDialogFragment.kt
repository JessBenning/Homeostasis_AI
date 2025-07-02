package com.homeostasis.app.ui.groups

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater // Keep for onCreateView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.homeostasis.app.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CreateGroupDialogFragment : DialogFragment() {

    private var isFromOnboarding: Boolean = false
    private val viewModel: GroupViewModel by viewModels()

    // Views need to be class members if accessed across onCreateDialog and onViewCreated/onResume
    private lateinit var groupNameEditText: EditText
    private lateinit var createButton: Button
    private lateinit var cancelButton: Button
    private var loadingProgressBar: ProgressBar? = null

    // Store the inflated view to avoid re-inflating if onCreateView is called
    private var dialogCustomView: View? = null


    companion object {
        const val TAG = "CreateGroupDialogFragment"
        const val REQUEST_KEY_ONBOARDING_GROUP_SUCCESS = "onboardingCreateGroupSuccess"
        const val ARG_IS_FROM_ONBOARDING = "isFromOnboarding"
        const val REQUEST_KEY = "createGroupResult"
        const val RESULT_SUCCESS = "createGroupSuccess"

        fun newInstance(isFromOnboarding: Boolean): CreateGroupDialogFragment {
            val args = Bundle()
            args.putBoolean(ARG_IS_FROM_ONBOARDING, isFromOnboarding)
            val fragment = CreateGroupDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isFromOnboarding = arguments?.getBoolean(ARG_IS_FROM_ONBOARDING) ?: false
        Log.d(TAG, "onCreate called, isFromOnboarding: $isFromOnboarding")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Log.d(TAG, "onCreateDialog started")
        val builder = MaterialAlertDialogBuilder(requireContext())
        val inflater = requireActivity().layoutInflater

        // Inflate the view here. This view will be the Dialog's content.
        // It's also technically the "Fragment's view" in this context.
        val view = inflater.inflate(R.layout.dialog_create_group, null)
        dialogCustomView = view // Store for onViewCreated if needed, though we can re-get

        builder.setView(view)

        // Initialize views that are part of the dialog's custom layout
        groupNameEditText = view.findViewById(R.id.group_name_edit_text)
        createButton = view.findViewById(R.id.create_group_button)
        cancelButton = view.findViewById(R.id.cancel_create_group_button)
        // loadingProgressBar = view.findViewById(R.id.loading_progress_bar) // Example
        // loadingProgressBar?.visibility = View.GONE

        // --- Set up Listeners for custom buttons ---
        // These are fine here as they are part of the dialog's view setup
        createButton.setOnClickListener {
            val groupName = groupNameEditText.text.toString().trim()
            if (groupName.isNotEmpty()) {
                loadingProgressBar?.visibility = View.VISIBLE
                createButton.isEnabled = false
                cancelButton.isEnabled = false
                val currentUserId = viewModel.getCurrentUserId()
                if (currentUserId != null) {
                    viewModel.createGroup(groupName)//, currentUserId)
                } else {
                    Toast.makeText(requireContext(), "Error: User not logged in", Toast.LENGTH_SHORT).show()
                    loadingProgressBar?.visibility = View.GONE
                    createButton.isEnabled = true
                    cancelButton.isEnabled = true
                }
            } else {
                groupNameEditText.error = "Group name cannot be empty"
            }
        }

        cancelButton.setOnClickListener {
            dismiss()
        }

        val dialog = builder.create()
        Log.d(TAG, "onCreateDialog finished, dialog created")
        return dialog
    }

    // onCreateView is called by the system even if you primarily use onCreateDialog.
    // If you return null, DialogFragment creates a default FrameLayout.
    // If you return the view you set in builder.setView(), it helps align lifecycles.
    // However, for DialogFragments where the view is set via Dialog.setContentView or Builder.setView,
    // overriding onCreateView might not be strictly necessary just for this.
    // The key is that onViewCreated WILL be called if the Dialog has a view.

    // Let's try WITHOUT overriding onCreateView first, and put observer in onViewCreated.

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called - setting up observers")

        // 'view' here is the one set via builder.setView() in onCreateDialog
        // or the one returned by onCreateView if you override it.

        // This is generally the safest place to start collecting flows tied to the view's lifecycle.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                Log.d(TAG, "repeatOnLifecycle(STARTED) in onViewCreated - collecting createGroupResult")
                viewModel.createGroupResult.collectLatest { result ->
                    if (!isAdded || result == null) {
                        Log.d(TAG, "Collector: isAdded=${isAdded}, result=${result} - returning")
                        return@collectLatest
                    }
                    Log.d(TAG, "Collector: Received result: $result")

                    // Ensure views are still accessible and not null if fragment is being rapidly destroyed/recreated
                    // Though isAdded check helps.
                    loadingProgressBar?.visibility = View.GONE
                    createButton.isEnabled = true
                    cancelButton.isEnabled = true

                    when (result) {
                        is GroupViewModel.CreateGroupResult.Success -> {
                            val groupNameText = groupNameEditText.text.toString().trim()
                            Toast.makeText(requireContext(), "Group '$groupNameText' created successfully", Toast.LENGTH_SHORT).show()
                            handleCreateGroupSuccess()
                        }
                        is GroupViewModel.CreateGroupResult.Error -> {
                            Toast.makeText(requireContext(), "Error creating group: ${result.message}", Toast.LENGTH_LONG).show()
                        }
                        is GroupViewModel.CreateGroupResult.InProgress -> {
                            Log.d(TAG, "Group creation in progress (observed in onViewCreated collector).")
                            loadingProgressBar?.visibility = View.VISIBLE
                            createButton.isEnabled = false
                            cancelButton.isEnabled = false
                        }
                    }
                    viewModel.consumeCreateGroupResult()
                }
            }
        }
    }


    override fun onStart() {
        super.onStart()
        // Optional: Adjust dialog window properties
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        Log.d(TAG, "onStart: Dialog window layout configured.")
    }

    private fun handleCreateGroupSuccess() {
        Log.d(TAG, "handleCreateGroupSuccess called. isFromOnboarding: $isFromOnboarding")
        val resultBundle = bundleOf(RESULT_SUCCESS to true)
        val requestKey = if (isFromOnboarding) {
            REQUEST_KEY_ONBOARDING_GROUP_SUCCESS
        } else {
            REQUEST_KEY
        }
        parentFragmentManager.setFragmentResult(requestKey, resultBundle)
        Log.d(TAG, "Fragment result set on key '$requestKey'.")
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView called.")
        dialogCustomView = null // Clear reference to the custom view
        // No explicit job cancellation needed for the collector launched with
        // viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) as it's automatically handled.
    }
}