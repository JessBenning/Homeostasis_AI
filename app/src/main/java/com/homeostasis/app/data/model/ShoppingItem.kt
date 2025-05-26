package com.homeostasis.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Data class representing a shopping item in the Homeostasis app.
 * This is the base item definition that can be used across multiple shopping lists.
 */
data class ShoppingItem(
    @DocumentId
    val id: String = "",
    
    val name: String = "",
    
    val category: String? = null,
    
    @PropertyName("lastUsed")
    val lastUsed: Timestamp = Timestamp.now(),
    
    @PropertyName("usageCount")
    val usageCount: Int = 0,
    
    @PropertyName("createdBy")
    val createdBy: String = "",
    
    @PropertyName("createdAt")
    val createdAt: Timestamp = Timestamp.now(),
    
    @PropertyName("lastModifiedAt")
    val lastModifiedAt: Timestamp = Timestamp.now()
) {
    // Empty constructor for Firestore
    constructor() : this(
        id = "",
        name = "",
        category = null,
        lastUsed = Timestamp.now(),
        usageCount = 0,
        createdBy = "",
        createdAt = Timestamp.now(),
        lastModifiedAt = Timestamp.now()
    )
    
    companion object {
        const val COLLECTION = "shoppingItems"
        
        // Common shopping items for quick-add
        val COMMON_ITEMS = listOf(
            "Milk",
            "Eggs",
            "Bread",
            "Apples",
            "Bananas",
            "Chicken",
            "Rice",
            "Pasta",
            "Tomatoes",
            "Onions"
        )
    }
}