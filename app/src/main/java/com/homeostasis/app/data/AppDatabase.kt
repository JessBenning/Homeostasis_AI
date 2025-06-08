package com.homeostasis.app.data

import com.homeostasis.app.data.model.HouseholdGroup
import com.homeostasis.app.data.model.Invitation
import com.homeostasis.app.data.model.Task
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

//TODO: change exportSchema to true for production
@Database(
    entities = [Task::class, com.homeostasis.app.data.model.HouseholdGroup::class, com.homeostasis.app.data.model.Invitation::class, com.homeostasis.app.data.model.User::class, com.homeostasis.app.data.model.TaskHistory::class],
    version = 9,
    exportSchema = true
)
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
                ).addMigrations(
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9
                ).build()
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

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN needsSync INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE tasks ADD COLUMN isDeletedLocally INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add the new 'isCompleted' column to the 'tasks' table.
                // SQLite uses INTEGER for booleans (0 for false, 1 for true).
                // NOT NULL DEFAULT 0 sets new rows and existing rows (for this new column) to false.
                database.execSQL("ALTER TABLE tasks ADD COLUMN isCompleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE task_history ADD COLUMN needsSync INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN householdGroupId TEXT")
                database.execSQL("ALTER TABLE user ADD COLUMN householdGroupId TEXT")
                database.execSQL("ALTER TABLE task_history ADD COLUMN householdGroupId TEXT")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `household_groups` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

    }

}