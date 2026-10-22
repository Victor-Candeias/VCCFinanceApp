package pt.vcc.financeapp.feature.finance.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.ReportPeriodType
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.ChartBarGroup
import pt.vcc.financeapp.feature.finance.ui.ChartPalette
import pt.vcc.financeapp.feature.finance.ui.ChartSlice
import pt.vcc.financeapp.feature.finance.ui.DonutChart
import pt.vcc.financeapp.feature.finance.ui.IncomeExpenseBarChart
import pt.vcc.financeapp.feature.finance.ui.IncomeExpenseLegend
import pt.vcc.financeapp.feature.finance.ui.LoadingState
import pt.vcc.financeapp.feature.finance.ui.PeriodSelector
import pt.vcc.financeapp.feature.finance.ui.PeriodTotalsCard
import pt.vcc.financeapp.feature.finance.ui.formatted
import pt.vcc.financeapp.feature.finance.ui.parseHexColor
import pt.vcc.financeapp.feature.finance.ui.percent
import pt.vcc.financeapp.feature.finance.ui.shortLabel

@Composable
fun ReportsScreen(modifier: Modifier = Modifier, viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PeriodTypeSelector(state.period.type, viewModel::selectType)
        val isYear = state.period.type == ReportPeriodType.YEAR
        PeriodSelector(
            label = if (isYear) state.period.year.toString() else state.period.month.formatted(),
            previousDescription = stringResource(if (isYear) R.string.reports_year_previous else R.string.month_previous),
            nextDescription = stringResource(if (isYear) R.string.reports_year_next else R.string.month_next),
            onPrevious = viewModel::previousPeriod,
            onNext = viewModel::nextPeriod,
        )
        val content = state.content
        when {
            state.isLoading -> LoadingState()
            state.error != null -> Text(stringResource(R.string.reports_error), color = MaterialTheme.colorScheme.error)
            content == null -> Text(
                stringResource(R.string.reports_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> ReportBody(content, state.currencies, viewModel::selectCurrency)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodTypeSelector(selected: ReportPeriodType, onSelect: (ReportPeriodType) -> Unit) {
    val options = listOf(ReportPeriodType.MONTH to R.string.reports_period_month, ReportPeriodType.YEAR to R.string.reports_period_year)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (type, label) ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(stringResource(label)) }
        }
    }
}

@Composable
private fun ReportBody(content: ReportContent, currencies: List<String>, onSelectCurrency: (String) -> Unit) {
    if (currencies.size > 1) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reports_currency), style = MaterialTheme.typography.bodyMedium)
            currencies.forEach { currency ->
                FilterChip(
                    selected = currency == content.currency,
                    onClick = { onSelectCurrency(currency) },
                    label = { Text(currency) },
                )
            }
        }
    }
    PeriodTotalsCard(listOf(content.totals))
    Text(
        pluralStringResource(R.plurals.reports_transaction_count, content.transactionCount, content.transactionCount),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    CategorySection(
        title = stringResource(R.string.reports_expenses_by_category),
        emptyText = stringResource(R.string.reports_expenses_empty),
        lines = content.expensesByCategory,
        total = content.totals.expenses,
        showChart = true,
    )
    CategorySection(
        title = stringResource(R.string.reports_income_by_category),
        emptyText = stringResource(R.string.reports_income_empty),
        lines = content.incomeByCategory,
        total = content.totals.income,
        showChart = false,
    )
    if (content.monthly.isNotEmpty()) MonthlySection(content)
    AccountSection(content.byAccount)
}

@Composable
private fun CategorySection(title: String, emptyText: String, lines: List<ReportCategoryLine>, total: Money, showChart: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (lines.isEmpty()) {
                Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            val noCategory = stringResource(R.string.dashboard_category_none)
            val colors = lines.mapIndexed { index, line -> parseHexColor(line.color) ?: ChartPalette[index % ChartPalette.size] }
            if (showChart) {
                val others = stringResource(R.string.dashboard_category_others)
                val slices = lines.take(MAX_SLICES).mapIndexed { index, line ->
                    ChartSlice(line.name ?: noCategory, line.total.amountMinor, line.total.formatted(), colors[index])
                } + lines.drop(MAX_SLICES).takeIf { it.isNotEmpty() }?.let { rest ->
                    val sum = rest.fold(Money.zero(total.currency)) { acc, line -> acc + line.total }
                    ChartSlice(others, sum.amountMinor, sum.formatted(), MaterialTheme.colorScheme.outline)
                }.let(::listOfNotNull)
                DonutChart(
                    slices = slices,
                    description = stringResource(
                        R.string.dashboard_expenses_chart_description,
                        slices.joinToString("; ") { "${it.label} ${it.formattedValue} (${percent(it.value, total.amountMinor)})" },
                    ),
                )
                HorizontalDivider()
            }
            lines.forEachIndexed { index, line ->
                CategoryLine(line.name ?: noCategory, line.total, percent(line.total.amountMinor, total.amountMinor), colors[index])
            }
        }
    }
}

@Composable
private fun CategoryLine(name: String, total: Money, share: String, color: Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(total.formatted(), style = MaterialTheme.typography.bodyMedium)
        Text(
            share,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MonthlySection(content: ReportContent) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.reports_monthly_title), style = MaterialTheme.typography.titleMedium)
            val income = stringResource(R.string.totals_income)
            val expenses = stringResource(R.string.totals_expenses)
            IncomeExpenseBarChart(
                groups = content.monthly.map {
                    ChartBarGroup(it.month.shortLabel(), it.totals.income.amountMinor, it.totals.expenses.amountMinor)
                },
                description = stringResource(
                    R.string.dashboard_monthly_chart_description,
                    content.monthly.joinToString("; ") {
                        "${it.month.formatted()}: $income ${it.totals.income.formatted()}, $expenses ${it.totals.expenses.formatted()}"
                    },
                ),
            )
            IncomeExpenseLegend(income, expenses)
        }
    }
}

@Composable
private fun AccountSection(accounts: List<ReportAccountLine>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.reports_by_account), style = MaterialTheme.typography.titleMedium)
            accounts.forEach { account ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(account.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "+" + account.totals.income.formatted(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "−" + account.totals.expenses.formatted(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private const val MAX_SLICES = 5
