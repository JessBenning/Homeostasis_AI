package com.homeostasis.app.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.homeostasis.app.data.remote.CategoryRepository
import com.homeostasis.app.data.remote.ShoppingListRepository
import com.homeostasis.app.data.remote.UserRepository
import com.homeostasis.app.data.remote.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for authentication operations.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val categoryRepository: CategoryRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val userSettingsRepository: UserSettingsRepository
) : ViewModel() {

    // Authentication state
    private val _authState = MutableLiveData<AuthState>(AuthState.Unauthenticated)
    val authState: LiveData<AuthState> = _authState
    
    /**
     * Sign in with email and password.
     */
    fun signIn(email: String, password: String) {
        _authState.value = AuthState.Loading
        
        viewModelScope.launch {
            try {
                val result = userRepository.signIn(email, password)
                
                if (result.isSuccess) {
                    _authState.value = AuthState.Authenticated(result.getOrNull()!!)
                } else {
                    _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Authentication failed")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Authentication failed")
            }
        }
    }
    
    /**
     * Register a new user with email, password, and name.
     */
    fun register(email: String, password: String, name: String) {
        _authState.value = AuthState.Loading
        
        viewModelScope.launch {
            try {
                val result = userRepository.registerUser(email, password, name)
                
                if (result.isSuccess) {
                    val user = result.getOrNull()!!
                    
                    // Create default categories for the user
                    categoryRepository.createDefaultCategories(user.uid)
                    
                    // Create default shopping lists for the user
                    shoppingListRepository.createDefaultShoppingLists(user.uid)
                    
                    // Create default user settings
                    userSettingsRepository.getUserSettings(user.uid)
                    
                    _authState.value = AuthState.Authenticated(user)
                } else {
                    _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Registration failed")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Registration failed")
            }
        }
    }
    
    /**
     * Sign out the current user.
     */
    fun signOut() {
        userRepository.signOut()
        _authState.value = AuthState.Unauthenticated
    }
    
    /**
     * Check if a user is currently signed in.
     */
    fun checkAuthState() {
        val currentUser = userRepository.getCurrentUser()
        
        if (currentUser != null) {
            _authState.value = AuthState.Authenticated(currentUser)
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }
    
    /**
     * Send a password reset email.
     */
    fun sendPasswordResetEmail(email: String) {
        viewModelScope.launch {
            try {
                val result = userRepository.sendPasswordResetEmail(email)
                
                if (result.isSuccess) {
                    _authState.value = AuthState.PasswordResetEmailSent
                } else {
                    _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Failed to send password reset email")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Failed to send password reset email")
            }
        }
    }
    
    /**
     * Authentication state.
     */
    sealed class AuthState {
        object Unauthenticated : AuthState()
        object Loading : AuthState()
        data class Authenticated(val user: FirebaseUser) : AuthState()
        data class Error(val message: String) : AuthState()
        object PasswordResetEmailSent : AuthState()
    }
}