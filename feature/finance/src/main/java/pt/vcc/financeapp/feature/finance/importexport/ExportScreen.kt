package pt.vcc.financeapp.feature.finance.importexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.financeapp.core.data.exporter.ExportFormat
import pt.vcc.financeapp.core.data.exporter.ExportResult
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.PeriodSelector
import pt.vcc.financeapp.feature.finance.ui.formatted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(onBack: () -> Unit, viewModel: ExportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val label = when (state.scope) {
        ExportScope.MONTH -> state.period.month.formatted()
        ExportScope.YEAR -> state.period.year.toString()
        ExportScope.ALL -> stringResource(R.string.export_scope_all_label)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(state.format.mimeType)) { uri ->
        if (uri != null) viewModel.export(uri.toString(), label)
    }
    // Os insets do sistema já são tratados pelo Scaffold da app.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(R.string.export_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.export_format), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                ExportFormat.entries.forEach { format ->
                    FormatOption(format, selected = format == state.format, onSelect = { viewModel.selectFormat(format) })
                }
            }
            Text(stringResource(R.string.export_period), style = MaterialTheme.typography.titleMedium)
            ScopeSelector(state.scope, viewModel::selectScope)
            if (state.scope != ExportScope.ALL) {
                val isYear = state.scope == ExportScope.YEAR
                PeriodSelector(
                    label = label,
                    previousDescription = stringResource(if (isYear) R.string.reports_year_previous else R.string.month_previous),
                    nextDescription = stringResource(if (isYear) R.string.reports_year_next else R.string.month_next),
                    onPrevious = viewModel::previousPeriod,
                    onNext = viewModel::nextPeriod,
                )
            }
            state.transactionCount?.let { count ->
                Text(
                    pluralStringResource(R.plurals.export_transaction_count, count, count),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    stringResource(R.string.export_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Button(
                onClick = { launcher.launch(state.fileName) },
                enabled = !state.isExporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (state.isExporting) R.string.export_in_progress else R.string.export_action))
            }
            when (val result = state.result) {
                is ExportResult.Exported -> Text(
                    pluralStringResource(R.plurals.export_success, result.transactionCount, result.transactionCount),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                ExportResult.Failed -> Text(
                    stringResource(R.string.export_failed),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                null -> Unit
            }
        }
    }
}

@Composable
private fun FormatOption(format: ExportFormat, selected: Boolean, onSelect: () -> Unit) {
    val (title, description) = when (format) {
        ExportFormat.CSV -> R.string.export_format_csv to R.string.export_format_csv_description
        ExportFormat.XLSX -> R.string.export_format_xlsx to R.string.export_format_xlsx_description
        ExportFormat.PDF -> R.string.export_format_pdf to R.string.export_format_pdf_description
        ExportFormat.JSON -> R.string.export_format_json to R.string.export_format_json_description
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeSelector(selected: ExportScope, onSelect: (ExportScope) -> Unit) {
    val options = listOf(
        ExportScope.MONTH to R.string.reports_period_month,
        ExportScope.YEAR to R.string.reports_period_year,
        ExportScope.ALL to R.string.export_scope_all,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (scope, label) ->
            SegmentedButton(
                selected = scope == selected,
                onClick = { onSelect(scope) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(stringResource(label)) }
        }
    }
}
