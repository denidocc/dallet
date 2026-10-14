package com.dallet.app.ui.comparison

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ledger.CategoryKind
import com.dallet.app.ledger.HistoryPeriod
import com.dallet.app.ledger.OperationKind
import com.dallet.app.ui.ComparisonRowUi
import com.dallet.app.ui.ComparisonSideId
import com.dallet.app.ui.ComparisonSideUi
import com.dallet.app.ui.ComparisonUiState
import com.dallet.app.ui.common.SurfaceCard
import com.dallet.app.ui.history.HistoryFilterSheet
import com.dallet.app.ui.moneyText
import com.dallet.app.ui.theme.expenseAmountColor
import com.dallet.app.ui.theme.incomeAmountColor
import java.time.LocalDate
import kotlin.math.abs

/** Everything the comparison screen can do to either side. */
class ComparisonActions(
    val onKindSelected: (ComparisonSideId, OperationKind?) -> Unit,
    val onAccountToggled: (ComparisonSideId, Long) -> Unit,
    val onCategoryToggled: (ComparisonSideId, Long) -> Unit,
    val onPeriodSelected: (ComparisonSideId, HistoryPeriod) -> Unit,
    val onCustomPeriod: (ComparisonSideId, LocalDate, LocalDate) -> Unit,
    val onResetSide: (ComparisonSideId) -> Unit,
    val onPeriodVsPeriod: () -> Unit,
    val onCategoryVsCategory: () -> Unit
)

@Composable
fun ComparisonScreen(state: ComparisonUiState, actions: ComparisonActions) {
    var editing by remember { mutableStateOf<ComparisonSideId?>(null) }
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = actions.onPeriodVsPeriod, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.comparison_period_vs_period))
            }
            OutlinedButton(
                onClick = {
                    actions.onCategoryVsCategory()
                    // B starts without categories: let the user pick the ones to compare with.
                    editing = ComparisonSideId.B
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.comparison_category_vs_category))
            }
        }
        SideCard(side = a, onEdit = { editing = ComparisonSideId.A })
        SideCard(side = b, onEdit = { editing = ComparisonSideId.B })
        DifferenceCard(state = state, a = a, b = b)
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
            titleA = a.title,
            titleB = b.title,
            higherIsGood = false
        )
        CategoryTable(
            title = stringResource(R.string.comparison_income_by_category),
            rows = state.rows.filter { it.kind == CategoryKind.INCOME },
            titleA = a.title,
            titleB = b.title,
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
    editing?.let { side ->
        HistoryFilterSheet(
            state = if (side == ComparisonSideId.A) state.sheetA else state.sheetB,
            onKindSelected = { actions.onKindSelected(side, it) },
            onAccountToggled = { actions.onAccountToggled(side, it) },
            onCategoryToggled = { actions.onCategoryToggled(side, it) },
            onPeriodSelected = { actions.onPeriodSelected(side, it) },
            onCustomPeriod = { from, to -> actions.onCustomPeriod(side, from, to) },
            onReset = { actions.onResetSide(side) },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun SideCard(side: ComparisonSideUi, onEdit: () -> Unit) {
    SurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = side.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onEdit) {
                Text(stringResource(R.string.comparison_edit))
            }
        }
        Text(
            text = listOf(side.subtitle, stringResource(R.string.comparison_days, side.days))
                .filter { it.isNotEmpty() }
                .joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
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
private fun DifferenceCard(state: ComparisonUiState, a: ComparisonSideUi, b: ComparisonSideUi) {
    SurfaceCard {
        Text(
            text = stringResource(R.string.comparison_difference),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.comparison_difference_hint, a.title, b.title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DifferenceRow(
            label = stringResource(R.string.analytics_expense_label),
            diff = moneyText(state.expenseDiffLabel),
            sign = state.expenseDiffSign,
            percent = state.expenseChangePercent,
            baseTitle = b.title,
            higherIsGood = false
        )
        DifferenceRow(
            label = stringResource(R.string.analytics_income_label),
            diff = moneyText(state.incomeDiffLabel),
            sign = state.incomeDiffSign,
            percent = state.incomeChangePercent,
            baseTitle = b.title,
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
    baseTitle: String,
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
            text = percentText(percent, baseTitle),
            style = MaterialTheme.typography.labelMedium,
            color = if (percent == null) MaterialTheme.colorScheme.onSurfaceVariant else color
        )
    }
}

@Composable
private fun CategoryTable(
    title: String,
    rows: List<ComparisonRowUi>,
    titleA: String,
    titleB: String,
    higherIsGood: Boolean
) {
    if (rows.isEmpty()) return
    Text(text = title, style = MaterialTheme.typography.titleMedium)
    SurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = titleA,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = titleB,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
            rows.forEach { row -> CategoryRow(row = row, higherIsGood = higherIsGood, baseTitle = titleB) }
        }
    }
}

@Composable
private fun CategoryRow(row: ComparisonRowUi, higherIsGood: Boolean, baseTitle: String) {
    val color = changeColor(row.diffSign, higherIsGood)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = row.name, style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = moneyText(row.aLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = moneyText(row.bLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = moneyText(row.diffLabel) + " · " + percentText(row.changePercent, baseTitle),
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
private fun percentText(percent: Int?, baseTitle: String): String {
    if (percent == null) return stringResource(R.string.comparison_no_base, baseTitle)
    val arrow = when {
        percent > 0 -> "▲"
        percent < 0 -> "▼"
        else -> "▬"
    }
    return "$arrow ${abs(percent)}%"
}
