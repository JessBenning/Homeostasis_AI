package com.homeostasis.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Data class representing a shopping list item in the Homeostasis app.
 * This maps items to specific shopping lists with additional properties.
 */
data class ShoppingListItem(
    @DocumentId
    val id: String = "",
    
    @PropertyName("itemId")
    val itemId: String = "",
    
    @PropertyName("listId")
    val listId: String = "",
    
    val quantity: String? = null,
    
    val notes: String? = null,
    
    @PropertyName("addedBy")
    val addedBy: String = "",
    
    @PropertyName("addedAt")
    val addedAt: Timestamp = Timestamp.now(),
    
    @PropertyName("isChecked")
    val isChecked: Boolean = false,
    
    @PropertyName("checkedBy")
    val checkedBy: String? = null,
    
    @PropertyName("checkedAt")
    val checkedAt: Timestamp? = null,
    
    @PropertyName("lastModifiedAt")
    val lastModifiedAt: Timestamp = Timestamp.now(),
    
    @PropertyName("isDeleted")
    val isDeleted: Boolean = false
) {
    // Empty constructor for Firestore
    constructor() : this(
        id = "",
        itemId = "",
        listId = "",
        quantity = null,
        notes = null,
        addedBy = "",
        addedAt = Timestamp.now(),
        isChecked = false,
        checkedBy = null,
        checkedAt = null,
        lastModifiedAt = Timestamp.now(),
        isDeleted = false
    )
    
    companion object {
        const val COLLECTION = "shoppingListItems"
    }
}