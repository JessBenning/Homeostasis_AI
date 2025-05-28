package com.homeostasis.app.data

import androidx.room.Dao
import androidx.room.Query
import com.homeostasis.app.data.model.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM User WHERE id = :userId")
    fun getUserById(userId: String): Flow<User>
}