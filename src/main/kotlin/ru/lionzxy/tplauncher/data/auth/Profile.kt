package ru.lionzxy.tplauncher.data.auth

import sk.tomsik68.mclauncher.api.login.ESessionType
import sk.tomsik68.mclauncher.api.login.ISession

data class Profile(
    val login: String,
    val accessToken: String,
    val profileId: String,
    val email: String? = null
) : ISession {
    override fun getUsername() = login
    override fun getType() = ESessionType.MOJANG
    override fun getUUID() = profileId
    override fun getSessionID() = accessToken
    override fun getProperties() = emptyList<ISession.Prop>()

}
