package com.dallet.app.ui.analytics

import com.dallet.app.ui.common.AppButtonShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ui.moneyText
import com.dallet.app.ledger.AnalyticsPeriod
import com.dallet.app.ui.AccountStatUi
import com.dallet.app.ui.AnalyticsCategorySliceUi
import com.dallet.app.ui.AnalyticsFlowPointUi
import com.dallet.app.ui.AnalyticsMode
import com.dallet.app.ui.AnalyticsUiState
import com.dallet.app.ui.DataUiState
import com.dallet.app.ui.ReportFormat
import com.dallet.app.ui.common.SurfaceCard
import com.dallet.app.ui.theme.expenseAmountColor
import com.dallet.app.ui.theme.incomeAmountColor
import com.dallet.app.ui.common.DateRangePickerDialog
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

private const val SHOW_COMPARISON = true

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyticsScreen(
    state: AnalyticsUiState,
    onPeriodSelected: (AnalyticsPeriod) -> Unit,
    onCustomPeriod: (LocalDate, LocalDate) -> Unit,
    onCompare: () -> Unit,
    onModeSelected: (AnalyticsMode) -> Unit,
    onStep: (Int) -> Unit,
    dataState: DataUiState,
    onExportReport: (ReportFormat) -> Unit
) {
    var showExportDialog by remember { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }
    if (showRangePicker) {
        DateRangePickerDialog(
            initialFrom = state.customFrom,
            initialTo = state.customTo,
            onConfirm = { from, to ->
                showRangePicker = false
                onCustomPeriod(from, to)
            },
            onDismiss = { showRangePicker = false }
        )
    }
    val isIncome = state.mode == AnalyticsMode.INCOME
    val slices = if (isIncome) state.incomeSlices else state.categorySlices
    val flow = if (isIncome) state.incomeFlowPoints else state.flowPoints
    val totalLabel = if (isIncome) state.incomeTotalLabel else state.expenseTotalLabel
    val emptyText = stringResource(
        if (isIncome) R.string.analytics_chart_empty_income else R.string.analytics_chart_empty
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.analytics_title),
            style = MaterialTheme.typography.titleLarge
        )
        // Four chips do not fit one line on a narrow phone, so they may wrap.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.period == AnalyticsPeriod.WEEK,
                onClick = { onPeriodSelected(AnalyticsPeriod.WEEK) },
                label = { Text(stringResource(R.string.analytics_period_week)) }
            )
            FilterChip(
                selected = state.period == AnalyticsPeriod.MONTH,
                onClick = { onPeriodSelected(AnalyticsPeriod.MONTH) },
                label = { Text(stringResource(R.string.analytics_period_month)) }
            )
            FilterChip(
                selected = state.period == AnalyticsPeriod.YEAR,
                onClick = { onPeriodSelected(AnalyticsPeriod.YEAR) },
                label = { Text(stringResource(R.string.analytics_period_year)) }
            )
            FilterChip(
                selected = state.period == AnalyticsPeriod.CUSTOM,
                onClick = { showRangePicker = true },
                label = { Text(stringResource(R.string.analytics_period_custom)) }
            )
        }
        PeriodNavigator(
            label = state.periodLabel,
            canGoForward = state.canGoForward,
            onBack = { onStep(-1) },
            onForward = { onStep(1) }
        )
        if (SHOW_COMPARISON) {
            OutlinedButton(shape = AppButtonShape, onClick = onCompare, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.analytics_compare))
            }
        }
        if (!state.isComplete) {
            Text(
                text = stringResource(R.string.analytics_incomplete),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(
                label = stringResource(R.string.analytics_income_label),
                value = moneyText(state.incomeTotalLabel),
                valueColor = incomeAmountColor(),
                changePercent = state.incomeChangePercent,
                higherIsGood = true,
                period = state.period,
                selected = state.mode == AnalyticsMode.INCOME,
                onClick = { onModeSelected(AnalyticsMode.INCOME) },
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                label = stringResource(R.string.analytics_expense_label),
                value = moneyText(state.expenseTotalLabel),
                valueColor = expenseAmountColor(),
                changePercent = state.expenseChangePercent,
                higherIsGood = false,
                period = state.period,
                selected = state.mode == AnalyticsMode.EXPENSE,
                onClick = { onModeSelected(AnalyticsMode.EXPENSE) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MiniSummaryCard(
                label = stringResource(R.string.analytics_goals_label),
                value = moneyText(state.savings.goalsNetLabel),
                selected = state.mode == AnalyticsMode.GOALS,
                onClick = { onModeSelected(AnalyticsMode.GOALS) },
                modifier = Modifier.weight(1f)
            )
            MiniSummaryCard(
                label = stringResource(R.string.analytics_debts_label),
                value = moneyText(state.savings.debtsNetLabel),
                selected = state.mode == AnalyticsMode.DEBTS,
                onClick = { onModeSelected(AnalyticsMode.DEBTS) },
                modifier = Modifier.weight(1f)
            )
        }
        when (state.mode) {
            AnalyticsMode.GOALS -> GoalsAnalytics(state)
            AnalyticsMode.DEBTS -> DebtsAnalytics(state)
            else -> {

        Text(
            text = stringResource(
                if (isIncome) R.string.analytics_income_by_category
                else R.string.analytics_by_category
            ),
            style = MaterialTheme.typography.titleMedium
        )
        if (slices.isEmpty()) {
            SurfaceCard {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            SurfaceCard {
                CategoryDonut(
                    slices = slices,
                    centerLabel = stringResource(
                        if (isIncome) R.string.analytics_income_label
                        else R.string.analytics_expense_label
                    ),
                    totalLabel = moneyText(totalLabel)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    slices.forEach { slice ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(slice.colorArgb))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = slice.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (slice.details.isNotEmpty()) {
                                    Text(
                                        text = slice.details,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = moneyText(slice.amountLabel),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = stringResource(
                if (isIncome) R.string.analytics_income_flow
                else R.string.analytics_expense_flow
            ),
            style = MaterialTheme.typography.titleMedium
        )
        if (flow.all { it.fraction <= 0f && it.previousFraction <= 0f }) {
            SurfaceCard {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            SurfaceCard {
                FlowLegend()
                Spacer(modifier = Modifier.height(12.dp))
                FlowChart(points = flow)
            }
        }

        if (state.accountStats.isNotEmpty()) {
            Text(
                text = stringResource(R.string.analytics_by_account),
                style = MaterialTheme.typography.titleMedium
            )
            SurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    state.accountStats.forEach { stat ->
                        AccountStat(stat)
                    }
                }
            }
        }

        SurfaceCard(onClick = if (dataState.busy) null else ({ showExportDialog = true })) {
            Text(
                text = stringResource(R.string.report_export),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.report_export_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        dataState.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (dataState.isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(R.string.report_export)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = state.periodLabel,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    TextButton(
                        onClick = {
                            showExportDialog = false
                            onExportReport(ReportFormat.PDF)
                        }
                    ) {
                        Text(stringResource(R.string.report_format_pdf))
                    }
                    TextButton(
                        onClick = {
                            showExportDialog = false
                            onExportReport(ReportFormat.CSV)
                        }
                    ) {
                        Text(stringResource(R.string.report_format_csv))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun AccountStat(stat: AccountStatUi) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stat.name, style = MaterialTheme.typography.titleMedium)
        StatBar(
            label = stringResource(R.string.analytics_income_label),
            value = moneyText(stat.incomeLabel),
            fraction = stat.incomeFraction,
            color = incomeAmountColor()
        )
        StatBar(
            label = stringResource(R.string.analytics_expense_label),
            value = moneyText(stat.expenseLabel),
            fraction = stat.expenseFraction,
            color = expenseAmountColor()
        )
    }
}

@Composable
private fun StatBar(
    label: String,
    value: String,
    fraction: Float,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun PeriodNavigator(
    label: String,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        NavButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            description = stringResource(R.string.analytics_prev),
            enabled = true,
            onClick = onBack
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        NavButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            description = stringResource(R.string.analytics_next),
            enabled = canGoForward,
            onClick = onForward
        )
    }
}

@Composable
private fun NavButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        )
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    valueColor: Color,
    changePercent: Int?,
    higherIsGood: Boolean,
    period: AnalyticsPeriod,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SurfaceCard(modifier = modifier, onClick = onClick, selected = selected) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = valueColor,
            modifier = Modifier.padding(top = 6.dp)
        )
        ChangeLine(
            percent = changePercent,
            higherIsGood = higherIsGood,
            period = period,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ChangeLine(
    percent: Int?,
    higherIsGood: Boolean,
    period: AnalyticsPeriod,
    modifier: Modifier = Modifier
) {
    if (percent == null) {
        Text(
            text = stringResource(R.string.analytics_no_previous),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }
    val noun = stringResource(
        when (period) {
            AnalyticsPeriod.WEEK -> R.string.analytics_vs_week
            AnalyticsPeriod.MONTH -> R.string.analytics_vs_month
            AnalyticsPeriod.YEAR -> R.string.analytics_vs_year
            AnalyticsPeriod.CUSTOM -> R.string.analytics_vs_custom
        }
    )
    val arrow = when {
        percent > 0 -> "▲"
        percent < 0 -> "▼"
        else -> "▬"
    }
    val color = when {
        percent == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        (percent > 0) == higherIsGood -> incomeAmountColor()
        else -> expenseAmountColor()
    }
    Text(
        text = "$arrow ${abs(percent)}% $noun",
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
    )
}

@Composable
private fun CategoryDonut(
    slices: List<AnalyticsCategorySliceUi>,
    centerLabel: String,
    totalLabel: String
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val stroke = Stroke(width = size.minDimension * 0.22f, cap = StrokeCap.Butt)
            val diameter = size.minDimension
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)
            if (slices.isEmpty()) {
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke
                )
                return@Canvas
            }
            var start = -90f
            slices.forEach { slice ->
                val sweep = 360f * slice.fraction
                drawArc(
                    color = Color(slice.colorArgb),
                    startAngle = start,
                    sweepAngle = sweep.coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke
                )
                start += sweep
            }
        }
        Column(
            modifier = Modifier.width(110.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = totalLabel,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun previousBarColor(): Color =
    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

@Composable
private fun FlowLegend() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(
            color = MaterialTheme.colorScheme.primary,
            label = stringResource(R.string.analytics_legend_current)
        )
        LegendItem(
            color = previousBarColor(),
            label = stringResource(R.string.analytics_legend_previous)
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FlowChart(points: List<AnalyticsFlowPointUi>) {
    val currentColor = MaterialTheme.colorScheme.primary
    val previousColor = previousBarColor()
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        points.forEach { point ->
            Column(
                modifier = Modifier.width(BucketWidth),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.height(BarAreaHeight),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .width(PreviousBarWidth)
                            .height(barHeight(point.previousFraction))
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(previousColor)
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (point.fraction > 0f) {
                            Text(
                                text = compactAmount(point.amountLabel),
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .wrapContentWidth(unbounded = true)
                                    .padding(bottom = 4.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(CurrentBarWidth)
                                .height(barHeight(point.fraction))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(currentColor)
                        )
                    }
                }
                Text(
                    text = point.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

private val BucketWidth = 62.dp
private val BarAreaHeight = 128.dp
private val MaxBarHeight = 100.dp
private val PreviousBarWidth = 22.dp
private val CurrentBarWidth = 28.dp

private fun barHeight(fraction: Float) =
    if (fraction > 0f) MaxBarHeight * fraction.coerceIn(0.02f, 1f) else 2.dp

private fun compactAmount(label: String): String {
    val value = label.toDoubleOrNull() ?: return label
    return when {
        value >= 1000.0 -> {
            val thousands = String.format(Locale.US, "%.1f", value / 1000.0).removeSuffix(".0")
            thousands + "k"
        }
        value % 1.0 == 0.0 -> value.toLong().toString()
        else -> String.format(Locale.US, "%.1f", value).removeSuffix(".0")
    }
}

@Composable
private fun MiniSummaryCard(
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun SliceLegend(slices: List<AnalyticsCategorySliceUi>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        slices.forEach { slice ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(slice.colorArgb))
                )
                Text(
                    text = slice.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = moneyText(slice.amountLabel),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    SurfaceCard {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GoalsAnalytics(state: AnalyticsUiState) {
    val data = state.savings
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SurfaceCard {
            AmountLine(stringResource(R.string.analytics_goals_deposited), moneyText(data.goalsDepositedLabel), incomeAmountColor())
            Spacer(modifier = Modifier.height(6.dp))
            AmountLine(stringResource(R.string.analytics_goals_withdrawn), moneyText(data.goalsWithdrawnLabel), expenseAmountColor())
        }
        Text(
            text = stringResource(R.string.analytics_goals_by_goal),
            style = MaterialTheme.typography.titleMedium
        )
        if (data.goalSlices.isEmpty()) {
            EmptyCard(stringResource(R.string.analytics_goals_empty))
        } else {
            SurfaceCard {
                CategoryDonut(
                    slices = data.goalSlices,
                    centerLabel = stringResource(R.string.analytics_goals_label),
                    totalLabel = moneyText(data.goalsDepositedLabel)
                )
                Spacer(modifier = Modifier.height(16.dp))
                SliceLegend(data.goalSlices)
            }
        }
        Text(
            text = stringResource(R.string.analytics_goals_flow),
            style = MaterialTheme.typography.titleMedium
        )
        if (data.goalFlowPoints.all { it.fraction <= 0f && it.previousFraction <= 0f }) {
            EmptyCard(stringResource(R.string.analytics_goals_empty))
        } else {
            SurfaceCard {
                FlowLegend()
                Spacer(modifier = Modifier.height(12.dp))
                FlowChart(points = data.goalFlowPoints)
            }
        }
        if (data.goalProgress.isNotEmpty()) {
            Text(
                text = stringResource(R.string.analytics_goals_progress),
                style = MaterialTheme.typography.titleMedium
            )
            SurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    data.goalProgress.forEach { goal ->
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = goal.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${goal.percent}%",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(goal.percent.coerceIn(0, 100) / 100f)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                            Text(
                                text = moneyText(goal.savedLabel) + " / " + moneyText(goal.targetLabel),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebtsAnalytics(state: AnalyticsUiState) {
    val data = state.savings
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SurfaceCard {
            AmountLine(stringResource(R.string.analytics_debts_owed_to_me), moneyText(data.owedToMeLabel), incomeAmountColor())
            Spacer(modifier = Modifier.height(6.dp))
            AmountLine(stringResource(R.string.analytics_debts_i_owe), moneyText(data.iOweLabel), expenseAmountColor())
        }
        Text(
            text = stringResource(R.string.analytics_debts_owed_to_me),
            style = MaterialTheme.typography.titleMedium
        )
        if (data.owedToMeSlices.isEmpty()) {
            EmptyCard(stringResource(R.string.analytics_debts_empty))
        } else {
            SurfaceCard {
                CategoryDonut(
                    slices = data.owedToMeSlices,
                    centerLabel = stringResource(R.string.analytics_debts_label),
                    totalLabel = moneyText(data.owedToMeLabel)
                )
                Spacer(modifier = Modifier.height(16.dp))
                SliceLegend(data.owedToMeSlices)
            }
        }
        if (data.iOweSlices.isNotEmpty()) {
            Text(
                text = stringResource(R.string.analytics_debts_i_owe),
                style = MaterialTheme.typography.titleMedium
            )
            SurfaceCard {
                CategoryDonut(
                    slices = data.iOweSlices,
                    centerLabel = stringResource(R.string.analytics_debts_label),
                    totalLabel = moneyText(data.iOweLabel)
                )
                Spacer(modifier = Modifier.height(16.dp))
                SliceLegend(data.iOweSlices)
            }
        }
        Text(
            text = stringResource(R.string.analytics_debts_movement),
            style = MaterialTheme.typography.titleMedium
        )
        if (data.debtMovement.isEmpty()) {
            EmptyCard(stringResource(R.string.analytics_debts_movement_empty))
        } else {
            SurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    data.debtMovement.forEach { row ->
                        AmountLine(row.label, moneyText(row.amountLabel), MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountLine(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor
        )
    }
}
