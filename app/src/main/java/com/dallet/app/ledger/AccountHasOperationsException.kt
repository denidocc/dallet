package com.dallet.app.ledger

class AccountHasOperationsException :
    IllegalStateException("Нельзя удалить счёт, пока у него есть операции")
