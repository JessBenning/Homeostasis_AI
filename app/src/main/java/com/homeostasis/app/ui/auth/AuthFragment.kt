package com.homeostasis.app.ui.auth

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
// import androidx.lifecycle.lifecycleScope // Not needed here directly, viewLifecycleOwner.lifecycleScope is used
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
// import androidx.lifecycle.lifecycleScope // Duplicate import
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseUser
import com.google.android.material.textfield.TextInputLayout
import com.homeostasis.app.R
import com.homeostasis.app.data.Constants
import com.homeostasis.app.data.sync.FirebaseSyncManager
import com.homeostasis.app.data.local.UserDao
import com.homeostasis.app.data.sync.RemoteToLocalSyncHandler
import dagger.hilt.android.AndroidEntryPoint
// import kotlinx.coroutines.flow.first // Not directly used in this snippet, but might be used by called functions
import kotlinx.coroutines.launch // Standard launch
import javax.inject.Inject

/**
 * Fragment for user authentication (login and registration).
 */
@AndroidEntryPoint
class AuthFragment : Fragment() {

    private lateinit var viewModel: AuthViewModel

    @Inject
    lateinit var userDao: UserDao
    @Inject
    lateinit var firebaseSyncManager: FirebaseSyncManager // Inject FirebaseSyncManager
    @Inject
    lateinit var remoteToLocalSyncHandler: RemoteToLocalSyncHandler


    // UI components
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var nameEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var toggleModeTextViewRegister: TextView
    private lateinit var toggleModeTextViewLogin: TextView
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var confirmPasswordLayout: TextInputLayout // If you have a confirm password field with a toggle

    // State
    private var isLoginMode = true
    private var isProcessingAuthentication = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_auth, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        Log.d("AuthFragment", "AuthFragment onViewCreated. ViewModel instance ID: ${System.identityHashCode(viewModel)}")



        // Initialize UI components
        emailEditText = view.findViewById(R.id.email_edit_text)
        passwordEditText = view.findViewById(R.id.password_edit_text)
        confirmPasswordEditText = view.findViewById(R.id.confirm_password_edit_text)
        nameEditText = view.findViewById(R.id.name_edit_text)
        loginButton = view.findViewById(R.id.login_button)
        registerButton = view.findViewById(R.id.register_button)
        toggleModeTextViewRegister = view.findViewById(R.id.toggle_mode_text_view_register)
        toggleModeTextViewLogin = view.findViewById(R.id.toggle_mode_text_view_login)
        passwordLayout = view.findViewById(R.id.password_layout)
        confirmPasswordLayout = view.findViewById(R.id.confirm_password_layout)





        // Set initial UI state
        updateUIForMode()

        // Set up click listeners
        loginButton.setOnClickListener {
            if (validateLoginInputs()) {
                login()
            }
        }

        registerButton.setOnClickListener {
            if (validateRegistrationInputs()) {
                register()
            }
        }

        toggleModeTextViewRegister.setOnClickListener {
            isLoginMode = !isLoginMode
            updateUIForMode()
        }

        toggleModeTextViewLogin.setOnClickListener {
            isLoginMode = !isLoginMode
            updateUIForMode()
        }
        activity?.title = getString(R.string.auth_login)

        Log.d("AuthFragment", "Setting up authState observer. Current state in VM (peek): ${viewModel.authState.value}")



        val authObserver = Observer<AuthViewModel.AuthState> { state ->
            Log.d("AuthFragmentObserver", "State: $state")
            handleAuthState(state)
        }
        viewModel.authState.observe(viewLifecycleOwner, authObserver)

        // If MainActivity placed us here, we assume we need to authenticate.
        // The ViewModel's initial state should be Unauthenticated.
        // No need for an immediate manual check if MainActivity handles the initial routing.
        Log.d("AuthFragment", "Current authState from VM (after observe setup): ${viewModel.authState.value}")
        // If viewModel.authState.value is ALREADY Authenticated here (e.g. config change while logged in),
        // handleAuthState will take care of it.
        if (viewModel.authState.value is AuthViewModel.AuthState.Authenticated) {
            Log.d("AuthFragment", "ViewModel already in Authenticated state, likely due to config change or re-creation while authenticated.")
            handleAuthState(viewModel.authState.value) // Ensure navigation if already authenticated.
        }
    }

    private fun handleAuthState(state: AuthViewModel.AuthState?) {
        when (state) {
            is AuthViewModel.AuthState.Authenticated -> {
                if (!isProcessingAuthentication) { // Check the flag
                    isProcessingAuthentication = true // Set the flag
                    Log.d("AuthFragment", "Handling Authenticated state. User: ${state.user.uid}")
                    handleSuccessfulAuthentication(state.user)
                } else {
                    Log.d("AuthFragment", "Already processing authentication, skipping duplicate call.")
                }
            }
            is AuthViewModel.AuthState.Error -> {
                Log.d("AuthFragment", "Handling Error state: ${state.message}")
                context?.let { ctx ->
                    Toast.makeText(ctx, state.message, Toast.LENGTH_LONG).show()
                }
            }
            is AuthViewModel.AuthState.Loading -> {
                Log.d("AuthFragment", "Handling Loading state")
                // TODO: Implement loading indicator
            }
            is AuthViewModel.AuthState.Unauthenticated, null -> {
                Log.d("AuthFragment", "Handling Unauthenticated or null state.")
                // UI should be in login/register mode, typically handled by updateUIForMode()
            }
            // Handle other states like PasswordResetEmailSent if necessary
            is AuthViewModel.AuthState.PasswordResetEmailSent -> {
                Log.d("AuthFragment", "Password reset email sent state.")
                // Potentially show a confirmation message
                context?.let { ctx ->
                    Toast.makeText(ctx, "Password reset email sent. Please check your inbox.", Toast.LENGTH_LONG).show()
                }
            }
            else -> { // Handles AuthState.Unauthenticated or if state is null
                Log.d("AuthFragment", "Handling Unauthenticated or null state. Updating UI for current mode.")
                // Hide any loading indicators here if they were shown
                updateUIForMode() // This will show either login or register UI based on isLoginMode
            }
        }
    }

    private fun updateUIForMode() {
        if (isLoginMode) {
            // Login mode
            loginButton.visibility = View.VISIBLE
            registerButton.visibility = View.GONE
            confirmPasswordEditText.visibility = View.GONE
            nameEditText.visibility = View.GONE
            toggleModeTextViewRegister.visibility= View.VISIBLE
            toggleModeTextViewLogin.visibility = View.GONE
            passwordLayout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE

            // If confirm password field is not used in login, ensure its toggle is off or layout hidden
            confirmPasswordLayout.endIconMode = TextInputLayout.END_ICON_NONE // Or just rely on visibility GONE

        } else {
            // Registration mode
            loginButton.visibility = View.GONE
            registerButton.visibility = View.VISIBLE
            confirmPasswordEditText.visibility = View.VISIBLE
            nameEditText.visibility = View.VISIBLE
            toggleModeTextViewLogin.visibility = View.VISIBLE
            toggleModeTextViewRegister.visibility= View.GONE


            // Enable password toggle for both password fields in registration mode
            passwordLayout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            confirmPasswordLayout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
    }

    // In AuthFragment.kt

    private fun handleSuccessfulAuthentication(firebaseUser: FirebaseUser) {
        lifecycleScope.launch { // Or a more appropriate longer-lived scope
            try {
                Log.d("AuthFragmentHSAS", "Coroutine for sync started. User: ${firebaseUser.uid}")

                // 1. Fetch/Cache remote user data (includes householdGroupId) and update local DB
                // This is the most critical step for FirebaseSyncManager to pick up the group.
                val syncedUser = remoteToLocalSyncHandler.fetchAndCacheRemoteUser(firebaseUser.uid)
                if (syncedUser == null) {
                    Log.e("AuthFragmentHSAS", "Failed to sync user profile for UID: ${firebaseUser.uid}.")
                    context?.let { Toast.makeText(it, "Error syncing user data.", Toast.LENGTH_LONG).show() }
                    isProcessingAuthentication = false // Reset flag on failure
                    return@launch
                }
                Log.d("AuthFragmentHSAS", "User profile synced: ${syncedUser.name}, Household: ${syncedUser.householdGroupId}")

                // 2. Sync the household group details itself (name, members etc.)
                // This ensures the Group table in Room is up-to-date.
                val syncedGroup = remoteToLocalSyncHandler.fetchAndCacheGroupById(firebaseUser.uid, syncedUser.householdGroupId) // This should use the user's ID
                // or the now locally cached syncedUser.householdGroupId
                Log.d("AuthFragmentHSAS", "Group sync attempt finished. Synced Group: ${syncedGroup?.id}")


                // 3. Get the local user again to ensure we have the latest householdGroupId for navigation logic
                val localUserForNav = userDao.getUserById(firebaseUser.uid) // Suspend
                if (localUserForNav == null) {
                    Log.e("AuthFragmentHSAS", "User ${firebaseUser.uid} not found in local DB after sync for navigation.")
                    context?.let { Toast.makeText(it, "Failed to get local user data.", Toast.LENGTH_LONG).show() }
                    isProcessingAuthentication = false // Reset flag
                    return@launch
                }

                val householdGroupIdForNav = localUserForNav.householdGroupId?.takeIf { it.isNotEmpty() } ?: Constants.DEFAULT_GROUP_ID

                if (!isAdded || view == null) {
                    Log.w("AuthFragmentHSAS", "View became unavailable before navigation. Aborting.")
                    isProcessingAuthentication = false
                    return@launch
                }

                Log.d("AuthFragmentHSAS", "Sync complete. Navigating. Group for Nav: $householdGroupIdForNav")

                // --- FirebaseSyncManager will automatically handle Task and TaskHistory sync ---
                // The FirebaseSyncManager is already observing the user's householdGroupId.
                // When fetchAndCacheRemoteUser updated the local User entity with the correct
                // householdGroupId, FirebaseSyncManager's internal flows would have triggered
                // and set up the necessary listeners for tasks and task history for that group.
                // No explicit call like firebaseSyncManager.syncTasksForGroup() is needed here.

                if (householdGroupIdForNav == Constants.DEFAULT_GROUP_ID) {
                    Log.d("AuthFragmentHSAS", "Navigating to onboarding.")
                    navigateToOnboarding()
                } else {
                    Log.d("AuthFragmentHSAS", "Navigating to tasks. Group $householdGroupIdForNav sync should be active via FirebaseSyncManager.")
                    navigateToTasks()
                }

            } catch (e: Exception) {
                Log.e("AuthFragmentHSAS", "Error during handleSuccessfulAuthentication coroutine", e)
                context?.let { Toast.makeText(it, "An error occurred: ${e.message}", Toast.LENGTH_LONG).show() }
            } finally {
                Log.d("AuthFragmentHSAS", "Coroutine for sync finished. Resetting processing flag.")
                isProcessingAuthentication = false
            }
        }
    }

    private fun navigateToTasks() {
        // Ensure fragment is still in a state where navigation is safe
        if (!isAdded || view == null) {
            Log.w("AuthFragment", "navigateToTasks: Fragment not in a state to navigate.")
            return
        }
        findNavController().navigate(
            R.id.navigation_tasks,
            null,
            androidx.navigation.NavOptions.Builder()
                .setPopUpTo(R.id.navigation_auth, true)
                .build()
        )
    }

    private fun navigateToOnboarding() {
        // Ensure fragment is still in a state where navigation is safe
        if (!isAdded || view == null) {
            Log.w("AuthFragment", "navigateToOnboarding: Fragment not in a state to navigate.")
            return
        }
        Log.d("AuthFragment", "Navigating to onboarding screen.")
        findNavController().navigate(
            R.id.onboardingDialogFragment,
            null,
            androidx.navigation.NavOptions.Builder()
                .setPopUpTo(R.id.navigation_auth, true)
                .build()
        )
    }



    private fun validateLoginInputs(): Boolean {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString()

        var isValid = true
        if (email.isEmpty()) {
            emailEditText.error = "Email is required"
            isValid = false
        }

        if (password.isEmpty()) {
            passwordEditText.error = "Password is required"
            isValid = false
        }

        return isValid
    }

    private fun validateRegistrationInputs(): Boolean {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()
        val name = nameEditText.text.toString().trim()

        var isValid = true
        if (email.isEmpty()) {
            emailEditText.error = "Email is required"
            isValid = false
        }

        if (password.isEmpty()) {
            passwordEditText.error = "Password is required"
            isValid = false
        }

        if (password.length < 6) {
            passwordEditText.error = "Password must be at least 6 characters"
            isValid = false
        }

        if (confirmPassword != password) {
            confirmPasswordEditText.error = "Passwords do not match"
            isValid = false
        }

        if (name.isEmpty()) {
            nameEditText.error = "Name is required"
            isValid = false
        }

        return isValid
    }

    private fun login() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString()

        viewModel.signIn(email, password)
    }

    private fun register() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString()
        val name = nameEditText.text.toString().trim()

        viewModel.register(email, password, name)
    }
}