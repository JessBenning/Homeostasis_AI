package com.homeostasis.app.ui.groups

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText // More appropriate for input
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult // Import for setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.observe
import com.homeostasis.app.R
import dagger.hilt.android.AndroidEntryPoint

// Assuming you have an AcceptInviteViewModel
// import com.homeostasis.app.ui.groups.AcceptInviteViewModel
@AndroidEntryPoint
class AcceptInviteDialogFragment : DialogFragment() {

    private val viewModel: AcceptInviteViewModel by viewModels() // Or activityViewModels if shared
    private var isFromOnboarding: Boolean = false

    companion object {
        const val TAG = "AcceptInviteDialogFragment"
        private const val ARG_IS_FROM_ONBOARDING = "isFromOnboarding"

        // Result keys for communication
        const val REQUEST_KEY = "acceptInviteResult" // General request key
        const val RESULT_SUCCESS = "inviteSuccess"   // Boolean, true if successful
        const val RESULT_WAS_FROM_ONBOARDING = "wasFromOnboarding" // Boolean, to confirm context

        fun newInstance(isFromOnboarding: Boolean): AcceptInviteDialogFragment {
            val args = Bundle()
            args.putBoolean(ARG_IS_FROM_ONBOARDING, isFromOnboarding)
            val fragment = AcceptInviteDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isFromOnboarding = arguments?.getBoolean(ARG_IS_FROM_ONBOARDING) ?: false
        Log.d(TAG, "onCreate - isFromOnboarding: $isFromOnboarding")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView called")
        return inflater.inflate(R.layout.dialog_accept_invite, container, false)
        // Ensure R.layout.dialog_accept_invite is your correct layout file
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        val acceptButton: Button = view.findViewById(R.id.settings_accept_invite_button)
        val declineButton: Button = view.findViewById(R.id.settings_accept_invite_dismiss_button)
        val inviteLinkEditText: EditText = view.findViewById(R.id.invite_link_edit_text) // Changed to EditText
        val validationMessageTextView: TextView = view.findViewById(R.id.validation_message_text_view)

        // Observe ViewModel for invite result
        viewModel.inviteProcessComplete.observe(viewLifecycleOwner) { success ->
            if (success) {
                Log.d(TAG, "Invite processing successful. Notifying caller.")
                // Notify the calling fragment of success
                setFragmentResult(REQUEST_KEY, bundleOf(
                    RESULT_SUCCESS to true,
                    RESULT_WAS_FROM_ONBOARDING to isFromOnboarding
                ))
                dismiss()
            } else {
                Log.d(TAG, "Invite processing failed. Displaying error.")
                validationMessageTextView.text = getString(R.string.error_accept_invite_failed) // Add this string
                validationMessageTextView.visibility = View.VISIBLE
                // Optionally re-enable accept button if it was disabled
            }
        }

        acceptButton.setOnClickListener {
            val inviteLink = inviteLinkEditText.text.toString().trim()
            if (inviteLink.isNotEmpty()) {
                Log.d(TAG, "Accept button clicked with link: $inviteLink")
                validationMessageTextView.visibility = View.GONE
                // Optionally disable accept button here to prevent multiple clicks
                viewModel.processInviteLink(inviteLink, isFromOnboarding) // ViewModel handles the logic
            } else {
                validationMessageTextView.text = getString(R.string.error_invite_link_empty) // Add this string
                validationMessageTextView.visibility = View.VISIBLE
            }
        }

        declineButton.setOnClickListener {
            Log.d(TAG, "Decline button clicked")
            // Notify the calling fragment of dismissal (optional, but good for clarity)
            // If the caller needs to distinguish between a back press dismissal and an explicit cancel.
            setFragmentResult(REQUEST_KEY, bundleOf(
                RESULT_SUCCESS to false,
                RESULT_WAS_FROM_ONBOARDING to isFromOnboarding // Still useful to pass context
            ))
            dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // When using onCreateView, onCreateDialog can be minimal.
        // It's called before onCreateView.
        // If you need to set dialog properties not available in XML (like non-cancelable), do it here.
        val dialog = super.onCreateDialog(savedInstanceState)
        Log.d(TAG, "onCreateDialog called")
        // Example: dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    // You might need an AcceptInviteViewModel
    // class AcceptInviteViewModel : ViewModel() {
    //     private val _inviteResult = MutableLiveData<Boolean>()
    //     val inviteResult: LiveData<Boolean> = _inviteResult
    //
    //     fun processInviteLink(link: String, isFromOnboarding: Boolean) {
    //         // TODO: Implement your actual invite link processing logic
    //         // This would involve repository calls, updating user's group, etc.
    //         viewModelScope.launch {
    //             val success = // ... your logic ...
    //             _inviteResult.postValue(success)
    //         }
    //     }
    // }
}