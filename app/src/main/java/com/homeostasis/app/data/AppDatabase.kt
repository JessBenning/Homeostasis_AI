package com.homeostasis.app.data


import com.homeostasis.app.data.model.Task
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.homeostasis.app.data.local.GroupDao
import com.homeostasis.app.data.local.TaskDao
import com.homeostasis.app.data.local.TaskHistoryDao
import com.homeostasis.app.data.local.UserDao


@Database(
    entities = [Task::class, com.homeostasis.app.data.model.User::class, com.homeostasis.app.data.model.TaskHistory::class, com.homeostasis.app.data.model.Group::class], // Removed HouseholdGroup and Invitation entities
    version = 18, // Database version
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun userDao(): UserDao
    abstract fun taskHistoryDao(): TaskHistoryDao
    abstract fun groupDao(): GroupDao // Keep GroupDao

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
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18
                )
                    .build()
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
                // Add householdGroupId to tasks table as NOT NULL with a default value
                database.execSQL("ALTER TABLE tasks ADD COLUMN householdGroupId TEXT NOT NULL DEFAULT ''")
                // Add householdGroupId to user table as NOT NULL with a default value
                database.execSQL("ALTER TABLE user ADD COLUMN householdGroupId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE task_history ADD COLUMN householdGroupId TEXT NOT NULL DEFAULT ''")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `household_groups` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent()
                )

            }

        }


        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {

                database.execSQL("ALTER TABLE user ADD COLUMN needsSync INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE user ADD COLUMN isDeletedLocally INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {

                database.execSQL("ALTER TABLE user ADD COLUMN lastModifiedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add the new 'isDeletedLocally' column to the 'task_history' table.
                // SQLite uses INTEGER for booleans (0 for false, 1 for true).
                // NOT NULL DEFAULT 0 sets new rows and existing rows (for this new column) to false.
                database.execSQL("ALTER TABLE task_history ADD COLUMN isDeletedLocally INTEGER NOT NULL DEFAULT 0")
            }
        }

        // In your AppDatabase.kt
        val MIGRATION_12_13: Migration = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF") // Temporarily disable foreign key constraints for table manipulation

                // 1. Create the new table with the expected schema
                // Note: isDeleted, isCompleted, needsSync, isDeletedLocally are INTEGER NOT NULL
                // createdAt and lastModifiedAt are INTEGER NULL (for nullable Timestamps)
                db.execSQL(
                    """
            CREATE TABLE tasks_new (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                points INTEGER NOT NULL,
                categoryId TEXT NOT NULL,
                createdBy TEXT NOT NULL,
                isDeleted INTEGER NOT NULL DEFAULT 0, 
                createdAt INTEGER,                  -- Now Nullable
                lastModifiedAt INTEGER,             -- Now Nullable
                needsSync INTEGER NOT NULL DEFAULT 0,
                isDeletedLocally INTEGER NOT NULL DEFAULT 0,
                isCompleted INTEGER NOT NULL DEFAULT 0,
                householdGroupId TEXT NOT NULL DEFAULT '' 
            )
        """.trimIndent()
                )

                // 2. Copy data from the old table to the new table
                // Ensure all columns from the old 'tasks' table are selected.
                // The old table had not-null createdAt and lastModifiedAt, so they will have values.
                db.execSQL(
                    """
            INSERT INTO tasks_new (id, title, description, points, categoryId, createdBy, isDeleted, createdAt, lastModifiedAt, needsSync, isDeletedLocally, isCompleted, householdGroupId)
            SELECT id, title, description, points, categoryId, createdBy, isDeleted, createdAt, lastModifiedAt, needsSync, isDeletedLocally, isCompleted, householdGroupId FROM tasks
        """.trimIndent()
                )

                // 3. Drop the old tasks table
                db.execSQL("DROP TABLE tasks")

                // 4. Rename the new table to 'tasks'
                db.execSQL("ALTER TABLE tasks_new RENAME TO tasks")

                db.execSQL("PRAGMA foreign_keys=ON") // Re-enable foreign key constraints
                Log.d("Migration", "Successfully migrated tasks table from version 12 to 13.")
            }
        }

        // Migration from version 13 to 14
        val MIGRATION_13_14: Migration = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create the new 'groups' table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `groups` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `ownerId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `lastModifiedAt` INTEGER NOT NULL,
                        `needsSync` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent()
                )

                // --- Migrate 'tasks' table: Remove 'createdBy', Add 'ownerId' ---
                // 1. Create the new tasks table with the desired schema (including ownerId, excluding createdBy)
                // Ensure all other existing columns are included.
                db.execSQL(
                    """
                    CREATE TABLE tasks_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        points INTEGER NOT NULL,
                        categoryId TEXT NOT NULL,
                        ownerId TEXT NOT NULL DEFAULT '', -- Add new ownerId column with a default value
                        isDeleted INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER,
                        lastModifiedAt INTEGER,
                        needsSync INTEGER NOT NULL DEFAULT 0,
                        isDeletedLocally INTEGER NOT NULL DEFAULT 0,
                        isCompleted INTEGER NOT NULL DEFAULT 0,
                        householdGroupId TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent()
                )

                // 2. Copy data from the old tasks table to the new tasks_new table
                // Select all columns from the old table *except* createdBy.
                // Provide a default value for the new ownerId column during the copy.
                db.execSQL(
                    """
                    INSERT INTO tasks_new (id, title, description, points, categoryId, isDeleted, createdAt, lastModifiedAt, needsSync, isDeletedLocally, isCompleted, householdGroupId, ownerId)
                    SELECT id, title, description, points, categoryId, isDeleted, createdAt, lastModifiedAt, needsSync, isDeletedLocally, isCompleted, householdGroupId, '' -- Provide a default value for ownerId
                    FROM tasks
                """.trimIndent()
                )

                // 3. Drop the old tasks table
                db.execSQL("DROP TABLE tasks")

                // 4. Rename the new table to tasks
                db.execSQL("ALTER TABLE tasks_new RENAME TO tasks")

                Log.d("Migration", "Successfully migrated tasks table for version 14.")
            }
        }
        val MIGRATION_14_15: Migration = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQL command to add the new 'profileImageHashSignature' column to the 'users' table.
                // It's a TEXT column because the hash is a String.
                // It can be NULL because a user might not have a profile picture.
                db.execSQL("ALTER TABLE user ADD COLUMN profileImageHashSignature TEXT")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i("RoomMigration", "Migrating schema from 15 to 16: Adding profileImageHashSignature to user table.")
                // Add the new column 'profileImageHashSignature'.
                // It's a nullable TEXT column (String?).
               // db.execSQL("ALTER TABLE user ADD COLUMN profileImageHashSignature TEXT")

            }
        }
        val MIGRATION_16_17: Migration = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Step 1: Create the new temporary table "user_new"
                db.execSQL("""
            CREATE TABLE user_new (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                profileImageUrl TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                lastActive INTEGER NOT NULL,
                lastResetScore INTEGER NOT NULL,
                resetCount INTEGER NOT NULL,
                householdGroupId TEXT NOT NULL,
                lastModifiedAt INTEGER,        
                profileImageHashSignature TEXT,
                needsSync INTEGER NOT NULL DEFAULT 0, 
                isDeletedLocally INTEGER NOT NULL DEFAULT 0 
            )
        """.trimIndent())

                // Step 2: Copy data and provide defaults for new columns
                db.execSQL("""
            INSERT INTO user_new (
                id, name, profileImageUrl, createdAt, lastActive,
                lastResetScore, resetCount, householdGroupId,
                lastModifiedAt, profileImageHashSignature,
                needsSync, isDeletedLocally
            )
            SELECT
                id, name, profileImageUrl, createdAt, lastActive,
                lastResetScore, resetCount, householdGroupId,
                lastModifiedAt, profileImageHashSignature,
                0, 
                0  
            FROM user
        """.trimIndent())

                // Step 3: Drop the old "user" table
                db.execSQL("DROP TABLE user")

                // Step 4: Rename "user_new" to "user"
                db.execSQL("ALTER TABLE user_new RENAME TO user")
            }
        }

        val MIGRATION_17_18: Migration = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE user ADD COLUMN needsProfileImageUpload INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

    }
}