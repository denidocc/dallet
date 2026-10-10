package com.dallet.app.ledger

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

enum class Frequency {
    WEEKLY,
    MONTHLY,
    YEARLY
}

/** An expense or income that repeats on a schedule and is confirmed by the user when due. */
data class RecurringRule(
    val id: Long,
    val kind: OperationKind,
    val accountId: Long,
    val categoryId: Long,
    val amountMinor: Long,
    val comment: String?,
    val frequency: Frequency,
    val startDate: LocalDate,
    val nextDue: LocalDate
)

/** One due occurrence; only the earliest occurrence of a rule can be handled ([isFirst]). */
data class DueItem(
    val rule: RecurringRule,
    val dueDate: LocalDate,
    val isFirst: Boolean
)

object RecurringSchedule {
    const val MAX_OCCURRENCES = 60

    /** The occurrence that follows [date]; day of month is kept from the start date. */
    fun occurrenceAfter(rule: RecurringRule, date: LocalDate): LocalDate = when (rule.frequency) {
        Frequency.WEEKLY -> date.plusDays(7)
        Frequency.MONTHLY -> {
            val month = YearMonth.from(date).plusMonths(1)
            month.atDay(minOf(rule.startDate.dayOfMonth, month.lengthOfMonth()))
        }
        Frequency.YEARLY -> {
            val month = YearMonth.of(date.year + 1, rule.startDate.month)
            month.atDay(minOf(rule.startDate.dayOfMonth, month.lengthOfMonth()))
        }
    }

    /** All occurrences from the rule's next due date up to and including [today]. */
    fun dueDates(rule: RecurringRule, today: LocalDate): List<LocalDate> {
        val result = ArrayList<LocalDate>()
        var current = rule.nextDue
        while (!current.isAfter(today) && result.size < MAX_OCCURRENCES) {
            result.add(current)
            current = occurrenceAfter(rule, current)
        }
        return result
    }
}

object RecurringCodec {
    fun encode(rules: List<RecurringRule>): JSONArray = JSONArray().apply {
        rules.forEach { rule ->
            val item = JSONObject()
                .put("id", rule.id)
                .put("kind", rule.kind.name)
                .put("accountId", rule.accountId)
                .put("categoryId", rule.categoryId)
                .put("amountMinor", rule.amountMinor)
                .put("frequency", rule.frequency.name)
                .put("startDate", rule.startDate.toString())
                .put("nextDue", rule.nextDue.toString())
            rule.comment?.let { item.put("comment", it) }
            put(item)
        }
    }

    fun decode(array: JSONArray?): List<RecurringRule> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            RecurringRule(
                id = item.getLong("id"),
                kind = OperationKind.valueOf(item.getString("kind")),
                accountId = item.getLong("accountId"),
                categoryId = item.getLong("categoryId"),
                amountMinor = item.getLong("amountMinor"),
                comment = if (item.isNull("comment")) null else item.getString("comment"),
                frequency = Frequency.valueOf(item.getString("frequency")),
                startDate = LocalDate.parse(item.getString("startDate")),
                nextDue = LocalDate.parse(item.getString("nextDue"))
            )
        }
    }

    fun toText(rules: List<RecurringRule>): String = encode(rules).toString()

    fun fromText(text: String?): List<RecurringRule> =
        if (text.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                decode(JSONArray(text))
            } catch (_: Exception) {
                emptyList()
            }
        }
}
