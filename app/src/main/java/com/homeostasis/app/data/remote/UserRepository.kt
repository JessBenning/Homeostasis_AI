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
    suspend fun registerUser(email: String, password: String, name: String): Result<FirebaseUser> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
            val currentHouseholdGroupId = householdGroupIdProvider.getHouseholdGroupId().first()
            val now = Timestamp.now() // Get current timestamp once

            // Update display name in Firebase Auth
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build()
            firebaseUser?.updateProfile(profileUpdates)?.await()

            // Create user document in Firestore and local DB
            firebaseUser?.let {
                val user = User(
                    id = it.uid,
                    name = name,
                    // email = it.email, // Add 'email: String? = null' to your User data class if you want to store it
                    profileImageUrl = "", // Or a default placeholder URL
                    createdAt = now,
                    lastActive = now,
                    lastResetScore = 0, // Default value
                    resetCount = 0,    // Default value
                    householdGroupId = currentHouseholdGroupId ?: HouseholdGroupIdProvider.DEFAULT_HOUSEHOLD_GROUP_ID,
                    needsSync = false, // Just created and synced from auth/firestore
                    isDeletedLocally = false,
                    lastModifiedAt = now
                )
                // Save to Firestore (via base class)
                set(it.uid, user) // 'set' from FirebaseRepository should handle the whole user object
                // Save to local Room DB
                userDao.upsertUser(user)
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
            authResult.user?.uid?.let { userId ->
                val firestoreUserResult = getUserFromFirestore(userId) // This already handles householdGroupId internally for the returned User object
                if (firestoreUserResult.isSuccess) {
                    firestoreUserResult.getOrNull()?.let { userFromFirestore ->
                        // The userFromFirestore object will have householdGroupId populated by getUserFromFirestore
                        // We still ensure it's non-null before upserting, using the provider as a fallback if necessary.
                        val userToCache = userFromFirestore.copy(
                            householdGroupId = userFromFirestore.householdGroupId ?: householdGroupIdProvider.getHouseholdGroupId().first() ?: HouseholdGroupIdProvider.DEFAULT_HOUSEHOLD_GROUP_ID,
                            needsSync = false // Data is fresh from Firestore
                        )
                        userDao.upsertUser(userToCache)
                    }
                }
            }
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
     * Update user profile in Firestore and local DB.
     * The `updates` map should contain fields that are part of the User model.
     * This will fetch the full user object after update to ensure local cache is accurate.
     */
    suspend fun updateUserProfile(userId: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            // It's good practice to add/update a 'lastModifiedAt' field during any update
            val updatesWithTimestamp = updates.toMutableMap()
            updatesWithTimestamp["lastModifiedAt"] = Timestamp.now()

            collection.document(userId).update(updatesWithTimestamp).await()

            // Fetch the updated user from Firestore to ensure local DB is consistent
            val updatedUserResult = getUserFromFirestore(userId) // This fetches with correct householdGroupId handling for the User object
            if (updatedUserResult.isSuccess) {
                updatedUserResult.getOrNull()?.let { userFromFirestore ->
                    val userToCache = userFromFirestore.copy(
                        // Ensure householdGroupId is non-null, even if it came from Firestore as null
                        householdGroupId = userFromFirestore.householdGroupId ?: householdGroupIdProvider.getHouseholdGroupId().first() ?: HouseholdGroupIdProvider.DEFAULT_HOUSEHOLD_GROUP_ID,
                        needsSync = false // Data is fresh from Firestore
                    )
                    userDao.upsertUser(userToCache)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get user by ID. Attempts to fetch from local DAO first, then Firestore.
     * This is the corrected version.
     */
    suspend fun getUser(userId: String): User? {
        // First, get the current householdGroupId
        val currentHouseholdGroupId = householdGroupIdProvider.getHouseholdGroupId().first()
            ?: HouseholdGroupIdProvider.DEFAULT_HOUSEHOLD_GROUP_ID // Fallback if provider returns null

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
                    // preferring its own, then provider's, then default.
                    // This is important if userFromFirestore could somehow have a different group ID
                    // than currentHouseholdGroupId, though typically they should align for the current user.
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
        val currentHouseholdGroupId = householdGroupIdProvider.getHouseholdGroupId().first()
            ?: HouseholdGroupIdProvider.DEFAULT_HOUSEHOLD_GROUP_ID // Or your appropriate default

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
                // Ensure householdGroupId is populated if it comes as null from Firestore
                val finalHouseholdGroupId = userDocument.householdGroupId
                    ?: householdGroupIdProvider.getHouseholdGroupId().first()
                    ?: HouseholdGroupIdProvider.DEFAULT_HOUSEHOLD_GROUP_ID
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
            val users = collection.get().await().toObjects(User::class.java).map { user ->
                user.copy(
                    householdGroupId = user.householdGroupId
                        ?: householdGroupIdProvider.getHouseholdGroupId().first()
                        ?: HouseholdGroupIdProvider.DEFAULT_HOUSEHOLD_GROUP_ID
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
            val snapshot = collection.get().await()
            val users = snapshot.toObjects(User::class.java).map { user ->
                user.copy(
                    householdGroupId = user.householdGroupId
                        ?: householdGroupIdProvider.getHouseholdGroupId().first()
                        ?: HouseholdGroupIdProvider.DEFAULT_HOUSEHOLD_GROUP_ID
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
            // Ensure the user object has a householdGroupId and lastModifiedAt before pushing
            val userToPush = user.copy(
                householdGroupId = user.householdGroupId
                    ?: householdGroupIdProvider.getHouseholdGroupId().first()
                    ?: HouseholdGroupIdProvider.DEFAULT_HOUSEHOLD_GROUP_ID,
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