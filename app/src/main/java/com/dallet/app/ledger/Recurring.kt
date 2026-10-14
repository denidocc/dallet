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
    val nextDue: LocalDate,
    /** Date of the last payment of a loan-like rule; null when the rule never ends. */
    val endDate: LocalDate? = null,
    /** The final payment when the total did not split evenly; null means the usual amount. */
    val lastAmountMinor: Long? = null,
    /** How many payments the rule had when it was made; shown as "left N of M". */
    val totalPayments: Int? = null
)

/** A total split into equal payments; the last one takes the rounding remainder. */
data class RecurringPlan(
    val count: Int,
    val paymentMinor: Long,
    val lastPaymentMinor: Long
)

/** One due occurrence; only the earliest occurrence of a rule can be handled ([isFirst]). */
data class DueItem(
    val rule: RecurringRule,
    val dueDate: LocalDate,
    val isFirst: Boolean
)

object RecurringSchedule {
    const val MAX_OCCURRENCES = 60
    private const val MAX_PLAN_PAYMENTS = 600

    /** The occurrence that follows [date]; day of month is kept from the start date. */
    fun occurrenceAfter(rule: RecurringRule, date: LocalDate): LocalDate =
        next(rule.frequency, rule.startDate, date)

    private fun next(frequency: Frequency, startDate: LocalDate, date: LocalDate): LocalDate =
        when (frequency) {
            Frequency.WEEKLY -> date.plusDays(7)
            Frequency.MONTHLY -> {
                val month = YearMonth.from(date).plusMonths(1)
                month.atDay(minOf(startDate.dayOfMonth, month.lengthOfMonth()))
            }
            Frequency.YEARLY -> {
                val month = YearMonth.of(date.year + 1, startDate.month)
                month.atDay(minOf(startDate.dayOfMonth, month.lengthOfMonth()))
            }
        }

    /** The last day covered by a plan that lasts [months] months from [start]. */
    fun endForMonths(start: LocalDate, months: Int): LocalDate = start.plusMonths(months.toLong()).minusDays(1)

    /** Occurrences from [from] to [end], both included. */
    private fun countBetween(frequency: Frequency, startDate: LocalDate, from: LocalDate, end: LocalDate): Int {
        var count = 0
        var current = from
        while (!current.isAfter(end) && count < MAX_PLAN_PAYMENTS) {
            count++
            current = next(frequency, startDate, current)
        }
        return count
    }

    /** Splits [totalMinor] over every occurrence from [start] to [end]; null when that is not possible. */
    fun plan(frequency: Frequency, start: LocalDate, end: LocalDate, totalMinor: Long): RecurringPlan? {
        if (totalMinor <= 0L || end.isBefore(start)) return null
        val count = countBetween(frequency, start, start, end)
        if (count < 1 || count >= MAX_PLAN_PAYMENTS) return null
        val payment = totalMinor / count
        if (payment < 1L) return null
        return RecurringPlan(count, payment, totalMinor - payment * (count - 1))
    }

    /** Payments still to come, or null for a rule without an end. */
    fun remainingPayments(rule: RecurringRule): Int? {
        val end = rule.endDate ?: return null
        if (rule.nextDue.isAfter(end)) return 0
        return countBetween(rule.frequency, rule.startDate, rule.nextDue, end)
    }

    fun isFinished(rule: RecurringRule): Boolean = rule.endDate?.let { rule.nextDue.isAfter(it) } ?: false

    /** The amount to suggest for the occurrence on [date]. */
    fun amountFor(rule: RecurringRule, date: LocalDate): Long {
        val last = rule.lastAmountMinor ?: return rule.amountMinor
        val end = rule.endDate ?: return rule.amountMinor
        return if (occurrenceAfter(rule, date).isAfter(end)) last else rule.amountMinor
    }

    /** All occurrences from the rule's next due date up to and including [today]. */
    fun dueDates(rule: RecurringRule, today: LocalDate): List<LocalDate> {
        val result = ArrayList<LocalDate>()
        var current = rule.nextDue
        while (!current.isAfter(today) && result.size < MAX_OCCURRENCES) {
            if (rule.endDate != null && current.isAfter(rule.endDate)) break
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
            rule.endDate?.let { item.put("endDate", it.toString()) }
            rule.lastAmountMinor?.let { item.put("lastAmountMinor", it) }
            rule.totalPayments?.let { item.put("totalPayments", it) }
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
                nextDue = LocalDate.parse(item.getString("nextDue")),
                endDate = if (item.isNull("endDate")) null else LocalDate.parse(item.getString("endDate")),
                lastAmountMinor = if (item.isNull("lastAmountMinor")) null else item.getLong("lastAmountMinor"),
                totalPayments = if (item.isNull("totalPayments")) null else item.getInt("totalPayments")
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
