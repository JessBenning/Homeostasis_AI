package com.homeostasis.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Data class representing a shopping list in the Homeostasis app.
 */
data class ShoppingList(
    @DocumentId
    val id: String = "",
    
    val name: String = "",
    
    @PropertyName("createdBy")
    val createdBy: String = "",
    
    @PropertyName("createdAt")
    val createdAt: Timestamp = Timestamp.now(),
    
    @PropertyName("lastModifiedAt")
    val lastModifiedAt: Timestamp = Timestamp.now(),
    
    @PropertyName("isDeleted")
    val isDeleted: Boolean = false
) {
    // Empty constructor for Firestore
    constructor() : this(
        id = "",
        name = "",
        createdBy = "",
        createdAt = Timestamp.now(),
        lastModifiedAt = Timestamp.now(),
        isDeleted = false
    )
    
    companion object {
        const val COLLECTION = "shoppingLists"
        
        // Predefined shopping lists
        val DEFAULT_SHOPPING_LISTS = listOf(
            ShoppingList(name = "Groceries"),
            ShoppingList(name = "Hardware Store"),
            ShoppingList(name = "Pharmacy")
        )
    }
}