package com.homeostasis.app.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.homeostasis.app.data.model.ResetHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await

/**
 * Repository for reset history-related operations.
 */
class ResetHistoryRepository : FirebaseRepository<ResetHistory>() {
    
    override val collectionName: String = ResetHistory.COLLECTION
    
    /**
     * Record a reset event.
     */
    suspend fun recordResetEvent(
        resetBy: String,
        resetBehavior: String,
        thresholdValue: Int,
        preservedScoreDifference: Boolean,
        userScores: List<ResetHistory.UserScore>
    ): String? {
        return try {
            val resetHistory = ResetHistory(
                resetTime = Timestamp.now(),
                resetBy = resetBy,
                resetBehavior = resetBehavior,
                thresholdValue = thresholdValue,
                preservedScoreDifference = preservedScoreDifference,
                userScores = userScores
            )
            add(resetHistory)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get all reset history events.
     */
    suspend fun getAllResetHistory(): List<ResetHistory> {
        return try {
            collection
                .orderBy("resetTime", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(ResetHistory::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get all reset history events as a Flow.
     */
    fun getAllResetHistoryAsFlow(): Flow<List<ResetHistory>> = callbackFlow {
        val listenerRegistration = collection
            .orderBy("resetTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val resetHistory = snapshot.toObjects(ResetHistory::class.java)
                    trySend(resetHistory)
                }
            }
        
        awaitClose { listenerRegistration.remove() }
    }
    
    /**
     * Get reset history events triggered by a specific user.
     */
    suspend fun getResetHistoryByUser(userId: String): List<ResetHistory> {
        return try {
            collection
                .whereEqualTo("resetBy", userId)
                .orderBy("resetTime", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(ResetHistory::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get the most recent reset history event.
     */
    suspend fun getMostRecentResetHistory(): ResetHistory? {
        return try {
            collection
                .orderBy("resetTime", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
                .toObjects(ResetHistory::class.java)
                .firstOrNull()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get reset history events for a specific time period.
     */
    suspend fun getResetHistoryByTimePeriod(startDate: Timestamp, endDate: Timestamp): List<ResetHistory> {
        return try {
            collection
                .whereGreaterThanOrEqualTo("resetTime", startDate)
                .whereLessThanOrEqualTo("resetTime", endDate)
                .orderBy("resetTime", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(ResetHistory::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override fun getModelClass(): Class<ResetHistory> = ResetHistory::class.java
}