package com.homeostasis.app.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.homeostasis.app.data.AppDatabase
import com.google.firebase.firestore.DocumentChange
import kotlinx.coroutines.flow.collect
import com.homeostasis.app.data.model.Task
import android.net.Uri

class FirebaseSyncManager(
    private val db: AppDatabase,
    private val firestore: FirebaseFirestore
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        Log.d("FirebaseSyncManager", "Initialized")
    }

    fun syncTasks() {
        scope.launch {
            db.taskDao().getAllTasks().collect { tasks ->
                for (task in tasks) {
                    firestore.collection("tasks").document(task.id).set(task)
                        .addOnSuccessListener {
                            Log.d("FirebaseSyncManager", "Task synced successfully: ${task.id}")
                        }
                        .addOnFailureListener { e ->
                            Log.e("FirebaseSyncManager", "Error syncing task: ${task.id}", e)
                        }
                }
            }

            firestore.collection("tasks")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        Log.w("FirebaseSyncManager", "Listen failed.", e)
                        return@addSnapshotListener
                    }

                    if (snapshots != null) {
                        for (dc in snapshots.documentChanges) {
                            when (dc.type) {
                                DocumentChange.Type.ADDED -> {
                                    val task = dc.document.toObject(Task::class.java)
                                    scope.launch {
                                        db.taskDao().insertTask(task)
                                    }
                                    Log.d("FirebaseSyncManager", "New task: ${task.id}")
                                }
                                DocumentChange.Type.MODIFIED -> {
                                    val task = dc.document.toObject(Task::class.java)
                                    scope.launch {
                                        db.taskDao().updateTask(task)
                                    }
                                    Log.d("FirebaseSyncManager", "Modified task: ${task.id}")
                                }
                                DocumentChange.Type.REMOVED -> {
                                    val task = dc.document.toObject(Task::class.java)
                                    scope.launch {
                                        db.taskDao().deleteTask(task)
                                    }
                                    Log.d("FirebaseSyncManager", "Removed task: ${task.id}")
                                }
                            }
                        }
                    }
                }
        }
    }

    fun syncHouseholdGroups() {
        scope.launch {
            // TODO: Implement household group synchronization logic
        }
    }

    fun syncInvitations() {
        scope.launch {
            // TODO: Implement invitation synchronization logic
        }
    }

    fun syncUsers() {
        scope.launch {
            // TODO: Implement user synchronization logic
        }
    }
}