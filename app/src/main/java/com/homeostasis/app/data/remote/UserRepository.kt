package com.homeostasis.app.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp // Import Timestamp
import com.homeostasis.app.data.model.User
import com.homeostasis.app.data.UserDao // Import UserDao
import com.homeostasis.app.data.HouseholdGroupIdProvider // Import HouseholdGroupIdProvider
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first // Import first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for user-related operations.
 */
class UserRepository @Inject constructor(
    private val firebaseStorageRepository: FirebaseStorageRepository, // Inject FirebaseStorageRepository
    private val userDao: UserDao, // Inject UserDao
    private val householdGroupIdProvider: HouseholdGroupIdProvider // Inject HouseholdGroupIdProvider
) : FirebaseRepository<User>() {

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
     * This function is primarily for updating Firestore directly, used by sync manager.
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

    /**
     * Pushes a locally modified user to Firestore.
     * This is called by the sync manager.
     *
     * @param user The user object to push.
     * @return True if push was successful, false otherwise.
     */
    suspend fun pushUserToFirestore(user: User): Boolean {
        return try {
            // Use the existing set function which handles create/update
            set(user.id, user)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Uploads a profile picture to Firebase Storage.
     *
     * @param userId The ID of the user the picture belongs to.
     * @param localFile The local file containing the profile picture.
     * @return The download URL of the uploaded picture, or null if the upload failed.
     */
    suspend fun uploadProfilePicture(userId: String, localFile: java.io.File): String? {
        return firebaseStorageRepository.uploadFile(
            file = localFile,
            path = "profile_pictures/$userId", // Storage path
            fileName = "profile.jpg" // Fixed filename for the profile picture
        )
    }
/**
 * Updates the user's score.
 *
 * @param userId The ID of the user whose score to update.
 * @param scoreChange The amount to change the score by (positive or negative).
 * @return True if the update was successful, false otherwise.
 */
//suspend fun updateUserScore(userId: String, scoreChange: Int): Boolean {
//    return try {
//        val userResult = getUserById(userId) // Assuming getUserById is reliable
//        if (userResult.isSuccess) {
//            val user = userResult.getOrThrow()
//            val updatedUser = user.copy(score = user.score + scoreChange) // Assuming User has a 'score' field
//            pushUserToFirestore(updatedUser) // Use the existing push function
//        } else {
//            false // User not found or error fetching
//        }
//    } catch (e: Exception) {
//        false // Error during update
//    }
//}

override fun getModelClass(): Class<User> = User::class.java
}
