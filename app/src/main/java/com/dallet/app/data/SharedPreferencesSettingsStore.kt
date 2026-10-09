package com.dallet.app.data

import android.content.Context
import com.dallet.app.ledger.Currency
import com.dallet.app.ledger.ExchangeRate
import com.dallet.app.ledger.SettingsStore
import com.dallet.app.ledger.ThemeMode
import java.time.LocalDate

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

    override fun rates(): List<ExchangeRate> =
        prefs.getString(KEY_RATES, null).orEmpty().split(";").mapNotNull { part ->
            val fields = part.split(":")
            if (fields.size != 3) return@mapNotNull null
            val currency = Currency.values().firstOrNull { it.name == fields[0] }
                ?: return@mapNotNull null
            val scaled = fields[1].toLongOrNull() ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(fields[2]) }.getOrNull()
                ?: return@mapNotNull null
            ExchangeRate(currency, scaled, date)
        }

    override fun saveRates(rates: List<ExchangeRate>) {
        val text = rates.joinToString(";") { "${it.currency.name}:${it.scaled}:${it.updatedOn}" }
        prefs.edit().putString(KEY_RATES, text).apply()
    }

    private companion object {
        const val KEY_RATES = "rates"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_MAIN_CURRENCY = "main_currency"
    }
}
