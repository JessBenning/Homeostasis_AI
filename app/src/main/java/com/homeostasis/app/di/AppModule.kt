package com.homeostasis.app.di

import android.app.Application
import androidx.room.Room
import com.google.firebase.firestore.FirebaseFirestore
import com.homeostasis.app.data.AppDatabase
import com.homeostasis.app.data.HouseholdGroupDao
import com.homeostasis.app.data.HouseholdGroupRemoteDataSource
import com.homeostasis.app.data.InvitationDao
import com.homeostasis.app.data.TaskDao
import com.homeostasis.app.data.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideAppDatabase(app: Application): AppDatabase {
        return Room.databaseBuilder(
            app,
            AppDatabase::class.java,
            "homeostasis_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Singleton
    @Provides
    fun provideTaskDao(db: AppDatabase): TaskDao {
        return db.taskDao()
    }

    @Singleton
    @Provides
    fun provideHouseholdGroupDao(db: AppDatabase): HouseholdGroupDao {
        return db.householdGroupDao()
    }

    @Singleton
    @Provides
    fun provideInvitationDao(db: AppDatabase): InvitationDao {
        return db.invitationDao()
    }

    @Singleton
    @Provides
    fun provideHouseholdGroupRemoteDataSource(firestore: FirebaseFirestore): HouseholdGroupRemoteDataSource {
        return HouseholdGroupRemoteDataSource(firestore)
    }

    
        @Singleton
        @Provides
        fun provideFirebaseFirestore(): FirebaseFirestore {
            return FirebaseFirestore.getInstance()
        }
    
        @Singleton
        @Provides
        fun provideUserDao(db: AppDatabase): UserDao {
            return db.userDao()
        }
    }