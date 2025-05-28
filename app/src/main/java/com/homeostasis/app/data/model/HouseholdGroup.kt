package com.homeostasis.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "household_groups")
data class HouseholdGroup(
    @PrimaryKey @DocumentId val id: String = "",
    val name: String = "",
    val ownerId: String = "", // User ID of the owner
    val createdAt: Timestamp = Timestamp.now()
)