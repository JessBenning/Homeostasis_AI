package com.homeostasis.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Data class representing a task category in the Homeostasis app.
 */
data class Category(
    @DocumentId
    val id: String = "",
    
    val name: String = "",
    
    val color: String = "#4285F4", // Default to blue
    
    val icon: String = "🏠", // Default to house icon
    
    @PropertyName("createdBy")
    val createdBy: String = "",
    
    @PropertyName("createdAt")
    val createdAt: Timestamp = Timestamp.now()
) {
    // Empty constructor for Firestore
    constructor() : this(
        id = "",
        name = "",
        color = "#4285F4",
        icon = "🏠",
        createdBy = "",
        createdAt = Timestamp.now()
    )
    
    companion object {
        const val COLLECTION = "categories"
        
        // Predefined categories
        val DEFAULT_CATEGORIES = listOf(
            Category(name = "House", color = "#4285F4", icon = "🏠"),
            Category(name = "School", color = "#34A853", icon = "📚"),
            Category(name = "Personal", color = "#A142F4", icon = "👤")
        )
    }
}