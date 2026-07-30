package com.dallet.app.ledger

class CategoryHasOperationsException :
    IllegalStateException("Нельзя удалить категорию, пока у неё есть операции")
