package com.homeostasis.app.data

import com.homeostasis.app.data.model.HouseholdGroup
import com.homeostasis.app.data.model.Invitation
import com.homeostasis.app.data.model.Task
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Task::class, com.homeostasis.app.data.model.HouseholdGroup::class, com.homeostasis.app.data.model.Invitation::class, com.homeostasis.app.data.model.User::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun householdGroupDao(): HouseholdGroupDao
    abstract fun invitationDao(): InvitationDao
    abstract fun userDao(): UserDao
}