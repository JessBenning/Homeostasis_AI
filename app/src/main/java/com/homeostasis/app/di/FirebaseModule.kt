package com.homeostasis.app.di


import android.content.Context
import androidx.room.Room
import com.google.firebase.firestore.FirebaseFirestore
import com.homeostasis.app.data.AppDatabase
import com.homeostasis.app.data.TaskDao
import com.homeostasis.app.data.remote.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for providing Firebase-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    
    @Provides
    @Singleton
    fun provideUserRepository(): UserRepository {
        return UserRepository()
    }
    
    @Provides
    @Singleton
    fun provideTaskRepository(taskDao: TaskDao): TaskRepository {
        return TaskRepository(taskDao)
    }

    @Provides
    @Singleton
    fun provideTaskHistoryRepository(@ApplicationContext context: Context): TaskHistoryRepository {
        return TaskHistoryRepository(context)
    }


    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "app_database"
        ).build()
    }


    @Provides
    @Singleton
    fun provideCategoryRepository(): CategoryRepository {
        return CategoryRepository()
    }
    
    @Provides
    @Singleton
    fun provideShoppingListRepository(): ShoppingListRepository {
        return ShoppingListRepository()
    }
    
    @Provides
    @Singleton
    fun provideShoppingItemRepository(): ShoppingItemRepository {
        return ShoppingItemRepository()
    }
    
    @Provides
    @Singleton
    fun provideShoppingListItemRepository(): ShoppingListItemRepository {
        return ShoppingListItemRepository()
    }
    
    @Provides
    @Singleton
    fun provideSettingsRepository(): SettingsRepository {
        return SettingsRepository()
    }
    
    @Provides
    @Singleton
    fun provideUserSettingsRepository(): UserSettingsRepository {
        return UserSettingsRepository()
    }
    
    @Provides
    @Singleton
    fun provideResetHistoryRepository(): ResetHistoryRepository {
        return ResetHistoryRepository()
    }
    
    @Provides
    @Singleton
    fun provideFirebaseStorageRepository(): FirebaseStorageRepository {
        return FirebaseStorageRepository()
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    @Provides
    @Singleton
    fun provideTaskDao(appDatabase: AppDatabase): TaskDao {
        return appDatabase.taskDao()
    }

    
}