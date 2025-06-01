package com.homeostasis.app.data

import com.homeostasis.app.data.model.HouseholdGroup
import com.homeostasis.app.data.model.Invitation
import com.homeostasis.app.data.model.Task
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import androidx.room.Room
//TODO: change exportSchema to true for production
@Database(entities = [Task::class, com.homeostasis.app.data.model.HouseholdGroup::class, com.homeostasis.app.data.model.Invitation::class, com.homeostasis.app.data.model.User::class, com.homeostasis.app.data.model.TaskHistory::class], version = 5, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun householdGroupDao(): HouseholdGroupDao
    abstract fun invitationDao(): InvitationDao
    abstract fun userDao(): UserDao
    abstract fun taskHistoryDao(): TaskHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                ).addMigrations(MIGRATION_4_5).build()
                INSTANCE = instance
                instance
            }
        }
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks DROP COLUMN completionCount")
                database.execSQL("ALTER TABLE tasks DROP COLUMN lastCompletedAt")
                database.execSQL("ALTER TABLE tasks DROP COLUMN lastCompletedBy")
                database.execSQL("ALTER TABLE tasks DROP COLUMN completionHistory")
            }
        }

    }

}