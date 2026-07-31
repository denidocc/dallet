package com.dallet.app.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ledger.AnalyticsPeriod
import com.dallet.app.ui.AnalyticsUiState
import com.dallet.app.ui.common.SurfaceCard
import com.dallet.app.ui.theme.expenseAmountColor
import com.dallet.app.ui.theme.incomeAmountColor

@Composable
fun AnalyticsScreen(
    state: AnalyticsUiState,
    onPeriodSelected: (AnalyticsPeriod) -> Unit
) {
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
            SurfaceCard(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.analytics_income_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.amount_with_currency, state.incomeTotalLabel),
                    style = MaterialTheme.typography.headlineMedium,
                    color = incomeAmountColor(),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            SurfaceCard(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.analytics_expense_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.amount_with_currency, state.expenseTotalLabel),
                    style = MaterialTheme.typography.headlineMedium,
                    color = expenseAmountColor(),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Text(
            text = stringResource(R.string.analytics_by_category),
            style = MaterialTheme.typography.titleMedium
        )
        if (state.categoryBars.isEmpty()) {
            SurfaceCard {
                Text(
                    text = stringResource(R.string.analytics_chart_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            SurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    state.categoryBars.forEach { bar ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = bar.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = stringResource(
                                        R.string.amount_with_currency,
                                        bar.amountLabel
                                    ),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(bar.fraction.coerceIn(0.02f, 1f))
                                        .fillMaxHeight()
                                        .background(Color(bar.colorArgb))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
