package ru.lionzxy.tplauncher.utils

import java.util.*

object LocalizationHelper {
    val rb = ResourceBundle.getBundle("strings", Locale.getDefault())

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