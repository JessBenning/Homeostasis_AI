package com.homeostasis.app.data.remote

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/**
 * Base repository class for Firebase Firestore operations.
 */
abstract class FirebaseRepository<T> {

    protected abstract val collectionName: String
    
    protected val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }
    
    protected val collection: CollectionReference by lazy {
        firestore.collection(collectionName)
    }
    
    /**
     * Get a document by ID.
     */
    suspend fun getById(id: String): T? {
        return try {
            collection.document(id).get().await().toObject(getModelClass())
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get all documents in the collection.
     */
    suspend fun getAll(): List<T> {
        return try {
            collection.get().await().toObjects(getModelClass())
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get all documents in the collection as a Flow.
     */
    fun getAllAsFlow(): Flow<List<T>> = callbackFlow {
        val listenerRegistration = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            
            if (snapshot != null) {
                val items = snapshot.toObjects(getModelClass())
                trySend(items)
            }
        }
        
        awaitClose { listenerRegistration.remove() }
    }
    
    /**
     * Add a new document to the collection.
     * item of type T
     */
    suspend fun add(item: T): String? {
        return try {
            val documentReference = collection.add(item as Any).await()
            documentReference.id
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Set a document with a specific ID.
     * item of type T
     */
    suspend fun set(id: String, item: T): Boolean {
        return try {
            collection.document(id).set(item as Any).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Update a document with a specific ID.
     */
    suspend fun update(id: String, updates: Map<String, Any?>): Boolean {
        return try {
            collection.document(id).update(updates).await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Delete a document with a specific ID.
     */
    suspend fun delete(id: String): Boolean {
        return try {
            collection.document(id).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get a document by ID as a Flow.
     */
    fun getByIdAsFlow(id: String): Flow<T?> = callbackFlow {
        val listenerRegistration = collection.document(id).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            
            if (snapshot != null) {
                val item = snapshot.toObject(getModelClass())
                trySend(item)
            }
        }
        
        awaitClose { listenerRegistration.remove() }
    }
    
    /**
     * Get the class of the model.
     */
    protected abstract fun getModelClass(): Class<T>
}