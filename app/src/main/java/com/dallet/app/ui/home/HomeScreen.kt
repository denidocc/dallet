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
import com.dallet.app.ledger.Money
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
    links: HomeLinks,
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

        HomeListCard(
            title = stringResource(R.string.more_shopping),
            rows = links.shopping,
            emptyText = stringResource(R.string.home_shopping_hint),
            allText = stringResource(R.string.home_all_shopping),
            onRow = links.onShopping,
            onAll = links.onShoppingAll
        )
        HomeListCard(
            title = stringResource(R.string.goals_title),
            rows = links.goals,
            emptyText = stringResource(R.string.home_goals_hint),
            allText = stringResource(R.string.home_all_goals),
            onRow = links.onGoal,
            onAll = links.onGoalsAll
        )
        HomeListCard(
            title = stringResource(R.string.debts_title),
            rows = links.debts,
            emptyText = stringResource(R.string.home_debts_empty),
            allText = stringResource(R.string.home_all_debts),
            onRow = links.onDebt,
            onAll = links.onDebtsAll
        )
        HomeListCard(
            title = stringResource(R.string.recurring_title),
            rows = links.recurring,
            emptyText = stringResource(R.string.recurring_empty),
            allText = stringResource(R.string.home_all_recurring),
            onRow = links.onRecurring,
            onAll = links.onRecurringAll
        )

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
