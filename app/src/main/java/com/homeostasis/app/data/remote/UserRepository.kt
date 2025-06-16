package com.homeostasis.app.data.remote

import android.util.Log
import com.homeostasis.app.data.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp // Import Timestamp
import com.homeostasis.app.data.model.User
import com.homeostasis.app.data.UserDao // Import UserDao
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
    private val userDao: UserDao // Inject UserDao
) : FirebaseRepository<User>() {

    override val collectionName: String = User.COLLECTION

    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    // Add this method
    /**
     * Gets the current FirebaseUser from FirebaseAuth.
     *
     * @return The current FirebaseUser if logged in, otherwise null.
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    // Add this method as well if you need to quickly get the user ID
    /**
     * Gets the current FirebaseUser's ID.
     *
     * @return The current FirebaseUser's ID if logged in, otherwise null.
     */
    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }


    /**
     * Register a new user with email and password.
     */
    // In UserRepository.kt
    suspend fun registerUser(email: String, password: String, name: String): Result<FirebaseUser> {
        return try {
            Log.d("UserRepository", "Attempting to register user with Firebase: $email")
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
            Log.d("UserRepository", "Firebase user created successfully. UID: ${firebaseUser?.uid}")

            if (firebaseUser == null) {
                Log.e("UserRepository", "Firebase user is null after creation, cannot proceed to local DB save.")
                return Result.failure(Exception("Firebase user creation resulted in null user object"))
            }

            val now = Timestamp.now()
            // ... (profile update logic) ...

            val userToInsert = User(
                id = firebaseUser.uid, // Make sure firebaseUser.uid is not null here
                name = name,
                // email = firebaseUser.email,
                profileImageUrl = "",
                createdAt = now,
                lastActive = now,
                lastResetScore = 0,
                resetCount = 0,
                householdGroupId = com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID,
                needsSync = true,
                isDeletedLocally = false,
                lastModifiedAt = now
            )
            Log.d("UserRepository", "User object created for local DB: $userToInsert")

            Log.d("UserRepository", "User object created for local DB: $userToInsert")
            try {
                userDao.upsertUser(userToInsert)
                Log.d("UserRepository", "SUCCESS: userDao.upsertUser completed for UID: ${userToInsert.id}.")
            } catch (e: Exception) {
                Log.e("UserRepository", "EXCEPTION during userDao.upsertUser for UID ${userToInsert.id}: ${e.message}", e)
                // Log the full stack trace for more details by passing 'e' as the third argument
            }


            Log.d("UserRepository", "upsertUser called for UID: ${userToInsert.id}. Check local DB now.")

            Result.success(firebaseUser) // No need for !! if you checked above
        } catch (e: Exception) {
            Log.e("UserRepository", "Error during user registration: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Sign in with email and password.
     */
    suspend fun signIn(email: String, password: String): Result<FirebaseUser> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            // After successful sign-in, the FirebaseSyncManager should handle fetching the user data from Firestore
            // and saving it to the local DB. No direct Firestore fetch here.
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
        // Optionally, clear local user-specific data or caches here
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
     * Update user profile in local DB and mark for sync.
     * The `updates` map should contain fields that are part of the User model.
     */
    suspend fun updateUserProfile(userId: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            // Get the current user's householdGroupId from the local DB
            val currentHouseholdGroupId = userDao.getUserByIdWithoutHouseholdIdFlow(userId).first()?.householdGroupId?.takeIf { it.isNotEmpty() } ?: com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID

            val currentUser = userDao.getUserById(userId, currentHouseholdGroupId) // Fetch local user
            if (currentUser != null) {
                // Apply updates to the local user object
                val updatedUser = currentUser.copy(
                    name = updates["name"] as? String ?: currentUser.name,
                    profileImageUrl = updates["profileImageUrl"] as? String ?: currentUser.profileImageUrl,
                    // Add other fields from the map as needed
                    lastModifiedAt = Timestamp.now(), // Update modification timestamp
                    needsSync = true // Mark for sync
                )
                userDao.upsertUser(updatedUser) // Save updated user to local DB
                Result.success(Unit)
            } else {
                Result.failure(Exception("User not found locally for update"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get user by ID. Attempts to fetch from local DAO first, then Firestore.
     * This is the corrected version.
     */
    suspend fun getUser(userId: String): User? {
        // Get the current user's householdGroupId from the local DB
        val currentHouseholdGroupId = userDao.getUserByIdWithoutHouseholdIdFlow(userId).first()?.householdGroupId?.takeIf { it.isNotEmpty() } ?: com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID

        // Try local DAO first, passing the householdGroupId
        var user = userDao.getUserById(userId, currentHouseholdGroupId) // Corrected call

        if (user == null || user.needsSync) { // Also fetch if marked as needsSync
            // Not found locally or needs sync, try fetching from Firestore
            // getUserFromFirestore internally handles ensuring the returned User object has a householdGroupId
            val firestoreResult = getUserFromFirestore(userId)
            if (firestoreResult.isSuccess) {
                val userFromFirestore = firestoreResult.getOrNull()
                userFromFirestore?.let {
                    // Ensure the user object being cached has a householdGroupId,
                    // preferring its own, then the one from the local DB, then default.
                    val resolvedHouseholdGroupIdForCache = it.householdGroupId ?: currentHouseholdGroupId
                    val userToCache = it.copy(
                        householdGroupId = resolvedHouseholdGroupIdForCache,
                        needsSync = false // Mark as synced
                    )
                    userDao.upsertUser(userToCache) // upsertUser in DAO should handle the full User object
                    user = userToCache // Update user with the fresh data
                }
            }
        }
        // Final safety net for householdGroupId, though ideally it's always populated by now.
        // If the user was fetched from DAO, it should already have the correct currentHouseholdGroupId.
        // If fetched from Firestore, getUserFromFirestore populates it.
        // This makes sure the returned object is consistent.
        return user?.copy(
            householdGroupId = user.householdGroupId ?: currentHouseholdGroupId // Prefer user's existing, then current, then default (already handled by currentHouseholdGroupId init)
        )
    }
    // Add this method to your UserRepository.kt
    suspend fun updateUserProfileInLocalDb(userId: String, name: String, profileImageUrl: String): Boolean {
        // Get the current user's householdGroupId from the local DB
        val currentHouseholdGroupId = userDao.getUserByIdWithoutHouseholdIdFlow(userId).first()?.householdGroupId?.takeIf { it.isNotEmpty() } ?: com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID

        val existingUser = userDao.getUserById(userId, currentHouseholdGroupId)

        return if (existingUser != null) {
            val updatedUser = existingUser.copy(
                name = name,
                profileImageUrl = profileImageUrl,
                needsSync = true, // Mark for sync as local data changed
                lastModifiedAt = com.google.firebase.Timestamp.now() // Update modification timestamp
            )
            userDao.upsertUser(updatedUser) // Call the DAO to save the updated user
            true
        } else {
            // Log an error or handle the case where the user isn't found locally
            // For example: Log.e("UserRepository", "User $userId not found in group $currentHouseholdGroupId for local profile update.")
            false
        }
    }

    /**
     * Get user by ID specifically from Firestore.
     */
    suspend fun getUserFromFirestore(userId: String): Result<User> {
        return try {
            val userDocument = collection.document(userId).get().await().toObject(User::class.java)
            if (userDocument != null) {
                // Get the current user's householdGroupId from the local DB as a fallback
                val currentHouseholdGroupId = userDao.getUserByIdWithoutHouseholdIdFlow(userId).first()?.householdGroupId?.takeIf { it.isNotEmpty() } ?: com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID

                // Ensure householdGroupId is populated if it comes as null from Firestore, using local DB as fallback
                val finalHouseholdGroupId = userDocument.householdGroupId ?: currentHouseholdGroupId
                Result.success(userDocument.copy(householdGroupId = finalHouseholdGroupId))
            } else {
                Result.failure(Exception("User not found in Firestore"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    /**
     * Get all users from Firestore.
     */
    suspend fun getAllUsersFromFirestore(): Result<List<User>> {
        return try {
            // Get the current user's ID to fetch their householdGroupId from the local DB
            val currentUserId = getCurrentUserId()
            val currentHouseholdGroupId = if (currentUserId != null) {
                userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId).first()?.householdGroupId?.takeIf { it.isNotEmpty() } ?: com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
            } else {
                com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
            }

            val users = collection.get().await().toObjects(User::class.java).map { user ->
                user.copy(
                    // Use the user's existing householdGroupId from Firestore, or the current user's group as a fallback
                    householdGroupId = user.householdGroupId ?: currentHouseholdGroupId
                )
            }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all users as a Flow from Firestore.
     */
    fun getAllUsersFromFirestoreAsFlow(): Flow<Result<List<User>>> = flow {
        try {
            // Get the current user's ID to fetch their householdGroupId from the local DB
            val currentUserId = getCurrentUserId()
            val currentHouseholdGroupId = if (currentUserId != null) {
                userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId).first()?.householdGroupId?.takeIf { it.isNotEmpty() } ?: com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
            } else {
                com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
            }

            val snapshot = collection.get().await()
            val users = snapshot.toObjects(User::class.java).map { user ->
                user.copy(
                    // Use the user's existing householdGroupId from Firestore, or the current user's group as a fallback
                    householdGroupId = user.householdGroupId ?: currentHouseholdGroupId
                )
            }
            emit(Result.success(users))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    /**
     * Pushes a locally modified user to Firestore.
     * This is called by the sync manager.
     */
    suspend fun pushUserToFirestore(user: User): Boolean {
        return try {
            // Get the current user's ID to fetch their householdGroupId from the local DB as a fallback
            val currentUserId = getCurrentUserId()
            val currentHouseholdGroupId = if (currentUserId != null) {
                userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId).first()?.householdGroupId?.takeIf { it.isNotEmpty() } ?: com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
            } else {
                com.homeostasis.app.data.Constants.DEFAULT_GROUP_ID
            }

            // Ensure the user object has a householdGroupId and lastModifiedAt before pushing
            val userToPush = user.copy(
                // Use the user's existing householdGroupId, or the current user's group as a fallback
                householdGroupId = user.householdGroupId ?: currentHouseholdGroupId,
                lastModifiedAt = Timestamp.now(), // Always update lastModifiedAt on push
                needsSync = false // This flag is for local state, don't push it
            )
            set(userToPush.id, userToPush)
            true
        } catch (e: Exception) {
            // Log.e("UserRepository", "Error pushing user ${user.id} to Firestore", e)
            false
        }
    }



    /**
     * Uploads a profile picture to Firebase Storage.
     */
    suspend fun uploadProfilePicture(userId: String, localFile: java.io.File): String? {
        return firebaseStorageRepository.uploadFile(
            file = localFile,
            path = "profile_pictures/$userId", // Storage path
            fileName = "profile.jpg" // Fixed filename for the profile picture
        )
    }

    override fun getModelClass(): Class<User> = User::class.java
}