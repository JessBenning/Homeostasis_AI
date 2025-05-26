package com.homeostasis.app.di

import com.homeostasis.app.data.remote.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
    fun provideTaskRepository(): TaskRepository {
        return TaskRepository()
    }
    
    @Provides
    @Singleton
    fun provideTaskHistoryRepository(): TaskHistoryRepository {
        return TaskHistoryRepository()
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
}