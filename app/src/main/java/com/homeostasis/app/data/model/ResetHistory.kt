package com.homeostasis.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * Data class representing a reset history entry in the Homeostasis app.
 * This tracks when score resets occur and stores user scores before and after the reset.
 */
data class ResetHistory(
    @DocumentId
    val id: String = "",
    
    @PropertyName("resetTime")
    val resetTime: Timestamp = Timestamp.now(),
    
    @PropertyName("resetBy")
    val resetBy: String = "", // User ID who triggered the reset
    
    @PropertyName("resetBehavior")
    val resetBehavior: String = "relative", // "zero", "relative", "percentage"
    
    @PropertyName("thresholdValue")
    val thresholdValue: Int = 100,
    
    @PropertyName("preservedScoreDifference")
    val preservedScoreDifference: Boolean = true,
    
    @PropertyName("userScores")
    val userScores: List<UserScore> = emptyList()
) {
    // Empty constructor for Firestore
    constructor() : this(
        id = "",
        resetTime = Timestamp.now(),
        resetBy = "",
        resetBehavior = "relative",
        thresholdValue = 100,
        preservedScoreDifference = true,
        userScores = emptyList()
    )
    
    /**
     * User score before and after reset.
     */
    data class UserScore(
        @PropertyName("userId")
        val userId: String = "",
        
        @PropertyName("userName")
        val userName: String = "",
        
        @PropertyName("scoreBefore")
        val scoreBefore: Int = 0,
        
        @PropertyName("scoreAfter")
        val scoreAfter: Int = 0
    ) {
        // Empty constructor for Firestore
        constructor() : this(
            userId = "",
            userName = "",
            scoreBefore = 0,
            scoreAfter = 0
        )
    }
    
    companion object {
        const val COLLECTION = "resetHistory"
    }
}