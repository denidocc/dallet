package com.dallet.app.ui.home

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ui.budgets.BudgetRowView
import com.dallet.app.ui.DueItemUi
import com.dallet.app.ui.recurring.DueSection
import com.dallet.app.ui.moneyText
import com.dallet.app.ui.AccountRowUi
import com.dallet.app.ui.ExpenseRowUi
import com.dallet.app.ui.HomeUiState
import com.dallet.app.ui.categories.categoryIcon
import com.dallet.app.ui.common.SurfaceCard
import com.dallet.app.ui.theme.expenseAmountColor
import com.dallet.app.ui.theme.incomeAmountColor

@Composable
fun HomeScreen(
    state: HomeUiState,
    onExpenseClick: (Long) -> Unit,
    onBudgetsClick: () -> Unit,
    onShoppingClick: () -> Unit,
    onGoalsClick: () -> Unit,
    onDueConfirm: (DueItemUi, String, (Boolean) -> Unit) -> Unit,
    onDueSkip: (DueItemUi) -> Unit
) {
    if (state.loading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.home_total_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = moneyText(state.totalBalanceLabel),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MonthStat(
                    label = stringResource(R.string.home_month_income),
                    value = moneyText(state.monthIncomeLabel),
                    valueColor = incomeAmountColor(),
                    modifier = Modifier.weight(1f)
                )
                MonthStat(
                    label = stringResource(R.string.home_month_expense),
                    value = moneyText(state.monthExpenseLabel),
                    valueColor = expenseAmountColor(),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        DueSection(items = state.dueItems, onConfirm = onDueConfirm, onSkip = onDueSkip)

        if (state.budgetAlerts.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.budgets_title),
                    style = MaterialTheme.typography.titleMedium
                )
                state.budgetAlerts.forEach { row ->
                    BudgetRowView(row = row, onClick = onBudgetsClick)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.home_accounts_label),
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.accountRows.forEach { row ->
                    AccountCard(row)
                }
            }
        }

        SurfaceCard(onClick = onShoppingClick) {
            Text(
                text = stringResource(R.string.more_shopping),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.home_shopping_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SurfaceCard(onClick = onGoalsClick) {
            Text(
                text = stringResource(R.string.goals_title),
                style = MaterialTheme.typography.titleMedium
            )
            val goal = state.goal
            if (goal == null) {
                Text(
                    text = stringResource(R.string.home_goals_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = goal.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.home_recent_expenses),
                style = MaterialTheme.typography.titleMedium
            )
            SurfaceCard {
                if (state.recentExpenses.isEmpty()) {
                    Text(
                        text = stringResource(R.string.home_recent_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.recentExpenses.forEachIndexed { index, row ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                        ExpenseRow(row = row, onClick = { onExpenseClick(row.id) })
                    }
                }
            }
        }

        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun MonthStat(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun AccountCard(row: AccountRowUi) {
    SurfaceCard(modifier = Modifier.width(168.dp)) {
        Text(
            text = row.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = moneyText(row.balanceLabel),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ExpenseRow(
    row: ExpenseRowUi,
    onClick: () -> Unit
) {
    val accent = Color(row.colorArgb)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = categoryIcon(row.iconKey),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (row.subtitle.isNotBlank()) {
                Text(
                    text = row.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = moneyText(row.amountLabel),
            color = expenseAmountColor(),
            style = MaterialTheme.typography.titleMedium
        )
    }
}
