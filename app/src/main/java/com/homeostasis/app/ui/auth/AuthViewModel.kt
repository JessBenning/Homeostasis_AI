package com.homeostasis.app.ui.auth

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.homeostasis.app.data.local.UserDao // <<< IMPORT THIS
import com.homeostasis.app.data.model.User // <<< IMPORT YOUR LOCAL USER MODEL
import com.homeostasis.app.data.remote.UserRepository
import com.homeostasis.app.data.Constants // <<< IMPORT CONSTANTS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for authentication operations.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth, // Can keep this for the AuthStateListener
    private val userRepository: UserRepository,
    private val userDao: UserDao // <<< ADD UserDao INJECTION
    // private val constants: Constants // Not directly injectable, access its values directly
) : ViewModel() {

    // ... (authState, authStateListener, init block remain mostly the same)
    // Minor change in listener if you want to also clear local user data on unauthentication,
    // but for now, let's focus on registration.
    private val _authState = MutableLiveData<AuthState>(AuthState.Unauthenticated)
    val authState: LiveData<AuthState> = _authState

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            Log.d("AuthViewModel", "AuthStateListener: User is Authenticated (UID: ${firebaseUser.uid})")
            _authState.value = AuthState.Authenticated(firebaseUser)
        } else {
            Log.d("AuthViewModel", "AuthStateListener: User is Unauthenticated")
            if (_authState.value !is AuthState.Error && _authState.value !is AuthState.Loading) { // Prevent overwriting error/loading
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    init {
        Log.d("AuthViewModel", "AuthViewModel init. Instance ID: ${System.identityHashCode(this)}. Initial authState: ${_authState.value}")
        firebaseAuth.addAuthStateListener(authStateListener)
        // Check current user on init, in case listener fires after initial value set
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null && _authState.value is AuthState.Unauthenticated) {
            _authState.value = AuthState.Authenticated(currentUser)
        } else if (currentUser == null && _authState.value is AuthState.Authenticated) {
            // This can happen if ViewModel is re-created after user has signed out elsewhere
            _authState.value = AuthState.Unauthenticated
        }
    }

    /**
     * Sign in with email and password.
     */
    fun signIn(email: String, password: String) {
        Log.d("AuthViewModel", "signIn called")
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                // Use the new method name
                val result = userRepository.signInWithFirebaseAuth(email, password)
                if (result.isSuccess) {
                    val firebaseUser = result.getOrNull()!!
                    // AuthStateListener should pick this up, but we can also set it here
                    // to ensure immediate UI update before listener might fire.
                    _authState.value = AuthState.Authenticated(firebaseUser)
                    Log.d("AuthViewModel", "Sign in successful for: ${firebaseUser.uid}")
                } else {
                    val errorMessage = result.exceptionOrNull()?.message ?: "Authentication failed: Unknown reason"
                    _authState.value = AuthState.Error(errorMessage)
                    Log.d("AuthViewModel", "Sign in failed: $errorMessage")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "An unexpected error occurred during sign in")
                Log.e("AuthViewModel", "Sign in exception", e)
            }
        }
    }

    /**
     * Register a new user with email, password, and name.
     * This will now also create a local user record.
     */
    fun register(email: String, password: String, name: String) {
        Log.d("AuthViewModel", "register called")
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                // 1. Register user in Firebase Authentication
                // Use the new method name
                val authResult = userRepository.registerUserInFirebaseAuth(email, password, name)

                if (authResult.isSuccess) {
                    val firebaseUser = authResult.getOrNull()!!
                    Log.d("AuthViewModel", "Firebase Auth registration successful for: ${firebaseUser.uid}")

                    // 2. Create and save local user to Room database
                    // Assuming your User model has these fields. Adjust as necessary.
                    val newUser = User(
                        id = firebaseUser.uid, // Use Firebase UID as the local User ID
                        name = firebaseUser.displayName ?: name, // Use display name from Auth profile or provided name
                        //email = firebaseUser.email ?: email,
                        //profileImageUrl = null, // Initially no profile image URL
                        householdGroupId = Constants.DEFAULT_GROUP_ID, // Assign a default group or handle differently
//                        createdAt = Timestamp.now(), // Record creation time
//                        lastModifiedAt = Timestamp.now(),
                        needsSync = true // Mark for synchronization with Firestore
                        // Add any other necessary fields with default values
                    )

                    try {
                        userDao.upsertUser(newUser)
                        Log.d("AuthViewModel", "Local user record created/updated for UID: ${newUser.id}")

                        // AuthStateListener should pick up the new authenticated user,
                        // but setting it here ensures quicker UI update.
                        _authState.value = AuthState.Authenticated(firebaseUser)
                        Log.d("AuthViewModel", "Registration and local user save successful.")

                    } catch (dbException: Exception) {
                        Log.e("AuthViewModel", "Failed to save local user record", dbException)
                        // Critical decision: What to do if local save fails?
                        // - Option 1: Attempt to delete the Firebase Auth user (complex, requires re-auth for deletion).
                        // - Option 2: Leave Firebase Auth user, mark as error. User might exist in Auth but not locally.
                        // - Option 3: Retry local save.
                        // For now, setting to error state. The app needs a strategy for this.
                        _authState.value = AuthState.Error("Registration succeeded but failed to save local user profile: ${dbException.message}")
                        // Consider signing out the partially registered user to maintain consistency
                        // userRepository.signOutFromFirebaseAuth() // This would trigger Unauthenticated state
                    }

                } else {
                    val errorMessage = authResult.exceptionOrNull()?.message ?: "Registration failed: Unknown reason"
                    _authState.value = AuthState.Error(errorMessage)
                    Log.d("AuthViewModel", "Firebase Auth registration failed: $errorMessage")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "An unexpected error occurred during registration")
                Log.e("AuthViewModel", "Registration exception", e)
            }
        }
    }

    /**
     * Sign out the current user.
     */
    fun signOut() {
        Log.d("AuthViewModel", "signOut called")
        // Use the new method name
        userRepository.signOutFromFirebaseAuth()
        // AuthStateListener will handle setting _authState to Unauthenticated
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("AuthViewModel", "AuthViewModel onCleared. Removing AuthStateListener.")
        firebaseAuth.removeAuthStateListener(authStateListener)
    }

    /**
     * Check if a user is currently signed in (useful for initial app start checks).
     * This primarily relies on the AuthStateListener and initial check in init,
     * but can be called explicitly if needed.
     */
    fun checkAuthState() {
        val currentUser = userRepository.getCurrentUser() // This method in UserRepository is fine
        Log.d("AuthViewModel", "checkAuthState called. Current Firebase user: ${currentUser?.uid}")
        if (currentUser != null) {
            if (_authState.value !is AuthState.Authenticated || (_authState.value as? AuthState.Authenticated)?.user?.uid != currentUser.uid) {
                _authState.value = AuthState.Authenticated(currentUser)
            }
        } else {
            if (_authState.value !is AuthState.Unauthenticated) {
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    /**
     * Send a password reset email.
     */
    fun sendPasswordResetEmail(email: String) {
        Log.d("AuthViewModel", "sendPasswordResetEmail called for $email")
        // Note: We don't necessarily set AuthState to Loading here,
        // as password reset is often a side operation.
        // Or you might want a specific state like AuthState.PasswordResetInProgress
        viewModelScope.launch {
            try {
                // Use the new method name
                val result = userRepository.sendPasswordResetEmailWithFirebaseAuth(email)
                if (result.isSuccess) {
                    _authState.value = AuthState.PasswordResetEmailSent
                    Log.d("AuthViewModel", "Password reset email sent successfully for $email.")
                } else {
                    val errorMessage = result.exceptionOrNull()?.message ?: "Failed to send password reset email"
                    _authState.value = AuthState.Error(errorMessage)
                    Log.d("AuthViewModel", "Failed to send password reset email: $errorMessage")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Error sending password reset email")
                Log.e("AuthViewModel", "Password reset email exception", e)
            }
        }
    }

    /**
     * Authentication state.
     */
    sealed class AuthState {
        data class Authenticated(val user: FirebaseUser) : AuthState()
        data class Error(val message: String) : AuthState()
        object Loading : AuthState()
        object Unauthenticated : AuthState()
        object PasswordResetEmailSent : AuthState()
        // Potentially: object RegistrationSuccessful : AuthState() // If you need a distinct state post-local save
    }
}