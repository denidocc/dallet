package com.dallet.app.ledger

class Ledger(
    private val accountRepository: AccountRepository
) {
    suspend fun hasCompletedOnboarding(): Boolean = accountRepository.getAll().isNotEmpty()

    suspend fun completeOnboarding(
        cashOpeningMinor: Long,
        cardOpeningMinor: Long
    ) {
        require(cashOpeningMinor >= 0 && cardOpeningMinor >= 0) {
            "opening balances must be non-negative"
        }
        accountRepository.replaceAll(
            listOf(
                Account(id = 1L, name = CASH_NAME, openingBalanceMinor = cashOpeningMinor),
                Account(id = 2L, name = CARD_NAME, openingBalanceMinor = cardOpeningMinor)
            )
        )
    }

    suspend fun accountBalances(): List<AccountBalance> =
        accountRepository.getAll().map { account ->
            AccountBalance(
                account = account,
                balanceMinor = balanceFor(account)
            )
        }

    suspend fun totalBalanceMinor(): Long =
        accountBalances().sumOf { it.balanceMinor }

    private fun balanceFor(account: Account): Long {
        // Ticket #3: no operations yet — balance equals стартовый остаток.
        return account.openingBalanceMinor
    }

    companion object {
        const val CASH_NAME = "Наличные"
        const val CARD_NAME = "ЗП карта"
    }
}
