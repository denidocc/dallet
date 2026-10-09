package com.dallet.app.ui.shopping

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ui.common.SurfaceCard
import com.dallet.app.ui.theme.expenseAmountColor
import com.dallet.app.ui.theme.incomeAmountColor

@Composable
fun ProductDetailScreen(state: ProductDetailUiState) {
    if (state.loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (state.missing) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.product_missing))
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = state.name + " · " + state.unitLabel,
            style = MaterialTheme.typography.titleLarge
        )
        SurfaceCard {
            Text(
                text = stringResource(R.string.product_now),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = state.lastPriceLabel,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            state.changeLabel?.let { change ->
                Text(
                    text = change + " " + stringResource(R.string.product_vs_previous),
                    style = MaterialTheme.typography.labelMedium,
                    color = changeColor(state.priceWentUp),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        if (state.minLabel.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = stringResource(R.string.product_min),
                    value = state.minLabel,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = stringResource(R.string.product_max),
                    value = state.maxLabel,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Text(
            text = stringResource(R.string.product_chart),
            style = MaterialTheme.typography.titleMedium
        )
        SurfaceCard {
            if (state.chart.size >= 2) {
                PriceChart(points = state.chart)
            } else {
                Text(
                    text = stringResource(R.string.product_need_two),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (state.rows.isNotEmpty()) {
            Text(
                text = stringResource(R.string.product_records),
                style = MaterialTheme.typography.titleMedium
            )
            state.rows.forEach { row ->
                SurfaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = row.dateLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(text = row.priceLabel, style = MaterialTheme.typography.titleMedium)
                        }
                        row.changeLabel?.let { change ->
                            Text(
                                text = change,
                                style = MaterialTheme.typography.labelLarge,
                                color = changeColor(row.priceWentUp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun changeColor(wentUp: Boolean?): Color = when (wentUp) {
    true -> expenseAmountColor()
    false -> incomeAmountColor()
    null -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    SurfaceCard(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun PriceChart(points: List<ChartPointUi>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val pad = 12.dp.toPx()
        val width = size.width - pad * 2
        val height = size.height - pad * 2
        drawLine(gridColor, Offset(pad, pad), Offset(pad + width, pad), strokeWidth = 1.dp.toPx())
        drawLine(
            gridColor,
            Offset(pad, pad + height),
            Offset(pad + width, pad + height),
            strokeWidth = 1.dp.toPx()
        )
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = pad + width * point.x
            val y = pad + height * (1f - point.y)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        points.forEach { point ->
            drawCircle(
                color = lineColor,
                radius = 4.dp.toPx(),
                center = Offset(pad + width * point.x, pad + height * (1f - point.y))
            )
        }
    }
}
