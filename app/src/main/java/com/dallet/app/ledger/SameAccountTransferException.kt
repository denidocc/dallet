package com.dallet.app.ledger

class SameAccountTransferException :
    IllegalArgumentException("Нельзя перевести на тот же счёт")
