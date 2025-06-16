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
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputLayout
import com.homeostasis.app.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Fragment for user authentication (login and registration).
 */
@AndroidEntryPoint
class AuthFragment : Fragment() {

    private lateinit var viewModel: AuthViewModel
    
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
        
        // Observe authentication state
        viewModel.authState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AuthViewModel.AuthState.Authenticated -> {
                    Log.d("AuthFragmentObserver", "State is Authenticated. User: ${(state as AuthViewModel.AuthState.Authenticated).user}")
                    // Navigate to the tasks screen
                    findNavController().navigate(R.id.action_auth_to_tasks)
                }
                is AuthViewModel.AuthState.Error -> {
                    Log.d("AuthFragmentObserver", "State is Error: ${(state as AuthViewModel.AuthState.Error).message}") // Add this log
                    // Show error message
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
                is AuthViewModel.AuthState.Loading -> {
                    Log.d("AuthFragmentObserver", "State is Loading")
                    // Show loading indicator
                    // TODO: Implement loading indicator
                }
                else -> {
                    Log.d("AuthFragmentObserver", "State is other/idle: $state")
                    // Do nothing
                }
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
    
    private fun validateLoginInputs(): Boolean {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString()
        
        if (email.isEmpty()) {
            emailEditText.error = "Email is required"
            return false
        }
        
        if (password.isEmpty()) {
            passwordEditText.error = "Password is required"
            return false
        }
        
        return true
    }
    
    private fun validateRegistrationInputs(): Boolean {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()
        val name = nameEditText.text.toString().trim()
        
        if (email.isEmpty()) {
            emailEditText.error = "Email is required"
            return false
        }
        
        if (password.isEmpty()) {
            passwordEditText.error = "Password is required"
            return false
        }
        
        if (password.length < 6) {
            passwordEditText.error = "Password must be at least 6 characters"
            return false
        }
        
        if (confirmPassword != password) {
            confirmPasswordEditText.error = "Passwords do not match"
            return false
        }
        
        if (name.isEmpty()) {
            nameEditText.error = "Name is required"
            return false
        }
        
        return true
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