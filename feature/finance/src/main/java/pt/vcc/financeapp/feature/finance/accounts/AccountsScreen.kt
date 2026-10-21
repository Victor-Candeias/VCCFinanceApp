package pt.vcc.financeapp.feature.finance.accounts

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.financeapp.core.designsystem.components.EmptyState
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.LoadingState
import pt.vcc.financeapp.feature.finance.ui.labelRes

@Composable
fun AccountsScreen(
    modifier: Modifier = Modifier,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        state.isLoading -> LoadingState(modifier)
        state.error != null -> EmptyState(
            title = stringResource(R.string.finance_accounts_title),
            message = stringResource(R.string.error_loading),
            modifier = modifier,
        )
        state.accounts.isEmpty() -> EmptyState(
            title = stringResource(R.string.finance_accounts_title),
            message = stringResource(R.string.finance_accounts_empty),
            modifier = modifier,
        )
        else -> LazyColumn(modifier) {
            items(state.accounts, key = Account::id) { account -> AccountRow(account) }
        }
    }
}

@Composable
private fun AccountRow(account: Account) {
    val type = stringResource(account.type.labelRes)
    ListItem(
        headlineContent = { Text(account.name) },
        supportingContent = {
            Text(listOfNotNull(type, account.institutionName).joinToString(" · "))
        },
        trailingContent = { Text(account.currency) },
    )
}
