package ru.lionzxy.tplauncher.utils

import java.util.*

object LocalizationHelper {
    val rb by lazy {
        try {
            ResourceBundle.getBundle("strings", Locale.getDefault())
        } catch (e: Exception) {
            println(e)
            ResourceBundle.getBundle("strings", Locale("en_US"))
        }
    }

    fun getString(key: String, default: String): String {
        return try {
            rb.getString(key)
        } catch (e: Exception) {
            default
        }
    }

    fun getString(key: String): String? {
        return try {
            rb.getString(key)
        } catch (e: Exception) {
            null
        }
    }
}