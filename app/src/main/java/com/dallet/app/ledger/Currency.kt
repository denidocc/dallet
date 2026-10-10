package com.dallet.app.ledger

enum class Currency(val code: String) {
    TMT("ТМТ"),
    USD("USD"),
    EUR("EUR"),
    RUB("RUB"),
    TRY("TRY"),
    CNY("CNY"),
    KZT("KZT");

    companion object {
        val DEFAULT = TMT

        fun fromName(name: String?): Currency =
            values().firstOrNull { it.name == name } ?: DEFAULT
    }
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromName(name: String?): ThemeMode =
            values().firstOrNull { it.name == name } ?: SYSTEM
    }
}

enum class AppLanguage(val tag: String) {
    RU("ru"),
    EN("en");

    companion object {
        val DEFAULT = RU

        fun fromName(name: String?): AppLanguage =
            values().firstOrNull { it.name == name } ?: DEFAULT
    }
}

interface SettingsStore {
    fun mainCurrency(): Currency
    fun setMainCurrency(currency: Currency)
    fun themeMode(): ThemeMode
    fun setThemeMode(mode: ThemeMode)
    fun language(): AppLanguage
    fun setLanguage(language: AppLanguage)
    fun rates(): List<ExchangeRate>
    fun saveRates(rates: List<ExchangeRate>)
    fun budgets(): Map<Long, Long>
    fun saveBudgets(budgets: Map<Long, Long>)
}

class InMemorySettingsStore : SettingsStore {
    private var currency: Currency = Currency.DEFAULT
    private var theme: ThemeMode = ThemeMode.SYSTEM

    override fun mainCurrency(): Currency = currency

    override fun setMainCurrency(currency: Currency) {
        this.currency = currency
    }

    override fun themeMode(): ThemeMode = theme

    override fun setThemeMode(mode: ThemeMode) {
        theme = mode
    }

    private var lang: AppLanguage = AppLanguage.DEFAULT

    override fun language(): AppLanguage = lang

    override fun setLanguage(language: AppLanguage) {
        lang = language
    }

    private var storedBudgets: Map<Long, Long> = emptyMap()

    override fun budgets(): Map<Long, Long> = storedBudgets

    override fun saveBudgets(budgets: Map<Long, Long>) {
        storedBudgets = budgets
    }

    private var storedRates: List<ExchangeRate> = emptyList()

    override fun rates(): List<ExchangeRate> = storedRates

    override fun saveRates(rates: List<ExchangeRate>) {
        storedRates = rates
    }
}

data class NewAccount(val name: String, val openingBalanceMinor: Long)

data class AccountTemplate(val name: String, val enabledByDefault: Boolean)

object AccountTemplates {
    fun defaults(): List<AccountTemplate> = listOf(
        AccountTemplate(Ledger.CASH_NAME, true),
        AccountTemplate(Ledger.CARD_NAME, true),
        AccountTemplate("Накопления", false),
        AccountTemplate("Кредитная карта", false),
        AccountTemplate("Долги", false)
    )
}
