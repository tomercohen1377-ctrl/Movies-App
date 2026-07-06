package com.tcohen.moviesapp.data.auth

data class AuthSnapshot(
    val userId: String,
    val accessToken: String,
    val expiresAtEpochMs: Long,
)
