package com.dallet.app.ledger

data class Account(
    val id: Long,
    val name: String,
    val openingBalanceMinor: Long
)

data class AccountBalance(
    val account: Account,
    val balanceMinor: Long
)
