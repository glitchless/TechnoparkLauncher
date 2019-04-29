package ru.lionzxy.tplauncher.downloader.javas

import com.google.gson.annotations.SerializedName

class JavaBinaryModel(
    @SerializedName("type")
    val type: String,
    @SerializedName("arch")
    val arch: String,
    @SerializedName("downloadUrl")
    val downloadUrl: String,
    @SerializedName("javaRelativePath")
    var javaRelativePath: String,
    @SerializedName("extension")
    var extension: String
)