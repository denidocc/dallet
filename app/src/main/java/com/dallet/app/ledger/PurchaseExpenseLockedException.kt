package com.dallet.app.ledger

class PurchaseExpenseLockedException :
    IllegalStateException("Сумма расхода из закупа берётся из списка: верните закуп в работу")
