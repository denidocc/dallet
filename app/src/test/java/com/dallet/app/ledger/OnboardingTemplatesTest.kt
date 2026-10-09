package com.dallet.app.ledger

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class OnboardingTemplatesTest {

    private class Env {
        val settings = InMemorySettingsStore()
        val ledger = Ledger(
            accountRepository = InMemoryAccountRepository(),
            categoryRepository = InMemoryCategoryRepository(),
            operationRepository = InMemoryOperationRepository(),
            settings = settings
        )
    }

    @Test
    fun defaultTemplates_haveCashAndCardEnabledOnly() {
        val templates = AccountTemplates.defaults()

        assertThat(templates.filter { it.enabledByDefault }.map { it.name })
            .containsExactly("Наличные", "ЗП карта").inOrder()
        assertThat(templates.map { it.name })
            .containsAtLeast("Накопления", "Кредитная карта", "Долги")
    }

    @Test
    fun onboarding_createsChosenAccountsWithSequentialIds() = runTest {
        val env = Env()

        env.ledger.completeOnboarding(
            accounts = listOf(
                NewAccount("Наличные", 10_000L),
                NewAccount("Накопления", 50_000L),
                NewAccount("Кошелёк", 0L)
            ),
            currency = Currency.USD
        )

        val balances = env.ledger.accountBalances()
        assertThat(balances.map { it.account.name })
            .containsExactly("Наличные", "Накопления", "Кошелёк").inOrder()
        assertThat(balances.map { it.account.id }).containsExactly(1L, 2L, 3L).inOrder()
        assertThat(env.ledger.totalBalanceMinor()).isEqualTo(60_000L)
        assertThat(env.ledger.mainCurrency()).isEqualTo(Currency.USD)
    }

    @Test
    fun onboarding_seedsDefaultCategories() = runTest {
        val env = Env()

        env.ledger.completeOnboarding(listOf(NewAccount("Наличные", 0L)), Currency.TMT)

        assertThat(env.ledger.categories(CategoryKind.EXPENSE).map { it.name }).contains("Еда")
    }

    @Test
    fun onboarding_trimsNamesAndRejectsBadInput() = runTest {
        val env = Env()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { env.ledger.completeOnboarding(emptyList(), Currency.TMT) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                env.ledger.completeOnboarding(listOf(NewAccount("   ", 0L)), Currency.TMT)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                env.ledger.completeOnboarding(listOf(NewAccount("Счёт", -1L)), Currency.TMT)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                env.ledger.completeOnboarding(
                    listOf(NewAccount("Счёт", 0L), NewAccount(" счёт ", 0L)),
                    Currency.TMT
                )
            }
        }
        assertThat(env.ledger.hasCompletedOnboarding()).isFalse()

        env.ledger.completeOnboarding(listOf(NewAccount("  Наличные  ", 5L)), Currency.TMT)
        assertThat(env.ledger.accountBalances().single().account.name).isEqualTo("Наличные")
    }

    @Test
    fun legacyOnboarding_stillCreatesCashAndCardInTmt() = runTest {
        val env = Env()

        env.ledger.completeOnboarding(cashOpeningMinor = 100L, cardOpeningMinor = 200L)

        assertThat(env.ledger.accountBalances().map { it.account.name })
            .containsExactly(Ledger.CASH_NAME, Ledger.CARD_NAME).inOrder()
        assertThat(env.ledger.mainCurrency()).isEqualTo(Currency.TMT)
    }

    @Test
    fun mainCurrency_defaultsToTmt() {
        assertThat(runBlocking { Env().ledger.mainCurrency() }).isEqualTo(Currency.TMT)
    }

    @Test
    fun backup_carriesMainCurrency() = runTest {
        val source = Env()
        source.ledger.completeOnboarding(listOf(NewAccount("Наличные", 100L)), Currency.EUR)

        val target = Env()
        target.ledger.importBackup(source.ledger.exportBackup())

        assertThat(target.ledger.mainCurrency()).isEqualTo(Currency.EUR)
    }

    @Test
    fun backupWithoutCurrency_importsAsTmt() = runTest {
        val json = """
            {"app":"dallet","schemaVersion":1,
             "accounts":[{"id":1,"name":"Наличные","openingBalanceMinor":0}],
             "categories":[],"operations":[]}
        """.trimIndent()
        val env = Env()
        env.settings.setMainCurrency(Currency.RUB)

        env.ledger.importBackup(json)

        assertThat(env.ledger.mainCurrency()).isEqualTo(Currency.TMT)
    }

    @Test
    fun reset_returnsCurrencyToDefault() = runTest {
        val env = Env()
        env.ledger.completeOnboarding(listOf(NewAccount("Наличные", 0L)), Currency.KZT)

        env.ledger.resetAll()

        assertThat(env.ledger.mainCurrency()).isEqualTo(Currency.TMT)
    }

    @Test
    fun currencyList_isTheAgreedFixedSet() {
        assertThat(Currency.values().map { it.name })
            .containsExactly("TMT", "USD", "EUR", "RUB", "TRY", "CNY", "KZT").inOrder()
        assertThat(Currency.TMT.code).isEqualTo("ТМТ")
        assertThat(Currency.fromName("nope")).isEqualTo(Currency.TMT)
        assertThat(Currency.fromName(null)).isEqualTo(Currency.TMT)
        assertThat(Currency.fromName("USD")).isEqualTo(Currency.USD)
    }
}
