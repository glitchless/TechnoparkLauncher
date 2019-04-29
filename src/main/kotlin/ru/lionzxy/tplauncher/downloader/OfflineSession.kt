package ru.lionzxy.tplauncher.downloader

import sk.tomsik68.mclauncher.api.login.ESessionType
import sk.tomsik68.mclauncher.api.login.ISession
import java.util.*

class OfflineSession(val usernameOffline: String) : ISession {
    override fun getUsername() = usernameOffline

    override fun getType() = ESessionType.MOJANG

    override fun getUUID() = UUID.randomUUID().toString()

    override fun getSessionID() = "sessionid"

    override fun getProperties() = mutableListOf<ISession.Prop>()
}