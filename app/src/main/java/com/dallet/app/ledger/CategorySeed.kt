package com.dallet.app.ledger

object CategorySeed {
    fun defaults(): List<Category> = listOf(
        Category(1, "Зарплата", CategoryKind.INCOME, 0xFF2E7D32.toInt(), "work"),
        Category(2, "Подработка", CategoryKind.INCOME, 0xFF558B2F.toInt(), "freelance"),
        Category(3, "Еда", CategoryKind.EXPENSE, 0xFFE65100.toInt(), "food"),
        Category(4, "Транспорт", CategoryKind.EXPENSE, 0xFF1565C0.toInt(), "transport"),
        Category(5, "Дом", CategoryKind.EXPENSE, 0xFF6A1B9A.toInt(), "home"),
        Category(6, "Прочее", CategoryKind.EXPENSE, 0xFF546E7A.toInt(), "other")
    )
}
