package ru.lionzxy.tplauncher.utils

import java.util.*

object LocalizationHelper {
    val supportedLocales = setOf(Locale("en", "US"))
    val rb by lazy {
        setDefaultLocale()
        ResourceBundle.getBundle("strings", Locale.getDefault())
    }

    fun getString(key: String, vararg args: Any): String {
        return rb.getString(key).format(*args)
    }

    private fun setDefaultLocale() {
        if (!supportedLocales.contains(Locale.getDefault())) {
            Locale.setDefault(supportedLocales.first())
        }
    }

}