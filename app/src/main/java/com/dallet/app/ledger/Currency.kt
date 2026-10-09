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

interface SettingsStore {
    fun mainCurrency(): Currency
    fun setMainCurrency(currency: Currency)
}

class InMemorySettingsStore : SettingsStore {
    private var currency: Currency = Currency.DEFAULT

    override fun mainCurrency(): Currency = currency

    override fun setMainCurrency(currency: Currency) {
        this.currency = currency
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
