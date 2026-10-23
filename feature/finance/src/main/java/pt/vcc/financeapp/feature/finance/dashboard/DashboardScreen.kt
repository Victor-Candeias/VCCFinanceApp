package pt.vcc.financeapp.feature.finance.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.financeapp.core.designsystem.components.EmptyState
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.budgets.BudgetCard
import pt.vcc.financeapp.feature.finance.transactions.TransactionRow
import pt.vcc.financeapp.feature.finance.ui.BalanceTotalsCard
import pt.vcc.financeapp.feature.finance.ui.ChartBarGroup
import pt.vcc.financeapp.feature.finance.ui.ChartPalette
import pt.vcc.financeapp.feature.finance.ui.ChartSlice
import pt.vcc.financeapp.feature.finance.ui.DonutChart
import pt.vcc.financeapp.feature.finance.ui.IncomeExpenseBarChart
import pt.vcc.financeapp.feature.finance.ui.IncomeExpenseLegend
import pt.vcc.financeapp.feature.finance.ui.LoadingState
import pt.vcc.financeapp.feature.finance.ui.PeriodTotalsCard
import pt.vcc.financeapp.feature.finance.ui.formatted
import pt.vcc.financeapp.feature.finance.ui.parseHexColor
import pt.vcc.financeapp.feature.finance.ui.percent
import pt.vcc.financeapp.feature.finance.ui.shortLabel

@Composable
fun DashboardScreen(
    onAccountsClick: () -> Unit,
    onBudgetClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onPurchasesClick: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        state.isLoading -> LoadingState(modifier)
        state.error != null -> EmptyState(
            title = stringResource(R.string.finance_dashboard_title),
            message = stringResource(R.string.error_loading),
            modifier = modifier,
        )
        state.activeAccountCount == 0 -> Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.finance_dashboard_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.finance_dashboard_no_accounts),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAccountsClick) { Text(stringResource(R.string.finance_dashboard_view_accounts)) }
        }
        else -> DashboardContent(state, onAccountsClick, onBudgetClick, onPurchasesClick, modifier)
    }
}

@Composable
private fun DashboardCharts(state: DashboardUiState, modifier: Modifier) {
    val currency = state.chartCurrency ?: return
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.dashboard_expenses_by_category), style = MaterialTheme.typography.titleMedium)
            if (state.expensesByCategory.isEmpty()) {
                Text(
                    stringResource(R.string.dashboard_expenses_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val noCategory = stringResource(R.string.dashboard_category_none)
                val others = stringResource(R.string.dashboard_category_others)
                val othersColor = MaterialTheme.colorScheme.outline
                val slices = state.expensesByCategory.mapIndexed { index, item ->
                    ChartSlice(
                        label = when {
                            item.isOthers -> others
                            item.categoryId == null -> noCategory
                            else -> item.name ?: noCategory
                        },
                        value = item.total.amountMinor,
                        formattedValue = item.total.formatted(),
                        color = if (item.isOthers) othersColor else parseHexColor(item.color) ?: ChartPalette[index % ChartPalette.size],
                    )
                }
                val total = slices.sumOf { it.value }
                DonutChart(
                    slices = slices,
                    description = stringResource(
                        R.string.dashboard_expenses_chart_description,
                        slices.joinToString("; ") { "${it.label} ${it.formattedValue} (${percent(it.value, total)})" },
                    ),
                )
            }
            HorizontalDivider()
            Text(
                pluralStringResource(R.plurals.dashboard_monthly_title, state.monthlyTotals.size, state.monthlyTotals.size),
                style = MaterialTheme.typography.titleMedium,
            )
            val income = stringResource(R.string.totals_income)
            val expenses = stringResource(R.string.totals_expenses)
            IncomeExpenseBarChart(
                groups = state.monthlyTotals.map {
                    ChartBarGroup(it.month.shortLabel(), it.totals.income.amountMinor, it.totals.expenses.amountMinor)
                },
                description = stringResource(
                    R.string.dashboard_monthly_chart_description,
                    state.monthlyTotals.joinToString("; ") {
                        "${it.month.formatted()}: $income ${it.totals.income.formatted()}, $expenses ${it.totals.expenses.formatted()}"
                    },
                ),
            )
            IncomeExpenseLegend(income, expenses)
            if (state.chartOmitsCurrencies) {
                Text(
                    stringResource(R.string.dashboard_chart_currency_note, currency),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    onAccountsClick: () -> Unit,
    onBudgetClick: (String) -> Unit,
    onPurchasesClick: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.dashboard_month_summary, state.month.formatted()),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        FilledTonalButton(onClick = onPurchasesClick, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.purchases_title))
        }
        if (state.budgetAlerts.isNotEmpty()) {
            Text(
                text = stringResource(R.string.dashboard_budget_alerts),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            state.budgetAlerts.forEach { item ->
                BudgetCard(item, onClick = { onBudgetClick(item.progress.budget.id) }, Modifier.padding(horizontal = 16.dp))
            }
        }
        PeriodTotalsCard(state.monthTotals, Modifier.padding(horizontal = 16.dp))
        DashboardCharts(state, Modifier.padding(horizontal = 16.dp))
        Text(
            text = stringResource(R.string.dashboard_balances),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        BalanceTotalsCard(state.balanceTotals, Modifier.padding(horizontal = 16.dp))
        Text(
            text = pluralStringResource(R.plurals.dashboard_active_accounts, state.activeAccountCount, state.activeAccountCount),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Button(onClick = onAccountsClick, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.finance_dashboard_view_accounts))
        }
        Text(
            text = stringResource(R.string.dashboard_recent_transactions),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (state.recentTransactions.isEmpty()) {
            Text(
                text = stringResource(R.string.finance_transactions_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        state.recentTransactions.forEach { TransactionRow(it) }
    }
}
