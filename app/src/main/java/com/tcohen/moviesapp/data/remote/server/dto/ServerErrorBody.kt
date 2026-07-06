package com.tcohen.moviesapp.data.remote.server.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerErrorBody(
    @SerialName("error") val error: String = "",
)
