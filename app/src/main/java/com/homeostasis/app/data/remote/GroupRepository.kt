package com.homeostasis.app.data.remote

import android.util.Log
import com.google.firebase.Timestamp // Keep if used for local timestamp updates
import com.google.firebase.firestore.FieldValue // For server-side timestamps
import com.google.firebase.firestore.SetOptions // For SetOptions.merge()
import com.homeostasis.app.data.local.GroupDao
import com.homeostasis.app.data.model.Group
import com.homeostasis.app.data.model.toFirestoreMap

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val groupDao: GroupDao
    // Assuming FirebaseRepository base class provides 'firestore' or 'collection'
) : FirebaseRepository<Group>() { // Keep extending your base class

    override val collectionName: String = Group.COLLECTION // Or your defined constant for "groups"

    companion object {
        private const val TAG = "GroupRepository"
    }

    // --- NEW METHOD for Option 3 ---
    /**
     * Creates a new group document directly in Firestore.
     * This operation is awaited. It's intended to be called BEFORE local saving
     * when creating a brand new group, to ensure it exists remotely first.
     *
     * @param group The Group object to create.
     * @return True if the Firestore operation was successful, false otherwise.
     */
    suspend fun createGroupInFirestore(group: Group): Boolean {
        val specificTag = "$TAG-CreateFirestore"
        return try {
            // Use the 'collection' property from FirebaseRepository base class
            // The toFirestoreMap() helper ensures server timestamps and excludes local fields.
            collection.document(group.id)
                .set(group.toFirestoreMap(), SetOptions.merge()) // Use merge for safety
                .await()
            Log.d(specificTag, "Group ${group.id} successfully created/merged in Firestore.")
            true
        } catch (e: Exception) {
            Log.e(specificTag, "Error creating/merging group ${group.id} in Firestore", e)
            false
        }
    }

    // --- NEW/CONFIRMED METHOD for Option 3 ---
    /**
     * Inserts or updates a group in the local Room database.
     *
     * @param group The Group object to save locally.
     */
    suspend fun upsertGroupLocally(group: Group) {
        val specificTag = "$TAG-UpsertLocal"
        try {
            // Ensure groupDao.upsert is a suspend function
            groupDao.upsert(group)
            Log.d(specificTag, "Group ${group.id} upserted into local Room database. needsSync=${group.needsSync}")
        } catch (e: Exception) {
            Log.e(specificTag, "Error upserting group ${group.id} into local Room database", e)
            // Consider rethrowing or returning a result if error handling is needed here
        }
    }




    // --- EXISTING METHODS (Review and keep as needed) ---

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
        return groupDao.getAllGroups()
    }

    /**
     * Gets groups requiring sync from Room.
     */
    fun getGroupsRequiringSync(): Flow<List<Group>> {
        return groupDao.getGroupsRequiringSync()
    }

    /**
     * Pushes a locally modified Group to Firestore.
     * Intended to be called by the SyncManager when a group has needsSync = true.
     * This will use the toFirestoreMap helper to ensure server timestamps.
     */
    suspend fun pushGroupToFirestore(group: Group): Boolean {
        val specificTag = "$TAG-Push"
        return try {
            Log.d(specificTag, "Pushing Group ${group.id} (needsSync=${group.needsSync}) to Firestore.")
            // Use the 'collection' from FirebaseRepository and the toFirestoreMap helper
            collection.document(group.id)
                .set(group.toFirestoreMap(), SetOptions.merge()) // merge is good for updates
                .await()
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
        val specificTag = "$TAG-UpdateLocalAfterPush"
        if (firestoreSuccess) {
            val updatedGroup = group.copy(
                needsSync = false,
                // The lastModifiedAt from Firestore isn't easily available here without another read.
                // It's often acceptable for the local lastModifiedAt to be slightly different (client time of sync confirmation)
                // or you can choose to fetch the document again if exact server timestamp is critical locally.
                // For simplicity, we can update it to client's "now" or leave as is if Firestore's version is the source of truth.
                lastModifiedAt = Timestamp.now() // Or group.lastModifiedAt if you don't want to change it here
            )
            groupDao.upsert(updatedGroup)
            Log.d(specificTag, "Successfully updated local Group ${group.id} flags after Firestore push (needsSync=false).")
        } else {
            Log.e(specificTag, "Firestore push failed for Group ${group.id}. Local 'needsSync' flag remains true for retry.")
            // Optionally, ensure needsSync is explicitly true if there's any doubt
            // groupDao.upsert(group.copy(needsSync = true))
        }
    }

    override fun getModelClass(): Class<Group> = Group::class.java

    /**
     * Fetches a group by its ID directly from Firestore.
     * Uses the base FirebaseRepository's getById function.
     */
    suspend fun getGroupByIdFromFirestore(groupId: String): Group? {
        val specificTag = "$TAG-FetchFirestore"
        Log.d(specificTag, "Attempting to fetch Group with ID '$groupId' from Firestore collection '$collectionName'.")
        return try {
            val group = getById(groupId) // Assuming getById is from your FirebaseRepository base
            if (group != null) {
                Log.d(specificTag, "Successfully fetched Group with ID '${group.id}' from Firestore.")
            } else {
                Log.d(specificTag, "Group with ID '$groupId' not found in Firestore collection '$collectionName'.")
            }
            group
        } catch (e: Exception) {
            Log.e(specificTag, "Error fetching Group with ID '$groupId' from Firestore collection '$collectionName'.", e)
            null
        }
    }

    /**
     * Fetches a group by its ID directly from Firestore using the collection property.
     * This is an alternative if getById() isn't suitable or if you need more control.
     */
    suspend fun fetchRemoteGroup(groupId: String): Group? {
        val specificTag = "$TAG-FetchRemoteExplicit"
        Log.d(specificTag, "Attempting to fetch Group with ID '$groupId' from Firestore collection '$collectionName'.")
        try {
            val documentSnapshot = collection.document(groupId).get().await()
            val group = documentSnapshot.toObject(Group::class.java) // Ensure Group class is correctly structured for Firestore deserialization
            if (group != null) {
                Log.d(specificTag, "Successfully fetched Group with ID '${group.id}' from Firestore.")
            } else {
                Log.d(specificTag, "Group with ID '$groupId' not found in Firestore collection '$collectionName'.")
            }
            return group
        } catch (e: Exception) {
            Log.e(specificTag, "Error fetching remote group $groupId from Firestore.", e)
            return null
        }
    }
}