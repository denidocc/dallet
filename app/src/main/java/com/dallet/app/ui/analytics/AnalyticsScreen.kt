package com.dallet.app.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ledger.AnalyticsPeriod
import com.dallet.app.ui.AnalyticsCategorySliceUi
import com.dallet.app.ui.AnalyticsFlowPointUi
import com.dallet.app.ui.AnalyticsMode
import com.dallet.app.ui.AnalyticsUiState
import com.dallet.app.ui.common.SurfaceCard
import com.dallet.app.ui.theme.expenseAmountColor
import com.dallet.app.ui.theme.incomeAmountColor
import java.util.Locale

@Composable
fun AnalyticsScreen(
    state: AnalyticsUiState,
    onPeriodSelected: (AnalyticsPeriod) -> Unit,
    onModeSelected: (AnalyticsMode) -> Unit
) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        }
        if (state.rangeLabel.isNotBlank()) {
            Text(
                text = state.rangeLabel,
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
                value = stringResource(R.string.amount_with_currency, state.incomeTotalLabel),
                valueColor = incomeAmountColor(),
                selected = isIncome,
                onClick = { onModeSelected(AnalyticsMode.INCOME) },
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                label = stringResource(R.string.analytics_expense_label),
                value = stringResource(R.string.amount_with_currency, state.expenseTotalLabel),
                valueColor = expenseAmountColor(),
                selected = !isIncome,
                onClick = { onModeSelected(AnalyticsMode.EXPENSE) },
                modifier = Modifier.weight(1f)
            )
        }

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
                    totalLabel = stringResource(R.string.amount_with_currency, totalLabel)
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
                            Text(
                                text = slice.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(
                                    R.string.amount_with_currency,
                                    slice.amountLabel
                                ),
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
        if (flow.all { it.fraction <= 0f }) {
            SurfaceCard {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            SurfaceCard {
                FlowChart(points = flow)
            }
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    valueColor: Color,
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
    }
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
private fun FlowChart(points: List<AnalyticsFlowPointUi>) {
    val barColor = MaterialTheme.colorScheme.primary
    val scroll = rememberScrollState()
    val barWidth = if (points.size <= 7) 44.dp else 38.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .height(172.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        points.forEach { point ->
            Column(
                modifier = Modifier.width(barWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                val hasAmount = point.fraction > 0f
                val heightFraction = if (hasAmount) point.fraction.coerceIn(0.02f, 1f) else 0.02f
                if (hasAmount) {
                    Text(
                        text = compactAmount(point.amountLabel),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(100.dp * heightFraction)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(
                            if (hasAmount) barColor
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
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
