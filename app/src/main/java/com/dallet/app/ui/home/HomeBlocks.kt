package com.dallet.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dallet.app.R
import com.dallet.app.ledger.DebtDirection
import com.dallet.app.ui.RecurringRuleUi
import com.dallet.app.ui.common.AppButtonShape
import com.dallet.app.ui.common.OutlineAction
import com.dallet.app.ui.common.SurfaceCard
import com.dallet.app.ui.debts.DebtsListUi
import com.dallet.app.ui.goals.GoalRowUi
import com.dallet.app.ui.moneyText
import com.dallet.app.ui.shopping.ShoppingListRowUi

private const val HOME_ROWS = 3

/** One row of a home block; [percent] adds a progress bar. */
data class HomeLinkRow(
    val id: Long,
    val title: String,
    val subtitle: String?,
    val trailing: String?,
    val percent: Int? = null
)

/** Rows and click targets of the four home blocks: shopping, goals, debts and regular operations. */
class HomeLinks(
    val shopping: List<HomeLinkRow>,
    val goals: List<HomeLinkRow>,
    val debts: List<HomeLinkRow>,
    val recurring: List<HomeLinkRow>,
    val onShopping: (Long) -> Unit,
    val onShoppingAll: () -> Unit,
    val onGoal: (Long) -> Unit,
    val onGoalsAll: () -> Unit,
    val onDebt: (Long) -> Unit,
    val onDebtsAll: () -> Unit,
    val onRecurring: (Long) -> Unit,
    val onRecurringAll: () -> Unit
)

@Composable
fun shoppingLinkRows(lists: List<ShoppingListRowUi>): List<HomeLinkRow> =
    lists.take(HOME_ROWS).map { HomeLinkRow(it.id, it.title, it.subtitle, it.totalLabel) }

@Composable
fun goalLinkRows(goals: List<GoalRowUi>): List<HomeLinkRow> =
    goals.filter { !it.archived && !it.reached }
        .sortedByDescending { it.percent }
        .take(HOME_ROWS)
        .map {
            HomeLinkRow(
                id = it.id,
                title = it.name,
                subtitle = moneyText(it.savedLabel) + " / " + moneyText(it.targetLabel),
                trailing = "${it.percent}%",
                percent = it.percent
            )
        }

@Composable
fun debtLinkRows(state: DebtsListUi): List<HomeLinkRow> =
    (state.owedToMe + state.iOwe).filter { !it.settled }
        .take(HOME_ROWS)
        .map {
            HomeLinkRow(
                id = it.id,
                title = it.person,
                subtitle = stringResource(
                    if (it.direction == DebtDirection.LENT) R.string.analytics_debts_owed_to_me
                    else R.string.analytics_debts_i_owe
                ),
                trailing = moneyText(it.remainingLabel),
                percent = it.percent
            )
        }

@Composable
fun recurringLinkRows(rules: List<RecurringRuleUi>): List<HomeLinkRow> =
    rules.filter { !it.finished }
        .sortedBy { it.nextDueEpoch }
        .take(HOME_ROWS)
        .map { HomeLinkRow(it.id, it.title, it.subtitle, moneyText(it.amountLabel)) }

/** A card with up to three rows that open the item directly and a button for the whole section. */
@Composable
fun HomeListCard(
    title: String,
    rows: List<HomeLinkRow>,
    emptyText: String,
    allText: String,
    onRow: (Long) -> Unit,
    onAll: () -> Unit
) {
    SurfaceCard {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (rows.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Column(
            modifier = Modifier.padding(top = if (rows.isEmpty()) 8.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rows.forEach { row -> HomeLinkButton(row = row, onClick = { onRow(row.id) }) }
            OutlineAction(text = allText, onClick = onAll)
        }
    }
}

@Composable
private fun HomeLinkButton(row: HomeLinkRow, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppButtonShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, AppButtonShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            row.trailing?.let {
                Text(text = it, style = MaterialTheme.typography.titleSmall)
            }
        }
        row.subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        row.percent?.let { percent ->
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
