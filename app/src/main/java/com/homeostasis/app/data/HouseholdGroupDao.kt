package com.homeostasis.app.data

import androidx.room.*
import com.homeostasis.app.data.model.HouseholdGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface HouseholdGroupDao {
    @Query("SELECT * FROM household_groups")
    fun getAllHouseholdGroups(): Flow<List<HouseholdGroup>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHouseholdGroup(householdGroup: HouseholdGroup)

    @Update
    suspend fun updateHouseholdGroup(householdGroup: HouseholdGroup)

    @Delete
    suspend fun deleteHouseholdGroup(householdGroup: HouseholdGroup)
}