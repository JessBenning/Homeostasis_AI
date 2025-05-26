package com.homeostasis.app.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.homeostasis.app.data.model.ShoppingItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await

/**
 * Repository for shopping item-related operations.
 */
class ShoppingItemRepository : FirebaseRepository<ShoppingItem>() {
    
    override val collectionName: String = ShoppingItem.COLLECTION
    
    /**
     * Create a new shopping item.
     */
    suspend fun createShoppingItem(shoppingItem: ShoppingItem): String? {
        return try {
            val shoppingItemWithTimestamp = shoppingItem.copy(
                createdAt = Timestamp.now(),
                lastModifiedAt = Timestamp.now(),
                lastUsed = Timestamp.now()
            )
            add(shoppingItemWithTimestamp)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get shopping items created by a specific user.
     */
    suspend fun getShoppingItemsByUser(userId: String): List<ShoppingItem> {
        return try {
            collection
                .whereEqualTo("createdBy", userId)
                .get()
                .await()
                .toObjects(ShoppingItem::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get shopping items by name (for search/autocomplete).
     */
    suspend fun searchShoppingItems(query: String, limit: Int = 10): List<ShoppingItem> {
        return try {
            collection
                .orderBy("name")
                .startAt(query)
                .endAt(query + "\uf8ff")
                .limit(limit.toLong())
                .get()
                .await()
                .toObjects(ShoppingItem::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get most frequently used shopping items.
     */
    suspend fun getMostFrequentItems(limit: Int = 10): List<ShoppingItem> {
        return try {
            collection
                .orderBy("usageCount", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
                .toObjects(ShoppingItem::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get most recently used shopping items.
     */
    suspend fun getMostRecentItems(limit: Int = 10): List<ShoppingItem> {
        return try {
            collection
                .orderBy("lastUsed", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
                .toObjects(ShoppingItem::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Update a shopping item.
     */
//    suspend fun updateShoppingItem(itemId: String, name: String, category: String?): Boolean {
//        return try {
//            val updates:Map<String, Any?> = mapOf(
//                "name" to name,
//                "category" to category,
//                "lastModifiedAt" to Timestamp.now()
//            )
//            update(itemId, updates)
//        } catch (e: Exception) {
//            false
//        }
//    }
    suspend fun updateShoppingItem(itemId: String, name: String, category: String?): Boolean {
        return try {
            val updates = mutableMapOf<String, Any>(
                "name" to name,
                "lastModifiedAt" to Timestamp.now()
            )
            category?.let { updates["category"] = it } // Add category only if it's not null

            update(itemId, updates) // 'updates' is now Map<String, Any>
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Increment the usage count of a shopping item.
     */
    suspend fun incrementUsageCount(itemId: String): Boolean {
        return try {
            // Get the current item to get its current usage count
            val item = getById(itemId) ?: return false
            
            val updates = mapOf(
                "usageCount" to (item.usageCount + 1),
                "lastUsed" to Timestamp.now(),
                "lastModifiedAt" to Timestamp.now()
            )
            update(itemId, updates)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get shopping items by category.
     */
    suspend fun getShoppingItemsByCategory(category: String): List<ShoppingItem> {
        return try {
            collection
                .whereEqualTo("category", category)
                .get()
                .await()
                .toObjects(ShoppingItem::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get shopping items by multiple IDs.
     */
    suspend fun getShoppingItemsByIds(ids: List<String>): List<ShoppingItem> {
        if (ids.isEmpty()) return emptyList()
        
        return try {
            // Firestore can only query up to 10 items at a time with whereIn
            val result = mutableListOf<ShoppingItem>()
            
            // Split the IDs into chunks of 10
            ids.chunked(10).forEach { chunk ->
                val items = collection
                    .whereIn("id", chunk)
                    .get()
                    .await()
                    .toObjects(ShoppingItem::class.java)
                
                result.addAll(items)
            }
            
            result
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override fun getModelClass(): Class<ShoppingItem> = ShoppingItem::class.java
}