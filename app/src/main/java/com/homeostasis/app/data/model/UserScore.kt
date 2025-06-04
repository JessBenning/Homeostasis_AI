package com.homeostasis.app.data.model

data class UserScore(
    val userId: String,
    val userName: String,
    val userProfilePictureUrl: String,
    val score: Int
)