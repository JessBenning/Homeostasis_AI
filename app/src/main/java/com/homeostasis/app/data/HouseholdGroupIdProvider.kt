package com.homeostasis.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "household_group_id")

@Singleton
class HouseholdGroupIdProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val householdGroupIdKey = stringPreferencesKey("household_group_id")

    // Define the default household group ID as a constant
    companion object {
        const val DEFAULT_HOUSEHOLD_GROUP_ID = "default_group"
    }

    fun getHouseholdGroupId(): Flow<String?> {
        //TODO: remove this once table is available with dummy group
        return flowOf(DEFAULT_HOUSEHOLD_GROUP_ID) // Use the constant
//        return context.dataStore.data
//            .map { preferences ->
//                preferences[householdGroupIdKey]
//            }
    }

//    suspend fun setHouseholdGroupId(householdGroupId: String) {
//        context.dataStore.edit { preferences ->
//            preferences[householdGroupIdKey] = householdGroupId
//        }
//    }
}
