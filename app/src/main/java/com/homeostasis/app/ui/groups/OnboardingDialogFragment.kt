package com.homeostasis.app.ui.groups

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.os.bundleOf // Keep for potential future use if CreateGroup becomes a nav destination
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.homeostasis.app.R
import com.homeostasis.app.ui.auth.AuthViewModel

// Make sure these imports are correct and the classes exist with the defined constants
import com.homeostasis.app.ui.groups.AcceptInviteDialogFragment
import com.homeostasis.app.ui.groups.CreateGroupDialogFragment

class OnboardingDialogFragment : DialogFragment() {

    private val authViewModel: AuthViewModel by activityViewModels()

    companion object {
        const val TAG = "OnboardingDialogFragment"
        // No newInstance needed if navigated to via NavComponent and doesn't require args itself.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set up listeners for results from child dialogs.
        // This can be done in onCreate or onViewCreated. onCreate is slightly earlier.

        // Listener for results from AcceptInviteDialogFragment
        setFragmentResultListener(AcceptInviteDialogFragment.REQUEST_KEY) { requestKey, bundle ->
            Log.d(TAG, "Received result from AcceptInviteDialogFragment. RequestKey: $requestKey")
            val success = bundle.getBoolean(AcceptInviteDialogFragment.RESULT_SUCCESS)
            // val wasFromOnboarding = bundle.getBoolean(AcceptInviteDialogFragment.RESULT_WAS_FROM_ONBOARDING)
            // We already know it's from onboarding in this context.

            if (success) {
                Log.i(TAG, "Invite accepted successfully. Navigating to tasks.")
                navigateToTasksAndDismiss()
            } else {
                Log.i(TAG, "Invite dialog dismissed or invite failed.")
                // User remains on OnboardingDialogFragment.
                // Consider showing a Toast or allowing another attempt if desired.
            }
        }

        // Listener for results from CreateGroupDialogFragment
        // Ensure CreateGroupDialogFragment defines REQUEST_KEY and RESULT_SUCCESS.
        setFragmentResultListener(CreateGroupDialogFragment.REQUEST_KEY) { requestKey, bundle ->
            Log.d(TAG, "Received result from CreateGroupDialogFragment. RequestKey: $requestKey")
            val success = bundle.getBoolean(CreateGroupDialogFragment.RESULT_SUCCESS)
            // val wasFromOnboarding = bundle.getBoolean(CreateGroupDialogFragment.RESULT_WAS_FROM_ONBOARDING)

            if (success) {
                Log.i(TAG, "Group created successfully. Navigating to tasks.")
                navigateToTasksAndDismiss()
            } else {
                Log.i(TAG, "Create group dialog dismissed or group creation failed.")
                // User remains on OnboardingDialogFragment.
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false // User must make a choice or explicitly logout.
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.onboarding_dialog_title)) // Use string resources
            .setMessage(getString(R.string.onboarding_dialog_message)) // Use string resources
            .setPositiveButton(getString(R.string.onboarding_action_accept_invite)) { _, _ -> // Use string resources
                AcceptInviteDialogFragment.newInstance(isFromOnboarding = true)
                    .show(parentFragmentManager, AcceptInviteDialogFragment.TAG)
                // OnboardingDialogFragment remains open, awaiting result from AcceptInviteDialogFragment.
            }
            .setNegativeButton(getString(R.string.onboarding_action_create_group)) { _, _ -> // Use string resources
                CreateGroupDialogFragment.newInstance(isFromOnboarding = true)
                    .show(parentFragmentManager, CreateGroupDialogFragment.TAG)
                // OnboardingDialogFragment remains open, awaiting result from CreateGroupDialogFragment.
            }
            .setNeutralButton(getString(R.string.action_logout)) { _, _ -> // Use string resources
                handleLogout()
            }
            .create()
    }

    private fun handleLogout() {
        authViewModel.signOut() // AuthViewModel should ideally handle navigation changes on sign out.
        // Fallback navigation if AuthViewModel doesn't handle it or for explicit control:
        try {
            // Attempt to navigate to the authentication screen.
            // Ensure R.id.navigation_auth is a valid destination in your navigation graph,
            // and it's appropriate to navigate there directly from onboarding.
            if (findNavController().currentDestination?.id == R.id.onboardingDialogFragment) {
                findNavController().navigate(R.id.navigation_auth) // Adjust if this is not the correct auth destination
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Error navigating to auth screen on logout: ${e.message}", e)
            // As a last resort, just dismiss this dialog. The underlying activity/fragment
            // should react to the signOut event from AuthViewModel.
        }
        dismiss() // Dismiss OnboardingDialogFragment after initiating logout and navigation.
    }

    private fun navigateToTasksAndDismiss() {
        Log.i(TAG, "Navigating to tasks and dismissing OnboardingDialogFragment.")
        try {
            // Ensure R.id.action_onboardingDialogFragment_to_navigation_tasks is a valid action
            // in your nav_graph.xml that originates from the OnboardingDialogFragment's destination
            // and leads to your main tasks screen.
            if (findNavController().currentDestination?.id == R.id.onboardingDialogFragment) {
                findNavController().navigate(
                    R.id.action_auth_to_tasks,// <<<< IMPORTANT: Use an ACTION_ ID from onboarding
                    null,
                    NavOptions.Builder()
                        .setPopUpTo(R.id.onboardingDialogFragment, true) // Pop this dialog off the stack
                        .build()
                )
            }
        } catch (e: Exception) { // Catch broader exceptions like IllegalArgumentException
            Log.e(TAG, "Error navigating to tasks: ${e.message}", e)
            // If navigation fails, at least dismiss this dialog to prevent getting stuck.
            dismiss()
        }
    }

    // REMOVED onViewCreated content related to findViewById as dialog buttons are handled in onCreateDialog.
    // The FragmentResultListeners are now in onCreate.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // onViewCreated is called after onCreateView and before onStart.
        // If you had any view-specific setup that wasn't related to the
        // AlertDialog's own buttons, it would go here.
        // For this dialog, most logic is in onCreate and onCreateDialog.
        Log.d(TAG, "onViewCreated")
    }
}