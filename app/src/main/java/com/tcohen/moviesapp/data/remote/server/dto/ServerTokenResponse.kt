package com.tcohen.moviesapp.data.remote.server.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerTokenResponse(
    @SerialName("accessToken") val accessToken: String,
    @SerialName("tokenType") val tokenType: String = "Bearer",
)
