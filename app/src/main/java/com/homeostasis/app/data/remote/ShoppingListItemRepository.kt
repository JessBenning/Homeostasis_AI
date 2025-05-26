package com.homeostasis.app.data.remote

import com.google.firebase.Timestamp
import com.homeostasis.app.data.model.ShoppingListItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await

/**
 * Repository for shopping list item-related operations.
 */
class ShoppingListItemRepository : FirebaseRepository<ShoppingListItem>() {
    
    override val collectionName: String = ShoppingListItem.COLLECTION
    
    /**
     * Add an item to a shopping list.
     */
    suspend fun addItemToList(
        itemId: String,
        listId: String,
        userId: String,
        quantity: String? = null,
        notes: String? = null
    ): String? {
        return try {
            val shoppingListItem = ShoppingListItem(
                itemId = itemId,
                listId = listId,
                quantity = quantity,
                notes = notes,
                addedBy = userId,
                addedAt = Timestamp.now(),
                lastModifiedAt = Timestamp.now()
            )
            add(shoppingListItem)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get items in a specific shopping list.
     */
    suspend fun getItemsInList(listId: String): List<ShoppingListItem> {
        return try {
            collection
                .whereEqualTo("listId", listId)
                .whereEqualTo("isDeleted", false)
                .get()
                .await()
                .toObjects(ShoppingListItem::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get items in a specific shopping list as a Flow.
     */
    fun getItemsInListAsFlow(listId: String): Flow<List<ShoppingListItem>> = callbackFlow {
        val listenerRegistration = collection
            .whereEqualTo("listId", listId)
            .whereEqualTo("isDeleted", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val items = snapshot.toObjects(ShoppingListItem::class.java)
                    trySend(items)
                }
            }
        
        awaitClose { listenerRegistration.remove() }
    }
    
    /**
     * Update a shopping list item.
     */
    suspend fun updateShoppingListItem(
        id: String,
        quantity: String?,
        notes: String?
    ): Boolean {
        return try {
            val updates = mutableMapOf<String, Any>()
            quantity?.let { updates["quantity"] = it }
            notes?.let { updates["notes"] = it }
            updates["lastModifiedAt"] = Timestamp.now()
            
            update(id, updates)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Toggle the checked state of a shopping list item.
     */
    /**
     * Toggle the checked state of a shopping list item.
     */
    suspend fun toggleItemChecked(id: String, isChecked: Boolean, userId: String): Boolean {
        return try {
            val updates: Map<String, Any?> = if (isChecked) { // Explicitly type the map
                mapOf(
                    "isChecked" to true,
                    "checkedBy" to userId,
                    "checkedAt" to Timestamp.now(),
                    "lastModifiedAt" to Timestamp.now()
                )
            } else {
                mapOf(
                    "isChecked" to false,
                    "checkedBy" to null,
                    "checkedAt" to null,
                    "lastModifiedAt" to Timestamp.now()
                )
            }

            // If your `update` function strictly expects Map<String, Any> and cannot handle nulls,
            // you might need to filter out nulls or ensure your `update` function can handle them.
            // However, Firestore typically handles null values for deletions or resets.
            update(id, updates)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Soft delete a shopping list item.
     */
    suspend fun softDeleteShoppingListItem(id: String): Boolean {
        return try {
            val updates = mapOf(
                "isDeleted" to true,
                "lastModifiedAt" to Timestamp.now()
            )
            update(id, updates)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get checked items in a shopping list.
     */
    suspend fun getCheckedItemsInList(listId: String): List<ShoppingListItem> {
        return try {
            collection
                .whereEqualTo("listId", listId)
                .whereEqualTo("isChecked", true)
                .whereEqualTo("isDeleted", false)
                .get()
                .await()
                .toObjects(ShoppingListItem::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get unchecked items in a shopping list.
     */
    suspend fun getUncheckedItemsInList(listId: String): List<ShoppingListItem> {
        return try {
            collection
                .whereEqualTo("listId", listId)
                .whereEqualTo("isChecked", false)
                .whereEqualTo("isDeleted", false)
                .get()
                .await()
                .toObjects(ShoppingListItem::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Clear all checked items in a shopping list.
     */
    suspend fun clearCheckedItems(listId: String): Boolean {
        return try {
            // Get all checked items in the list
            val checkedItems = collection
                .whereEqualTo("listId", listId)
                .whereEqualTo("isChecked", true)
                .whereEqualTo("isDeleted", false)
                .get()
                .await()
                .documents
            
            // Batch update to mark them as deleted
            val batch = firestore.batch()
            checkedItems.forEach { document ->
                batch.update(
                    document.reference,
                    mapOf(
                        "isDeleted" to true,
                        "lastModifiedAt" to Timestamp.now()
                    )
                )
            }
            
            batch.commit().await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Uncheck all items in a shopping list.
     */
    suspend fun uncheckAllItems(listId: String): Boolean {
        return try {
            // Get all checked items in the list
            val checkedItems = collection
                .whereEqualTo("listId", listId)
                .whereEqualTo("isChecked", true)
                .whereEqualTo("isDeleted", false)
                .get()
                .await()
                .documents
            
            // Batch update to uncheck them
            val batch = firestore.batch()
            checkedItems.forEach { document ->
                batch.update(
                    document.reference,
                    mapOf(
                        "isChecked" to false,
                        "checkedBy" to null,
                        "checkedAt" to null,
                        "lastModifiedAt" to Timestamp.now()
                    )
                )
            }
            
            batch.commit().await()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getModelClass(): Class<ShoppingListItem> = ShoppingListItem::class.java
}