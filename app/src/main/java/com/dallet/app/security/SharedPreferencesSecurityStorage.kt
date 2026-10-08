package com.dallet.app.security

import android.content.Context

class SharedPreferencesSecurityStorage(context: Context) : SecurityStorage {
    private val prefs = context.applicationContext
        .getSharedPreferences("dallet_security", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String?) {
        prefs.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }

    override fun getBoolean(key: String): Boolean = prefs.getBoolean(key, false)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
}
