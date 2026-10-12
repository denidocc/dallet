package com.dallet.app.ui.comparison

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ledger.CategoryKind
import com.dallet.app.ui.ComparisonRowUi
import com.dallet.app.ui.ComparisonSideUi
import com.dallet.app.ui.ComparisonUiState
import com.dallet.app.ui.common.SurfaceCard
import com.dallet.app.ui.moneyText
import com.dallet.app.ui.theme.expenseAmountColor
import com.dallet.app.ui.theme.incomeAmountColor
import kotlin.math.abs

@Composable
fun ComparisonScreen(state: ComparisonUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.comparison_title),
            style = MaterialTheme.typography.titleLarge
        )
        val a = state.a
        val b = state.b
        if (!state.loaded || a == null || b == null) return@Column

        SideCard(side = a)
        SideCard(side = b)
        DifferenceCard(state = state)
        if (state.lengthsDiffer) {
            Text(
                text = stringResource(R.string.comparison_lengths_differ, a.days, b.days),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!a.isComplete || !b.isComplete) {
            Text(
                text = stringResource(R.string.analytics_incomplete),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        CategoryTable(
            title = stringResource(R.string.comparison_expense_by_category),
            rows = state.rows.filter { it.kind == CategoryKind.EXPENSE },
            higherIsGood = false
        )
        CategoryTable(
            title = stringResource(R.string.comparison_income_by_category),
            rows = state.rows.filter { it.kind == CategoryKind.INCOME },
            higherIsGood = true
        )
        if (state.rows.isEmpty()) {
            Text(
                text = stringResource(R.string.comparison_no_operations),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SideCard(side: ComparisonSideUi) {
    SurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = side.label,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.comparison_days, side.days),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = side.periodLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
        )
        AmountRow(
            label = stringResource(R.string.analytics_expense_label),
            value = moneyText(side.expenseLabel),
            average = moneyText(side.averageExpenseLabel),
            color = expenseAmountColor()
        )
        AmountRow(
            label = stringResource(R.string.analytics_income_label),
            value = moneyText(side.incomeLabel),
            average = moneyText(side.averageIncomeLabel),
            color = incomeAmountColor()
        )
    }
}

@Composable
private fun AmountRow(label: String, value: String, average: String, color: Color) {
    Column(modifier = Modifier.padding(top = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(text = value, style = MaterialTheme.typography.titleMedium, color = color)
        }
        Text(
            text = stringResource(R.string.comparison_average_day, average),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DifferenceCard(state: ComparisonUiState) {
    SurfaceCard {
        Text(
            text = stringResource(R.string.comparison_difference),
            style = MaterialTheme.typography.titleMedium
        )
        DifferenceRow(
            label = stringResource(R.string.analytics_expense_label),
            diff = moneyText(state.expenseDiffLabel),
            sign = state.expenseDiffSign,
            percent = state.expenseChangePercent,
            higherIsGood = false
        )
        DifferenceRow(
            label = stringResource(R.string.analytics_income_label),
            diff = moneyText(state.incomeDiffLabel),
            sign = state.incomeDiffSign,
            percent = state.incomeChangePercent,
            higherIsGood = true
        )
    }
}

@Composable
private fun DifferenceRow(
    label: String,
    diff: String,
    sign: Int,
    percent: Int?,
    higherIsGood: Boolean
) {
    val color = changeColor(sign, higherIsGood)
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(text = diff, style = MaterialTheme.typography.titleMedium, color = color)
        }
        Text(
            text = percentText(percent),
            style = MaterialTheme.typography.labelMedium,
            color = if (percent == null) MaterialTheme.colorScheme.onSurfaceVariant else color
        )
    }
}

@Composable
private fun CategoryTable(title: String, rows: List<ComparisonRowUi>, higherIsGood: Boolean) {
    if (rows.isEmpty()) return
    Text(text = title, style = MaterialTheme.typography.titleMedium)
    SurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { row -> CategoryRow(row = row, higherIsGood = higherIsGood) }
        }
    }
}

@Composable
private fun CategoryRow(row: ComparisonRowUi, higherIsGood: Boolean) {
    val color = changeColor(row.diffSign, higherIsGood)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = row.name, style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "A ${moneyText(row.aLabel)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "B ${moneyText(row.bLabel)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = moneyText(row.diffLabel) + " · " + percentText(row.changePercent),
            style = MaterialTheme.typography.labelLarge,
            color = color
        )
    }
}

/** A rise is bad for expenses and good for income; no change gets the neutral colour. */
@Composable
private fun changeColor(sign: Int, higherIsGood: Boolean): Color = when {
    sign == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
    (sign > 0) == higherIsGood -> incomeAmountColor()
    else -> expenseAmountColor()
}

@Composable
private fun percentText(percent: Int?): String {
    if (percent == null) return stringResource(R.string.comparison_no_base)
    val arrow = when {
        percent > 0 -> "▲"
        percent < 0 -> "▼"
        else -> "▬"
    }
    return "$arrow ${abs(percent)}%"
}
