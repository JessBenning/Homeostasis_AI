package com.homeostasis.app.data.remote

import android.net.Uri // Still needed for userProfileChangeRequest photoUri
import android.util.Log

// Removed MimeTypeMap as uploadImageToStorage is removed
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.auth.User
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.io.path.exists

/**
 * Repository for Firebase Authentication operations and updating FirebaseUser's profile (displayName, photoUrl).
 *
 * It does NOT directly interact with UserDao or the local database.
 * It does NOT directly handle file uploads to Firebase Storage; that is delegated
 * to FirebaseSyncManager (which would use FirebaseStorageRepository).
 *
 * Local user data management is handled by ViewModels or other services that interact with UserDao.
 * Firestore 'user' collection interaction is handled by FirebaseSyncManager or similar.
 */
@Singleton
class UserRepository @Inject constructor(
    // Removed FirebaseStorageRepository as uploadImageToStorage is gone.
    // FirebaseSyncManager will use FirebaseStorageRepository directly.
    private val auth: FirebaseAuth
) {

    companion object {
        private const val TAG = "UserRepository"
    }

    /**
     * Observes the Firebase Authentication state and emits the UID of the current user.
     * Emits null if no user is authenticated.
     */
    fun observeFirebaseAuthUid(): Flow<String?> = callbackFlow {
        Log.d(TAG, "observeFirebaseAuthUid: Setting up AuthStateListener.")
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            Log.d(TAG, "observeFirebaseAuthUid: AuthStateChanged. User: ${user?.uid}")
            trySend(user?.uid)
        }
        auth.addAuthStateListener(listener)

        awaitClose {
            Log.d(TAG, "observeFirebaseAuthUid: Closing AuthStateListener.")
            auth.removeAuthStateListener(listener)
        }
    }.distinctUntilChanged()

    /**
     * Gets the current FirebaseUser from FirebaseAuth.
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    /**
     * Gets the current FirebaseUser's ID.
     */
    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }


    /**
     * Registers a new user with email, password, and name in Firebase Authentication.
     * It ONLY handles the Firebase Auth user creation and Auth profile update (displayName).
     * The creation of a corresponding local user record in Room (via UserDao)
     * is the responsibility of the calling ViewModel or service.
     *
     * @param email User's email.
     * @param password User's password.
     * @param name User's display name for the Firebase Auth profile.
     * @return Result<FirebaseUser> containing the FirebaseUser on success, or an exception on failure.
     */
    suspend fun registerUserInFirebaseAuth(email: String, password: String, name: String): Result<FirebaseUser> {
        return try {
            Log.d(TAG, "Attempting to register user with Firebase Auth: $email")
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
            Log.d(TAG, "Firebase Auth user created successfully. UID: ${firebaseUser?.uid}")

            if (firebaseUser == null) {
                Log.e(TAG, "Firebase user is null after auth creation.")
                return Result.failure(Exception("Firebase user creation resulted in null user object"))
            }

            val profileUpdates = userProfileChangeRequest {
                displayName = name
            }
            firebaseUser.updateProfile(profileUpdates).await()
            Log.d(TAG, "Firebase Auth profile displayName updated for user: ${firebaseUser.uid}")

            Result.success(firebaseUser)
        } catch (e: Exception) {
            Log.e(TAG, "Error during Firebase Auth user registration for email $email: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Signs in a user with email and password using Firebase Authentication.
     */
    suspend fun signInWithFirebaseAuth(email: String, password: String): Result<FirebaseUser> {
        return try {
            Log.d(TAG, "Attempting to sign in user with email via Firebase Auth: $email")
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
            if (firebaseUser != null) {
                Log.d(TAG, "User ${firebaseUser.uid} signed in successfully via Firebase Auth.")
                Result.success(firebaseUser)
            } else {
                Log.e(TAG, "Sign in with Firebase Auth successful but FirebaseUser is null for email: $email")
                Result.failure(Exception("Sign in completed but FirebaseUser object was null."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during Firebase Auth sign in for email $email: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Signs out the current user from Firebase Authentication.
     */
    fun signOutFromFirebaseAuth() {
        val currentUserId = auth.currentUser?.uid
        Log.d(TAG, "Signing out current user from Firebase Auth: $currentUserId")
        auth.signOut()
        Log.d(TAG, "Sign out from Firebase Auth complete.")
    }

    /**
     * Sends a password reset email using Firebase Authentication.
     */
    suspend fun sendPasswordResetEmailWithFirebaseAuth(email: String): Result<Unit> {
        return try {
            Log.d(TAG, "Sending password reset email via Firebase Auth to: $email")
            auth.sendPasswordResetEmail(email).await()
            Log.d(TAG, "Password reset email sent successfully via Firebase Auth to: $email")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending password reset email via Firebase Auth to $email: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Updates the current Firebase Authenticated user's profile (displayName and/or photoUrl).
     * This method interacts directly with Firebase Authentication.
     * The photoUrl provided here should be a remote URL (e.g., from Firebase Storage,
     * obtained by FirebaseSyncManager after an upload).
     *
     * @param displayName The new display name (optional).
     * @param photoUrl The new photo URL (optional, should be a remote URL).
     * @return Result<Unit> indicating success or failure.
     */
//    suspend fun updateFirebaseAuthProfile(displayName: String?, photoUrl: String?): Result<Unit> {
//        val firebaseUser = auth.currentUser
//        if (firebaseUser == null) {
//            Log.w(TAG, "updateFirebaseAuthProfile: No Firebase user logged in.")
//            return Result.failure(Exception("No user logged in to update Firebase Auth profile."))
//        }
//        return try {
//            val requestBuilder = userProfileChangeRequest {
//                displayName?.let { this.displayName = it }
//                photoUrl?.let { this.photoUri = Uri.parse(it) }
//            }
//
//            // Only proceed if there are actual changes to make
//            if (requestBuilder.displayName != null || requestBuilder.photoUri != null ||
//                (displayName != null && displayName != firebaseUser.displayName) ||
//                (photoUrl != null && photoUrl != firebaseUser.photoUrl?.toString())) { // Check against current values
//                firebaseUser.updateProfile(requestBuilder).await()
//                Log.d(TAG, "Successfully updated Firebase Auth profile for user ${firebaseUser.uid}.")
//            } else {
//                Log.d(TAG, "updateFirebaseAuthProfile: No changes to apply to Firebase Auth profile for user ${firebaseUser.uid}.")
//            }
//            Result.success(Unit)
//        } catch (e: Exception) {
//            Log.e(TAG, "Error updating Firebase Auth profile for user ${firebaseUser.uid}: ${e.message}", e)
//            Result.failure(e)
//        }
//    }
}