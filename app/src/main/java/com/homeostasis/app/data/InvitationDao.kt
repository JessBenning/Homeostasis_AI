package com.homeostasis.app.data

import androidx.room.*
import com.homeostasis.app.data.model.Invitation
import kotlinx.coroutines.flow.Flow

@Dao
interface InvitationDao {
    @Query("SELECT * FROM invitations")
    fun getAllInvitations(): Flow<List<Invitation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvitation(invitation: Invitation)

    @Update
    suspend fun updateInvitation(invitation: Invitation)

    @Delete
    suspend fun deleteInvitation(invitation: Invitation)
}