package com.homeostasis.app.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.homeostasis.app.data.model.User
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Repository for user-related operations.
 */
class UserRepository : FirebaseRepository<User>() {
    
    override val collectionName: String = User.COLLECTION
    
    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }
    
    /**
     * Get the current authenticated user.
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }
    
    /**
     * Get the current authenticated user's ID.
     */
    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }
    
    /**
     * Register a new user with email and password.
     */
    suspend fun registerUser(email: String, password: String, name: String): Result<FirebaseUser> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
            
            // Update display name
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build()
            
            firebaseUser?.updateProfile(profileUpdates)?.await()
            
            // Create user document in Firestore
            firebaseUser?.let {
                val user = User(
                    id = it.uid,
                    name = name
                )
                set(it.uid, user)
            }
            
            Result.success(firebaseUser!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Sign in with email and password.
     */
    suspend fun signIn(email: String, password: String): Result<FirebaseUser> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            Result.success(authResult.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Sign out the current user.
     */
    fun signOut() {
        auth.signOut()
    }
    
    /**
     * Send password reset email.
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update user profile.
     */
    suspend fun updateUserProfile(userId: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            collection.document(userId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get user by ID.
     */
    suspend fun getUserById(userId: String): Result<User> {
        return try {
            val user = collection.document(userId).get().await().toObject(User::class.java)
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get all users.
     */
    suspend fun getAllUsers(): Result<List<User>> {
        return try {
            val users = collection.get().await().toObjects(User::class.java)
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get all users as a Flow.
     */
    fun getAllUsersAsFlow(): Flow<Result<List<User>>> = flow {
        try {
            val snapshot = collection.get().await()
            val users = snapshot.toObjects(User::class.java)
            emit(Result.success(users))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
    
    override fun getModelClass(): Class<User> = User::class.java
}