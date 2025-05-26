package com.homeostasis.app.data.remote

import com.google.firebase.Timestamp
import com.homeostasis.app.data.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await

/**
 * Repository for category-related operations.
 */
class CategoryRepository : FirebaseRepository<Category>() {
    
    override val collectionName: String = Category.COLLECTION
    
    /**
     * Create a new category.
     */
    suspend fun createCategory(category: Category): String? {
        return try {
            val categoryWithTimestamp = category.copy(
                createdAt = Timestamp.now()
            )
            add(categoryWithTimestamp)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get categories created by a specific user.
     */
    suspend fun getCategoriesByUser(userId: String): List<Category> {
        return try {
            collection
                .whereEqualTo("createdBy", userId)
                .get()
                .await()
                .toObjects(Category::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get categories created by a specific user as a Flow.
     */
    fun getCategoriesByUserAsFlow(userId: String): Flow<List<Category>> = callbackFlow {
        val listenerRegistration = collection
            .whereEqualTo("createdBy", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val categories = snapshot.toObjects(Category::class.java)
                    trySend(categories)
                }
            }
        
        awaitClose { listenerRegistration.remove() }
    }
    
    /**
     * Create default categories for a new user.
     */
    suspend fun createDefaultCategories(userId: String): List<String> {
        return try {
            val defaultCategories = Category.DEFAULT_CATEGORIES.map { category ->
                category.copy(
                    createdBy = userId,
                    createdAt = Timestamp.now()
                )
            }
            
            val categoryIds = mutableListOf<String>()
            
            // Add each category and collect the IDs
            defaultCategories.forEach { category ->
                val id = add(category)
                if (id != null) {
                    categoryIds.add(id)
                }
            }
            
            categoryIds
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Update a category.
     */
    suspend fun updateCategory(categoryId: String, name: String, color: String, icon: String): Boolean {
        return try {
            val updates = mapOf(
                "name" to name,
                "color" to color,
                "icon" to icon
            )
            update(categoryId, updates)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get all categories.
     */
    suspend fun getAllCategories(): List<Category> {
        return try {
            collection
                .get()
                .await()
                .toObjects(Category::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get all categories as a Flow.
     */
    fun getAllCategoriesAsFlow(): Flow<List<Category>> = callbackFlow {
        val listenerRegistration = collection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val categories = snapshot.toObjects(Category::class.java)
                    trySend(categories)
                }
            }
        
        awaitClose { listenerRegistration.remove() }
    }
    
    override fun getModelClass(): Class<Category> = Category::class.java
}