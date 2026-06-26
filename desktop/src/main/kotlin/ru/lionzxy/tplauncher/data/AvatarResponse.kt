package ru.lionzxy.tplauncher.data

import com.google.gson.annotations.SerializedName

data class AvatarResponse(val data: AvatarData)

data class AvatarData(@SerializedName("avatar_url") val avatarUrl: String)
