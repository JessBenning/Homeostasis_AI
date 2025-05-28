package com.homeostasis.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}

@Entity(tableName = "invitations")
data class Invitation(
    @PrimaryKey @DocumentId val id: String = "",
    val householdGroupId: String = "",
    val inviterId: String = "", // User ID of the inviter
    val inviteeEmail: String = "",
    val status: InvitationStatus = InvitationStatus.PENDING,
    val createdAt: Timestamp = Timestamp.now()
)