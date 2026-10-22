package pt.vcc.financeapp.feature.finance.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.financeapp.core.designsystem.components.EmptyState
import pt.vcc.financeapp.core.model.BudgetAlertLevel
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.LoadingState
import pt.vcc.financeapp.feature.finance.ui.formatted
import pt.vcc.financeapp.feature.finance.ui.labelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    onBack: () -> Unit,
    onAddBudget: () -> Unit,
    onBudgetClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Os insets do sistema já são tratados pelo Scaffold da app.
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(R.string.budgets_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddBudget) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.budgets_add))
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> LoadingState()
                state.error != null -> EmptyState(
                    title = stringResource(R.string.budgets_title),
                    message = stringResource(R.string.error_loading),
                )
                state.budgets.isEmpty() -> EmptyState(
                    title = stringResource(R.string.budgets_title),
                    message = stringResource(R.string.budgets_empty),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.budgets, key = { it.progress.budget.id }) { item ->
                        BudgetCard(item, onClick = { onBudgetClick(item.progress.budget.id) })
                    }
                }
            }
        }
    }
}

/** Gasto do período, barra de progresso e estado do alerta de um orçamento. */
@Composable
internal fun BudgetCard(item: BudgetItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val progress = item.progress
    val budget = progress.budget
    val levelColor = progress.alertLevel.color()
    Card(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(budget.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("${progress.percentUsed} %", style = MaterialTheme.typography.titleMedium, color = levelColor)
            }
            Text(
                listOf(item.categoryName ?: stringResource(R.string.budget_all_expenses), stringResource(budget.period.labelRes))
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { (progress.percentUsed / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = levelColor,
            )
            Row {
                Text(
                    stringResource(R.string.budget_spent_of, progress.spent.formatted(), budget.limit.formatted()),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val remaining = progress.remaining
                Text(
                    if (remaining.isNegative) {
                        stringResource(R.string.budget_exceeded_by, (-remaining).formatted())
                    } else {
                        stringResource(R.string.budget_remaining, remaining.formatted())
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = levelColor,
                )
            }
            if (progress.alertLevel == BudgetAlertLevel.WARNING) {
                Text(
                    stringResource(R.string.budget_warning, budget.alertThresholdPercent),
                    style = MaterialTheme.typography.bodySmall,
                    color = levelColor,
                )
            }
        }
    }
}

@Composable
private fun BudgetAlertLevel.color(): Color = when (this) {
    BudgetAlertLevel.OK -> MaterialTheme.colorScheme.primary
    BudgetAlertLevel.WARNING -> MaterialTheme.colorScheme.tertiary
    BudgetAlertLevel.EXCEEDED -> MaterialTheme.colorScheme.error
}
