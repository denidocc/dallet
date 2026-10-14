package com.dallet.app.ledger

import java.time.LocalDate

enum class DebtDirection {
    /** Someone owes the user. */
    LENT,

    /** The user owes someone. */
    BORROWED
}

data class Debt(
    val id: Long,
    val person: String,
    val direction: DebtDirection,
    val note: String,
    val createdAt: LocalDate
)

enum class DebtEntryKind {
    /** The money that started the debt; every debt has exactly one. */
    ISSUE,
    REPAYMENT,

    /** The rest of the debt was forgiven or will not be paid; an operation records it. */
    WRITE_OFF
}

/**
 * One money movement of a debt. [accountId] is the account it went through, or null when the
 * balances were left alone (an old debt entered afterwards).
 */
data class DebtEntry(
    val id: Long,
    val debtId: Long,
    val kind: DebtEntryKind,
    val amountMinor: Long,
    val date: LocalDate,
    val accountId: Long?,
    /** The expense or income created with a write-off. */
    val operationId: Long? = null
)

data class DebtData(
    val debts: List<Debt>,
    val entries: List<DebtEntry>
) {
    companion object {
        val EMPTY = DebtData(emptyList(), emptyList())
    }
}

/** The effect of an entry on its account: positive adds money, negative takes it away. */
fun accountDelta(direction: DebtDirection, entry: DebtEntry): Long {
    val received = when (direction) {
        DebtDirection.LENT -> entry.kind != DebtEntryKind.ISSUE
        DebtDirection.BORROWED -> entry.kind == DebtEntryKind.ISSUE
    }
    return if (received) entry.amountMinor else -entry.amountMinor
}

data class DebtSummary(
    val debt: Debt,
    val issuedMinor: Long,
    val repaidMinor: Long,
    val writtenOffMinor: Long = 0L
) {
    val remainingMinor: Long get() = (issuedMinor - repaidMinor - writtenOffMinor).coerceAtLeast(0L)
    val settled: Boolean get() = issuedMinor > 0L && repaidMinor + writtenOffMinor >= issuedMinor
    val percent: Int
        get() = if (issuedMinor <= 0L) 0
        else ((repaidMinor + writtenOffMinor).coerceAtLeast(0L) * 100L / issuedMinor).coerceAtMost(100L).toInt()
}

class DebtOverpayException : Exception("Repayment is larger than what is left")

interface DebtRepository {
    suspend fun getAll(): DebtData
    suspend fun replaceAll(data: DebtData)

    suspend fun debt(id: Long): Debt?
    suspend fun insertDebt(debt: Debt): Long
    suspend fun updateDebt(debt: Debt)

    /** Deletes the debt together with its entries. */
    suspend fun deleteDebt(id: Long)

    suspend fun insertEntry(entry: DebtEntry): Long
    suspend fun updateEntry(entry: DebtEntry)
    suspend fun deleteEntry(id: Long)
}

class InMemoryDebtRepository : DebtRepository {
    private var debts = mutableListOf<Debt>()
    private var entries = mutableListOf<DebtEntry>()
    private var nextDebtId = 1L
    private var nextEntryId = 1L

    override suspend fun getAll(): DebtData = DebtData(debts.toList(), entries.toList())

    override suspend fun replaceAll(data: DebtData) {
        debts = data.debts.toMutableList()
        entries = data.entries.toMutableList()
        nextDebtId = (debts.maxOfOrNull { it.id } ?: 0L) + 1L
        nextEntryId = (entries.maxOfOrNull { it.id } ?: 0L) + 1L
    }

    override suspend fun debt(id: Long): Debt? = debts.firstOrNull { it.id == id }

    override suspend fun insertDebt(debt: Debt): Long {
        val id = if (debt.id == 0L) nextDebtId++ else debt.id
        debts.add(debt.copy(id = id))
        return id
    }

    override suspend fun updateDebt(debt: Debt) {
        val index = debts.indexOfFirst { it.id == debt.id }
        require(index >= 0) { "debt not found" }
        debts[index] = debt
    }

    override suspend fun deleteDebt(id: Long) {
        debts.removeAll { it.id == id }
        entries.removeAll { it.debtId == id }
    }

    override suspend fun insertEntry(entry: DebtEntry): Long {
        val id = if (entry.id == 0L) nextEntryId++ else entry.id
        entries.add(entry.copy(id = id))
        return id
    }

    override suspend fun updateEntry(entry: DebtEntry) {
        val index = entries.indexOfFirst { it.id == entry.id }
        require(index >= 0) { "entry not found" }
        entries[index] = entry
    }

    override suspend fun deleteEntry(id: Long) {
        entries.removeAll { it.id == id }
    }
}
