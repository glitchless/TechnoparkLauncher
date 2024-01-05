package ru.lionzxy.tplauncher.config

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val login: String,
    val accessToken: String,
    val profileId: String,
    val email: String? = null
)