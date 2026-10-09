package com.dallet.app.ledger

object CategorySeed {
    fun defaults(names: List<String>? = null): List<Category> = listOf(
        Category(1, nameAt(names, 1 - 1, DEFAULT_NAMES), CategoryKind.INCOME, 0xFF2E7D32.toInt(), "work"),
        Category(2, nameAt(names, 2 - 1, DEFAULT_NAMES), CategoryKind.INCOME, 0xFF558B2F.toInt(), "freelance"),
        Category(3, nameAt(names, 3 - 1, DEFAULT_NAMES), CategoryKind.EXPENSE, 0xFFE65100.toInt(), "food"),
        Category(4, nameAt(names, 4 - 1, DEFAULT_NAMES), CategoryKind.EXPENSE, 0xFF1565C0.toInt(), "transport"),
        Category(5, nameAt(names, 5 - 1, DEFAULT_NAMES), CategoryKind.EXPENSE, 0xFF6A1B9A.toInt(), "home"),
        Category(6, nameAt(names, 6 - 1, DEFAULT_NAMES), CategoryKind.EXPENSE, 0xFF546E7A.toInt(), "other")
    )

    private val DEFAULT_NAMES =
        listOf("Зарплата", "Подработка", "Еда", "Транспорт", "Дом", "Прочее")

    private fun nameAt(names: List<String>?, index: Int, fallback: List<String>): String =
        names?.getOrNull(index)?.takeIf { it.isNotBlank() } ?: fallback[index]
}
