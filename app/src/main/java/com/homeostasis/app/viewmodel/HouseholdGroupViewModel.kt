package com.homeostasis.app.viewmodel

import androidx.lifecycle.ViewModel
import com.homeostasis.app.data.HouseholdGroupRemoteDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HouseholdGroupViewModel @Inject constructor(
    private val householdGroupRemoteDataSource: HouseholdGroupRemoteDataSource
) : ViewModel() {
    suspend fun inviteMemberToHouseholdGroup(householdGroupId: String, inviteeEmail: String, inviterId: String): String {
        return householdGroupRemoteDataSource.inviteMemberToHouseholdGroup(householdGroupId, inviteeEmail, inviterId)
    }
}