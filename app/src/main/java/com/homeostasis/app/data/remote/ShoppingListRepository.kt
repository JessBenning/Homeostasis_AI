package com.homeostasis.app.data.remote

import com.google.firebase.Timestamp
import com.homeostasis.app.data.model.ShoppingList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await

/**
 * Repository for shopping list-related operations.
 */
class ShoppingListRepository : FirebaseRepository<ShoppingList>() {
    
    override val collectionName: String = ShoppingList.COLLECTION
    
    /**
     * Create a new shopping list.
     */
    suspend fun createShoppingList(shoppingList: ShoppingList): String? {
        return try {
            val shoppingListWithTimestamp = shoppingList.copy(
                createdAt = Timestamp.now(),
                lastModifiedAt = Timestamp.now()
            )
            add(shoppingListWithTimestamp)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get shopping lists created by a specific user.
     */
    suspend fun getShoppingListsByUser(userId: String): List<ShoppingList> {
        return try {
            collection
                .whereEqualTo("createdBy", userId)
                .whereEqualTo("isDeleted", false)
                .get()
                .await()
                .toObjects(ShoppingList::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get shopping lists created by a specific user as a Flow.
     */
    fun getShoppingListsByUserAsFlow(userId: String): Flow<List<ShoppingList>> = callbackFlow {
        val listenerRegistration = collection
            .whereEqualTo("createdBy", userId)
            .whereEqualTo("isDeleted", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val shoppingLists = snapshot.toObjects(ShoppingList::class.java)
                    trySend(shoppingLists)
                }
            }
        
        awaitClose { listenerRegistration.remove() }
    }
    
    /**
     * Create default shopping lists for a new user.
     */
    suspend fun createDefaultShoppingLists(userId: String): List<String> {
        return try {
            val defaultShoppingLists = ShoppingList.DEFAULT_SHOPPING_LISTS.map { shoppingList ->
                shoppingList.copy(
                    createdBy = userId,
                    createdAt = Timestamp.now(),
                    lastModifiedAt = Timestamp.now()
                )
            }
            
            val shoppingListIds = mutableListOf<String>()
            
            // Add each shopping list and collect the IDs
            defaultShoppingLists.forEach { shoppingList ->
                val id = add(shoppingList)
                if (id != null) {
                    shoppingListIds.add(id)
                }
            }
            
            shoppingListIds
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Update a shopping list.
     */
    suspend fun updateShoppingList(shoppingListId: String, name: String): Boolean {
        return try {
            val updates = mapOf(
                "name" to name,
                "lastModifiedAt" to Timestamp.now()
            )
            update(shoppingListId, updates)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Soft delete a shopping list.
     */
    suspend fun softDeleteShoppingList(shoppingListId: String): Boolean {
        return try {
            val updates = mapOf(
                "isDeleted" to true,
                "lastModifiedAt" to Timestamp.now()
            )
            update(shoppingListId, updates)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get all active (non-deleted) shopping lists.
     */
    suspend fun getActiveShoppingLists(): List<ShoppingList> {
        return try {
            collection
                .whereEqualTo("isDeleted", false)
                .get()
                .await()
                .toObjects(ShoppingList::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get all active shopping lists as a Flow.
     */
    fun getActiveShoppingListsAsFlow(): Flow<List<ShoppingList>> = callbackFlow {
        val listenerRegistration = collection
            .whereEqualTo("isDeleted", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val shoppingLists = snapshot.toObjects(ShoppingList::class.java)
                    trySend(shoppingLists)
                }
            }
        
        awaitClose { listenerRegistration.remove() }
    }
    
    override fun getModelClass(): Class<ShoppingList> = ShoppingList::class.java
}