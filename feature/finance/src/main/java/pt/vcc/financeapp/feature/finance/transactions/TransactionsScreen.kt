package pt.vcc.financeapp.feature.finance.transactions

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.financeapp.core.designsystem.components.EmptyState
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.LoadingState
import pt.vcc.financeapp.feature.finance.ui.MonthSelector
import pt.vcc.financeapp.feature.finance.ui.PeriodTotalsCard

@Composable
fun TransactionsScreen(
    onAddTransaction: () -> Unit,
    onTransactionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MonthSelector(
                month = state.month,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            when {
                state.isLoading -> LoadingState()
                state.error != null -> EmptyState(
                    title = stringResource(R.string.finance_transactions_title),
                    message = stringResource(R.string.error_loading),
                )
                state.items.isEmpty() -> EmptyState(
                    title = stringResource(R.string.finance_transactions_title),
                    message = stringResource(R.string.finance_transactions_empty),
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    item {
                        PeriodTotalsCard(state.totals, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    items(state.items, key = { it.transaction.id }) { item ->
                        TransactionRow(item, onClick = { onTransactionClick(item.transaction.id) })
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onAddTransaction,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.transaction_add))
        }
    }
}