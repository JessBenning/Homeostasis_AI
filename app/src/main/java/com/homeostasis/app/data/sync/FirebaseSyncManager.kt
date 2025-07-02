package com.homeostasis.app.data.sync

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.homeostasis.app.data.Constants
import com.homeostasis.app.data.remote.UserSettingsRepository
import com.homeostasis.app.data.local.UserDao
import com.homeostasis.app.data.model.User
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSyncManager @Inject constructor(
    private val localToRemoteSyncHandler: LocalToRemoteSyncHandler,
    private val remoteToLocalSyncHandler: RemoteToLocalSyncHandler,
    private val firebaseAuth: FirebaseAuth,
    private val userDao: UserDao,
    private val userPreferencesRepository: UserSettingsRepository, // To observe current user ID
    @ApplicationContext private val context: Context
) {
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncObservationJob: Job? = null

    private var currentUserId: String? = null
    private var currentHouseholdGroupId: String? = null

    private val _isSyncActive = MutableLiveData<Boolean>(false)
    val isSyncActive: LiveData<Boolean> = _isSyncActive

    private val _lastSyncTimestamp = MutableLiveData<Long?>()
    val lastSyncTimestamp: LiveData<Long?> = _lastSyncTimestamp

    private val _syncStatusMessage = MutableLiveData<String?>()
    val syncStatusMessage: LiveData<String?> = _syncStatusMessage

    companion object {
        private const val TAG = "FirebaseSyncManager"
    }

    init {
        observeUserAndHouseholdChanges()
    }

    private fun observeUserAndHouseholdChanges() {
        syncObservationJob?.cancel() // Cancel any previous observation
        syncObservationJob = managerScope.launch {
            // Observe the currently logged-in user ID from UserPreferencesRepository or FirebaseAuth
            // For this example, using FirebaseAuth's authStateListener is more direct for login/logout
            firebaseAuth.addAuthStateListener { auth ->
                val firebaseUser = auth.currentUser
                val newUserId = firebaseUser?.uid

                if (newUserId != currentUserId) {
                    Log.i(TAG, "User auth state changed. Old: $currentUserId, New: $newUserId")
                    currentUserId = newUserId
                    updateSyncStateBasedOnUser()
                }
            }
            // Initial check in case listener isn't fired immediately
            if (currentUserId == null) { // Check if already set by listener
                currentUserId = firebaseAuth.currentUser?.uid
                updateSyncStateBasedOnUser()
            }
        }
    }

    private fun updateSyncStateBasedOnUser() {
        managerScope.launch { // Launch a new coroutine for this logic
            if (currentUserId != null) {
                _syncStatusMessage.postValue("User logged in: $currentUserId. Initializing sync...")
                Log.i(TAG, "User logged in: $currentUserId. Setting up household observer.")

                // Fetch initial user data to ensure householdGroupId is available locally if possible
                // This also handles the case where the user document might not exist locally yet.
                val initialUser = remoteToLocalSyncHandler.fetchAndCacheRemoteUser(currentUserId!!)
                currentHouseholdGroupId = initialUser?.householdGroupId ?: Constants.DEFAULT_GROUP_ID
                Log.d(TAG, "Initial user fetch complete. Household Group ID: $currentHouseholdGroupId for user $currentUserId")

                // Start observing the user's household group ID from the local DAO
                // This flow will react to changes if the user joins/leaves a group
                userDao.getUserByIdWithoutHouseholdIdFlow(currentUserId!!)
                    .map { user -> user?.householdGroupId ?: Constants.DEFAULT_GROUP_ID }
                    .distinctUntilChanged()
                    .collectLatest { householdId ->
                        Log.i(TAG, "Household Group ID changed for user $currentUserId to: $householdId. Restarting sync handlers.")
                        currentHouseholdGroupId = householdId
                        restartSyncHandlers()
                        _isSyncActive.postValue(true)
                        _syncStatusMessage.postValue("Sync active for user $currentUserId in household $currentHouseholdGroupId.")
                    }
            } else {
                Log.i(TAG, "User logged out. Stopping all sync handlers.")
                stopAllSyncing()
                _isSyncActive.postValue(false)
                _syncStatusMessage.postValue("User logged out. Sync stopped.")
                currentHouseholdGroupId = null
            }
        }
    }


    private suspend fun restartSyncHandlers() {
        val userId = currentUserId
        val householdId = currentHouseholdGroupId

        if (userId == null) {
            Log.w(TAG, "Cannot start sync handlers: User ID is null.")
            stopAllSyncing() // Ensure everything is stopped if user becomes null
            return
        }

        Log.i(TAG, "Restarting sync handlers for User: $userId, Household: $householdId")

        // Stop existing first to ensure clean state
        remoteToLocalSyncHandler.stopObservingRemoteChanges()
        localToRemoteSyncHandler.stopObservingLocalChanges()

        // Start remote-to-local sync
        remoteToLocalSyncHandler.startObservingRemoteChanges(userId, householdId)

        // Start local-to-remote sync
        localToRemoteSyncHandler.startObservingLocalChanges(userId, householdId)

        _lastSyncTimestamp.postValue(System.currentTimeMillis())
        Log.i(TAG, "Sync handlers restarted for User: $userId, Household: $householdId")
    }

    private fun stopAllSyncing() {
        Log.i(TAG, "Stopping all sync operations.")
        remoteToLocalSyncHandler.stopObservingRemoteChanges()
        localToRemoteSyncHandler.stopObservingLocalChanges()
        _isSyncActive.postValue(false)
    }

    /**
     * Call this when the application is shutting down or user is definitively logged out
     * beyond just the auth state listener's scope (e.g., in Application.onTerminate or ViewModel.onCleared).
     */
    fun shutdown() {
        Log.i(TAG, "FirebaseSyncManager shutting down.")
        syncObservationJob?.cancel()
        stopAllSyncing()
        managerScope.cancel() // Cancel the manager's own scope
    }

    /**
     * Manually triggers a one-time push of all pending local data to Firestore.
     * This is useful for a "Sync Now" button or specific events.
     */
    fun triggerManualSyncPush() {
        val userId = currentUserId
        val householdId = currentHouseholdGroupId
        if (userId == null) {
            Log.w(TAG, "Cannot trigger manual sync: User not logged in.")
            _syncStatusMessage.postValue("Sync failed: User not logged in.")
            return
        }
        managerScope.launch {
            _syncStatusMessage.postValue("Manual sync initiated...")
            Log.i(TAG, "Manual sync push triggered for User: $userId, Household: $householdId.")
            localToRemoteSyncHandler.pushAllPendingData(userId, householdId)
            _lastSyncTimestamp.postValue(System.currentTimeMillis())
            _syncStatusMessage.postValue("Manual sync completed.")
            Log.i(TAG, "Manual sync push completed.")
        }
    }

    /**
     * Manually triggers a one-time fetch of the current user's data and their household group data.
     * Could be expanded to fetch all household data.
     */
    suspend fun triggerManualSyncPull() {
        val userId = currentUserId
        if (userId == null) {
            Log.w(TAG, "Cannot trigger manual pull: User not logged in.")
            _syncStatusMessage.postValue("Sync pull failed: User not logged in.")
            return
        }
        withContext(Dispatchers.IO) { // Ensure running on IO dispatcher
            _syncStatusMessage.postValue("Manual data refresh initiated...")
            Log.i(TAG, "Manual data pull triggered for User: $userId.")
            val user = remoteToLocalSyncHandler.fetchAndCacheRemoteUser(userId)
            if (user?.householdGroupId != null && user.householdGroupId != Constants.DEFAULT_GROUP_ID) {
                remoteToLocalSyncHandler.fetchAndCacheGroupById(userId, user.householdGroupId)
            }
            // Optionally, you could re-trigger the initial fetch part of the generic listeners
            // but that's more involved. A targeted fetch is simpler for a manual pull.
            _lastSyncTimestamp.postValue(System.currentTimeMillis())
            _syncStatusMessage.postValue("Manual data refresh completed.")
            Log.i(TAG, "Manual data pull completed.")
        }
    }

    /**
     * To be called when the current user's household group ID might have changed
     * due to an action outside the direct observation flow (e.g., after a user creates/joins a group via a dialog,
     * and the local DB update might not have propagated yet or you want to force a re-check).
     *
     * This will effectively re-evaluate the household group and restart sync handlers if needed.
     */
    fun reEvaluateUserHousehold() {
        Log.i(TAG, "Re-evaluating user household state.")
        // This will trigger the collection in observeUserAndHouseholdChanges if the ID actually changed,
        // or just re-confirm the current state.
        // The current implementation of updateSyncStateBasedOnUser already handles fetching and observing.
        // We just need to ensure that the current user ID is processed again if it's not null.
        if (currentUserId != null) {
            managerScope.launch { // Ensure this runs in the manager's scope
                updateSyncStateBasedOnUser()
            }
        } else {
            Log.w(TAG, "Cannot re-evaluate household: User not logged in.")
        }
    }

    // --- Potential callback methods if lower-level handlers need to signal critical events ---
    // Example:
    // fun onCurrentUserDocumentDeletedRemotely() {
    // managerScope.launch {
    // Log.e(TAG, "CRITICAL: Current user document was deleted remotely! Forcing logout.")
    // stopAllSyncing()
    // currentUserId = null
    // currentHouseholdGroupId = null
    // _isSyncActive.postValue(false)
    // _syncStatusMessage.postValue("Error: User account deleted. Please log in again.")
    // // Here you would typically also clear local user session / preferences
    // // and navigate the user to the login screen.
    // // For simplicity, this example just logs and stops sync.
    // }
    // }
}