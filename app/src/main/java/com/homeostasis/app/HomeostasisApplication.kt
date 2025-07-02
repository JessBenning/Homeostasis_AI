package com.homeostasis.app

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.homeostasis.app.data.AppDatabase
import com.homeostasis.app.data.sync.FirebaseSyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Main application class for Homeostasis app.
 * Initializes Firebase and other app-wide components.
 */
@HiltAndroidApp
class HomeostasisApplication : Application() {

    @Inject
    lateinit var appDatabase: AppDatabase

    @Inject
    lateinit var firestore: FirebaseFirestore

    @Inject
    lateinit var taskRepository: com.homeostasis.app.data.remote.TaskRepository

    @Inject
    lateinit var firebaseSyncManager: FirebaseSyncManager

    override fun onCreate() {
        super.onCreate()

        try {
            // Initialize Firebase
            Log.d(TAG, "Initializing Firebase...")
            FirebaseApp.initializeApp(this)
            Log.d(TAG, "Firebase initialized successfully")

            // Configure Firestore settings for offline persistence
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            firestore.firestoreSettings = settings
            Log.d(TAG, "Firestore configured with offline persistence")

            // Verify Firestore connection with a simple operation
            firestore.collection("app_metadata").document("version")
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        Log.d(TAG, "Firestore connection verified: ${document.data}")
                    } else {
                        Log.d(TAG, "Firestore connected but version document doesn't exist")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error connecting to Firestore: ${e.message}", e)
                }

            // Initialize other app-wide components here
            // TODO: Initialize Room database
            // TODO: Initialize WorkManager for background tasks
            Log.d(TAG, "AppDatabase initialized successfully")



        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase: ${e.message}", e)
        }
    }

    companion object {
        const val TAG = "HomeostasisApp"
    }
}