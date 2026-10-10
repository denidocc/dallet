package com.dallet.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dallet.app.DalletApplication
import com.dallet.app.MainActivity
import com.dallet.app.R
import com.dallet.app.ledger.AnalyticsPeriod
import com.dallet.app.ledger.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Home-screen widget: total balance, this month's expenses and a "+ expense" button. */
class BalanceWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                render(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ADD_EXPENSE = "com.dallet.app.action.ADD_EXPENSE"
        private const val HIDDEN = "••••"

        /** Refreshes every placed widget; safe to call from anywhere after data changes. */
        fun update(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(ComponentName(appContext, BalanceWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    render(appContext, manager, ids)
                } catch (_: Exception) {
                }
            }
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val app = context.applicationContext as DalletApplication
            val strings = app.strings
            val views = RemoteViews(context.packageName, R.layout.widget_balance)
            views.setTextViewText(R.id.widget_label, strings.get(R.string.home_total_label))
            views.setTextViewText(R.id.widget_add, strings.get(R.string.widget_add_expense))

            if (app.ledger.hasCompletedOnboarding()) {
                val code = app.ledger.mainCurrency().code
                val hidden = app.pinLock.isEnabled
                val balance = if (hidden) {
                    "$HIDDEN $code"
                } else {
                    "${Money.formatManat(app.ledger.totalBalanceMinor())} $code"
                }
                val spent = if (hidden) {
                    "$HIDDEN $code"
                } else {
                    val month = app.ledger.analytics(AnalyticsPeriod.MONTH, LocalDate.now())
                    "${Money.formatManat(month.expenseTotalMinor)} $code"
                }
                views.setTextViewText(R.id.widget_balance, balance)
                views.setTextViewText(
                    R.id.widget_expense,
                    strings.get(R.string.widget_month_expense, spent)
                )
            } else {
                views.setTextViewText(R.id.widget_balance, strings.get(R.string.app_name))
                views.setTextViewText(R.id.widget_expense, "")
            }

            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val openApp = PendingIntent.getActivity(
                context,
                10,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                flags
            )
            val addExpense = PendingIntent.getActivity(
                context,
                11,
                Intent(context, MainActivity::class.java)
                    .setAction(ACTION_ADD_EXPENSE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                flags
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            views.setOnClickPendingIntent(R.id.widget_add, addExpense)
            manager.updateAppWidget(ids, views)
        }
    }
}
