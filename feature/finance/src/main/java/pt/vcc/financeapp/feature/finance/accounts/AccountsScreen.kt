package pt.vcc.financeapp.feature.finance.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.financeapp.core.data.usecase.AccountWithBalance
import pt.vcc.financeapp.core.designsystem.components.EmptyState
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.BalanceTotalsCard
import pt.vcc.financeapp.feature.finance.ui.LoadingState
import pt.vcc.financeapp.feature.finance.ui.formatted
import pt.vcc.financeapp.feature.finance.ui.labelRes

@Composable
fun AccountsScreen(
    onAddAccount: () -> Unit,
    onAccountClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier.fillMaxSize()) {
        when {
            state.isLoading -> LoadingState()
            state.error != null -> EmptyState(
                title = stringResource(R.string.finance_accounts_title),
                message = stringResource(R.string.error_loading),
            )
            state.accounts.isEmpty() && state.inactiveAccounts.isEmpty() -> EmptyState(
                title = stringResource(R.string.finance_accounts_title),
                message = stringResource(R.string.finance_accounts_empty),
            )
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                if (state.totals.isNotEmpty()) {
                    item(key = "totals") {
                        BalanceTotalsCard(state.totals, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
                items(state.accounts, key = { it.account.id }) { item -> AccountRow(item, onAccountClick) }
                if (state.inactiveAccounts.isNotEmpty()) {
                    item(key = "inactive-header") {
                        Text(
                            text = stringResource(R.string.accounts_inactive_section),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                        )
                    }
                    items(state.inactiveAccounts, key = { it.account.id }) { item -> AccountRow(item, onAccountClick) }
                }
            }
        }
        FloatingActionButton(
            onClick = onAddAccount,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.account_add))
        }
    }
}

@Composable
private fun AccountRow(item: AccountWithBalance, onClick: (String) -> Unit) {
    val account = item.account
    val balance = item.balance
    val type = stringResource(account.type.labelRes)
    val color = if (account.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
    ListItem(
        modifier = Modifier.clickable { onClick(account.id) },
        headlineContent = { Text(account.name, color = color) },
        supportingContent = {
            val default = if (account.isDefault) stringResource(R.string.account_default_badge) else null
            Text(listOfNotNull(type, account.institutionName, default).joinToString(" · "))
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = balance.booked.formatted(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (balance.booked.isNegative) MaterialTheme.colorScheme.error else color,
                )
                if (balance.pending.amountMinor != 0L) {
                    Text(
                        text = stringResource(R.string.balance_pending, balance.pending.formatted()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}