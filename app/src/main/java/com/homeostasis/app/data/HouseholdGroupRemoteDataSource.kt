package com.homeostasis.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.homeostasis.app.data.model.HouseholdGroup
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class HouseholdGroupRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun createHouseholdGroup(householdGroup: HouseholdGroup): String {
        return try {
            val documentReference = firestore.collection("householdGroups").add(householdGroup).await()
            documentReference.id
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun inviteMemberToHouseholdGroup(householdGroupId: String, inviteeEmail: String, inviterId: String): String {
        return try {
            val invitation = com.homeostasis.app.data.model.Invitation(
                householdGroupId = householdGroupId,
                inviteeEmail = inviteeEmail,
                inviterId = inviterId,
                status = com.homeostasis.app.data.model.InvitationStatus.PENDING
            )
            val documentReference = firestore.collection("invitations").add(invitation).await()
            documentReference.id
        } catch (e: Exception) {
            throw e
        }
    }
}