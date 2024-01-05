package ru.lionzxy.tplauncher.config

import kotlinx.serialization.Serializable

@Serializable
data class DownloadedInfo(
    var initFileDownload: Boolean? = false,
    var lastUpdateFromChangeLog: Long? = 0
)