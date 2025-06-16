package com.homeostasis.app.data.remote

import android.util.Log
import com.google.firebase.Timestamp
// import com.google.firebase.firestore.FirebaseFirestore // REMOVED - Assuming FirebaseRepository provides access
import com.homeostasis.app.data.GroupDao
import com.homeostasis.app.data.model.Group
import kotlinx.coroutines.flow.Flow
// import kotlinx.coroutines.flow.first // Not directly used in the provided methods after change, but keep if other methods need it
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    // private val firestore: FirebaseFirestore, // REMOVED
    private val groupDao: GroupDao
) : FirebaseRepository<Group>() {

    override val collectionName: String = Group.COLLECTION

    /**
     * Creates a new group in Firestore and Room.
     * The user creating the group becomes the owner.
     */
    // Removed direct Firestore interaction from createGroup, updateGroup, and deleteGroup.
    // These operations should now be done directly on the local DAO from the ViewModel,
    // and the FirebaseSyncManager will handle pushing changes to Firestore.

    /**
     * Gets a group by its ID from Room.
     */
    fun getGroupById(groupId: String): Flow<Group?> {

        return groupDao.getGroupById(groupId)

    }

    /**
     * Gets all groups from Room.
     */
    fun getAllGroups(): Flow<List<Group>> {
        // Similar to getGroupById, consider if householdGroupId is needed for DAO call
        return groupDao.getAllGroups()
    }

    /**
     * Gets groups requiring sync from Room.
     */
    fun getGroupsRequiringSync(): Flow<List<Group>> {
        // Consider if householdGroupId is needed for DAO call
        return groupDao.getGroupsRequiringSync()
    }

    /**
     * Pushes a locally modified Group to Firestore.
     * Intended to be called by the SyncManager.
     */
    suspend fun pushGroupToFirestore(group: Group): Boolean {
        val specificTag = "$TAG-Push"
        return try {
            Log.d(specificTag, "Pushing Group ${group.id} to Firestore.")
            // Ensure the Group object being pushed is correctly prepared
            // (e.g., lastModifiedAt updated, needsSync possibly set to false for Firestore if not stored there)
            // For example: val groupToPush = group.copy(lastModifiedAt = Timestamp.now())
            collection.document(group.id).set(group, com.google.firebase.firestore.SetOptions.merge()).await()
            Log.d(specificTag, "Successfully pushed Group ${group.id} to Firestore.")
            true
        } catch (e: Exception) {
            Log.e(specificTag, "Error pushing Group ${group.id} to Firestore.", e)
            false
        }
    }

    /**
     * Updates the local Group entity after a successful Firestore push.
     * Intended to be called by the SyncManager.
     */
    suspend fun updateLocalGroupAfterPush(group: Group, firestoreSuccess: Boolean) {
        val specificTag = "$TAG-UpdateLocal"
        if (firestoreSuccess) {
            // Update the local group to set needsSync to false
            val updatedGroup = group.copy(
                needsSync = false,
                lastModifiedAt = Timestamp.now() // Update timestamp locally after successful sync
            )
            groupDao.upsert(updatedGroup) // Use the general upsert method
            Log.d(specificTag, "Successfully updated local Group ${group.id} flags after Firestore push.")
        } else {
            Log.e(specificTag, "Firestore push failed for Group ${group.id}. Local 'needsSync' flag remains true for retry.")
            // Optionally, you might want to re-set needsSync = true explicitly on the original object in Room
            // groupDao.upsert(group.copy(needsSync = true))
        }
    }

    override fun getModelClass(): Class<Group> = Group::class.java

    /**
     * Fetches a group by its ID directly from Firestore.
     */
    suspend fun getGroupByIdFromFirestore(groupId: String): Group? {
        val specificTag = "$TAG-FetchFirestore"
        Log.d(specificTag, "Attempting to fetch Group with ID '$groupId' from Firestore collection '$collectionName'.")
        return try {
            // Use the base FirebaseRepository's getById function
            val group = getById(groupId)
            if (group != null) {
                Log.d(specificTag, "Successfully fetched Group with ID '${group.id}' from Firestore.")
            } else {
                Log.d(specificTag, "Group with ID '$groupId' not found in Firestore collection '$collectionName'.")
            }
            group
        } catch (e: Exception) {
            Log.e(specificTag, "Error fetching Group with ID '$groupId' from Firestore collection '$collectionName'.", e)
            null // Return null in case of error
        }
    }

    companion object {
        private const val TAG = "GroupRepository"
    }
}