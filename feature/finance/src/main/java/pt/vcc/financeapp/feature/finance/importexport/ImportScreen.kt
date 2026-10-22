package pt.vcc.financeapp.feature.finance.importexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.financeapp.core.data.importer.ImportFile
import pt.vcc.financeapp.core.data.importer.ImportParseException.Reason
import pt.vcc.financeapp.core.model.ImportProfile
import pt.vcc.financeapp.feature.finance.R

/** Importação de extratos (entrada "Importar extratos" do menu Mais). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onFileSelected(uri.toString())
    }
    // Os insets do sistema já são tratados pelo Scaffold da app.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val review = state.review
        if (state.step == ImportStep.REVIEW && review != null) {
            ImportReviewContent(
                review = review,
                currency = accounts.firstOrNull { it.id == state.accountId }?.currency ?: DEFAULT_CURRENCY,
                selectedRows = state.selectedRows,
                isWorking = state.isWorking,
                commitFailed = state.commitFailed,
                onToggle = viewModel::toggleRow,
                onBack = viewModel::backToMapping,
                onConfirm = viewModel::confirmImport,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val result = state.result
            if (state.step == ImportStep.DONE && result != null) {
                ImportDoneCard(result, onAnother = viewModel::clear, onClose = onBack)
                return@Column
            }
            Text(stringResource(R.string.import_intro), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { picker.launch(ImportViewModel.MIME_TYPES) },
                enabled = !state.isReading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (state.file == null) R.string.import_choose_file else R.string.import_choose_other))
            }
            when {
                state.isReading -> CircularProgressIndicator()
                state.error != null -> Text(
                    stringResource(state.error!!.messageRes()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.file != null -> {
                    val file = state.file!!
                    val mapping = state.mapping
                    if (state.profileAutoApplied && state.profile != null) {
                        Text(
                            stringResource(R.string.import_profile_auto_applied, state.profile!!.name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    AccountSelector(accounts, state.accountId, viewModel::selectAccount)
                    if (mapping != null) {
                        ColumnMappingSection(
                            table = file.table,
                            mapping = mapping,
                            profiles = profiles,
                            onMappingChange = viewModel::updateMapping,
                            onDebitCreditChange = viewModel::setDebitCredit,
                            onApplyProfile = viewModel::applyProfile,
                        )
                        if (mapping.isComplete) {
                            MappingResultCard(state.parsedRows, state.validRows, state.invalidRows)
                            Button(onClick = viewModel::review, enabled = state.canReview, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.import_review_action))
                            }
                            if (state.isWorking) CircularProgressIndicator()
                        }
                        SaveProfileSection(
                            name = state.profileName,
                            saved = state.profileSaved,
                            canSave = mapping.isComplete,
                            onNameChange = viewModel::onProfileNameChange,
                            onSave = viewModel::saveProfile,
                        )
                    }
                    ImportPreview(file)
                }
                else -> SavedProfiles(profiles, onDelete = viewModel::deleteProfile)
            }
        }
    }
}

@Composable
private fun ImportPreview(file: ImportFile) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(file.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(
                    R.string.import_file_summary,
                    file.format.name,
                    pluralStringResource(R.plurals.import_rows, file.table.rows.size, file.table.rows.size),
                    pluralStringResource(R.plurals.import_columns, file.table.columnCount, file.table.columnCount),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    val previewRows = file.table.rows.take(ImportViewModel.PREVIEW_ROWS)
    Text(
        pluralStringResource(R.plurals.import_preview_title, file.table.rows.size, previewRows.size, file.table.rows.size),
        style = MaterialTheme.typography.titleSmall,
    )
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        previewRows.forEachIndexed { index, row ->
            Row {
                for (column in 0 until file.table.columnCount) {
                    Text(
                        text = row.getOrNull(column).orEmpty().replace('\n', ' '),
                        modifier = Modifier
                            .width(140.dp)
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (index == 0) FontWeight.Bold else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(Modifier.width(140.dp * file.table.columnCount))
        }
    }
}

@Composable
private fun SavedProfiles(profiles: List<ImportProfile>, onDelete: (String) -> Unit) {
    if (profiles.isEmpty()) return
    Text(stringResource(R.string.import_profiles_title), style = MaterialTheme.typography.titleMedium)
    profiles.forEach { profile ->
        ListItem(
            headlineContent = { Text(profile.name) },
            supportingContent = {
                Text(
                    profile.headerSignature.ifEmpty { stringResource(R.string.import_header_none) }.replace("|", " · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                IconButton(onClick = { onDelete(profile.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.import_profile_delete, profile.name))
                }
            },
        )
    }
}

private const val DEFAULT_CURRENCY = "EUR"

private fun Reason.messageRes(): Int = when (this) {
    Reason.EMPTY -> R.string.import_error_empty
    Reason.TOO_LARGE -> R.string.import_error_too_large
    Reason.TOO_MANY_ROWS -> R.string.import_error_too_many_rows
    Reason.UNSUPPORTED_FORMAT -> R.string.import_error_unsupported
    Reason.INVALID_FILE -> R.string.import_error_invalid
    Reason.READ_FAILED -> R.string.import_error_read
}
