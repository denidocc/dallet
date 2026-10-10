package com.dallet.app.data

import android.content.Context
import com.dallet.app.ledger.AppLanguage
import com.dallet.app.ledger.Currency
import com.dallet.app.ledger.ExchangeRate
import com.dallet.app.ledger.RecurringCodec
import com.dallet.app.ledger.RecurringRule
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

    override fun budgets(): Map<Long, Long> =
        prefs.getString(KEY_BUDGETS, null).orEmpty().split(";").mapNotNull { part ->
            val fields = part.split(":")
            if (fields.size != 2) return@mapNotNull null
            val id = fields[0].toLongOrNull() ?: return@mapNotNull null
            val limit = fields[1].toLongOrNull() ?: return@mapNotNull null
            if (limit > 0) id to limit else null
        }.toMap()

    override fun saveBudgets(budgets: Map<Long, Long>) {
        val text = budgets.entries.joinToString(";") { "${it.key}:${it.value}" }
        prefs.edit().putString(KEY_BUDGETS, text).apply()
    }

    override fun recurring(): List<RecurringRule> =
        RecurringCodec.fromText(prefs.getString(KEY_RECURRING, null))

    override fun saveRecurring(rules: List<RecurringRule>) {
        prefs.edit().putString(KEY_RECURRING, RecurringCodec.toText(rules)).apply()
    }

    override fun language(): AppLanguage =
        prefs.getString(KEY_LANGUAGE, null)?.let { AppLanguage.fromName(it) } ?: AppLanguage.system()

    override fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    private companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_BUDGETS = "budgets"
        const val KEY_RECURRING = "recurring"
        const val KEY_RATES = "rates"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_MAIN_CURRENCY = "main_currency"
    }
}
