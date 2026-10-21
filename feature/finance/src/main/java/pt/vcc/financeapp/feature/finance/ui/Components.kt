package pt.vcc.financeapp.feature.finance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import pt.vcc.financeapp.core.model.PeriodTotals
import pt.vcc.financeapp.feature.finance.R

@Composable
internal fun MonthSelector(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.month_previous))
        }
        Text(month.formatted(), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.month_next))
        }
    }
}

/** Receitas, despesas e saldo do período, um bloco por moeda. */
@Composable
internal fun PeriodTotalsCard(totals: List<PeriodTotals>, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (totals.isEmpty()) {
                Text(stringResource(R.string.totals_empty), style = MaterialTheme.typography.bodyMedium)
            }
            totals.forEach { total ->
                TotalLine(stringResource(R.string.totals_income), total.income.formatted(), MaterialTheme.colorScheme.primary)
                TotalLine(stringResource(R.string.totals_expenses), total.expenses.formatted(), MaterialTheme.colorScheme.error)
                TotalLine(stringResource(R.string.totals_net), total.net.formatted(), MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun TotalLine(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
internal fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}
