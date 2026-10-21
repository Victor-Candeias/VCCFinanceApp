package pt.vcc.financeapp.feature.finance.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import pt.vcc.financeapp.feature.finance.transactions.TransactionRow
import pt.vcc.financeapp.feature.finance.ui.BalanceTotalsCard
import pt.vcc.financeapp.feature.finance.ui.LoadingState
import pt.vcc.financeapp.feature.finance.ui.PeriodTotalsCard
import pt.vcc.financeapp.feature.finance.ui.formatted

@Composable
fun DashboardScreen(
    onAccountsClick: () -> Unit,
    modifier: Modifier = Modifier,
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
        else -> DashboardContent(state, onAccountsClick, modifier)
    }
}

@Composable
private fun DashboardContent(state: DashboardUiState, onAccountsClick: () -> Unit, modifier: Modifier) {
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
        PeriodTotalsCard(state.monthTotals, Modifier.padding(horizontal = 16.dp))
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
