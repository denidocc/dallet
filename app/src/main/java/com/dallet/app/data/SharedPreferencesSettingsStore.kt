package com.dallet.app.data

import android.content.Context
import com.dallet.app.ledger.Currency
import com.dallet.app.ledger.SettingsStore
import com.dallet.app.ledger.ThemeMode

class SharedPreferencesSettingsStore(context: Context) : SettingsStore {
    private val prefs = context.getSharedPreferences("dallet_settings", Context.MODE_PRIVATE)

    override fun mainCurrency(): Currency =
        Currency.fromName(prefs.getString(KEY_MAIN_CURRENCY, null))

    override fun setMainCurrency(currency: Currency) {
        prefs.edit().putString(KEY_MAIN_CURRENCY, currency.name).apply()
    }

    override fun themeMode(): ThemeMode =
        ThemeMode.fromName(prefs.getString(KEY_THEME_MODE, null))

    override fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_MAIN_CURRENCY = "main_currency"
    }
}
