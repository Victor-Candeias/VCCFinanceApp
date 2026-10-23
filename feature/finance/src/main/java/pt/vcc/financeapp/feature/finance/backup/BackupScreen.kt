package pt.vcc.financeapp.feature.finance.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import pt.vcc.financeapp.core.data.backup.BackupError
import pt.vcc.financeapp.core.data.backup.BackupSummary
import pt.vcc.financeapp.feature.finance.R

private val SummaryDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupUiState.MIME_TYPE)) { uri ->
        if (uri != null) viewModel.create(uri.toString())
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.selectRestoreFile(uri.toString())
    }
    // Os insets do sistema já são tratados pelo Scaffold da app.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(R.string.backup_title)) },
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
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    stringResource(R.string.backup_info),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }

            Text(stringResource(R.string.backup_create_title), style = MaterialTheme.typography.titleMedium)
            PasswordField(
                value = state.password,
                onValueChange = viewModel::updatePassword,
                label = stringResource(R.string.backup_password),
                supportingText = pluralStringResource(
                    R.plurals.backup_password_hint,
                    BackupUiState.MIN_PASSWORD_LENGTH,
                    BackupUiState.MIN_PASSWORD_LENGTH,
                ),
                isError = state.password.isNotEmpty() && state.passwordIssue == BackupPasswordIssue.TOO_SHORT,
            )
            PasswordField(
                value = state.confirmation,
                onValueChange = viewModel::updateConfirmation,
                label = stringResource(R.string.backup_password_confirmation),
                supportingText = if (state.confirmation.isNotEmpty() && state.passwordIssue == BackupPasswordIssue.MISMATCH) {
                    stringResource(R.string.backup_password_mismatch)
                } else {
                    null
                },
                isError = state.confirmation.isNotEmpty() && state.passwordIssue == BackupPasswordIssue.MISMATCH,
            )
            Button(
                onClick = { createLauncher.launch(state.fileName) },
                enabled = state.canCreate,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.backup_create_action)) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(stringResource(R.string.backup_restore_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.backup_restore_warning), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = { openLauncher.launch(arrayOf("*/*")) },
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.restoreUri == null) R.string.backup_restore_choose else R.string.backup_restore_choose_other,
                    ),
                )
            }
            if (state.restoreUri != null) {
                Text(stringResource(R.string.backup_restore_selected), style = MaterialTheme.typography.bodySmall)
                PasswordField(
                    value = state.restorePassword,
                    onValueChange = viewModel::updateRestorePassword,
                    label = stringResource(R.string.backup_password),
                    supportingText = null,
                    isError = false,
                )
                Button(onClick = viewModel::open, enabled = state.canOpen, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.backup_restore_open))
                }
            }

            if (state.isBusy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.backup_in_progress), style = MaterialTheme.typography.bodyMedium)
            }
            state.message?.let { message ->
                val isError = message is BackupMessage.Failed
                Text(
                    text = message.text(),
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }

    state.pending?.let { backup ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.backup_restore_confirm_message,
                        backup.summary.createdAtText(),
                        backup.summary.contentsText(),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmRestore,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.backup_restore_confirm)) }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String?,
    isError: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BackupMessage.text(): String = when (this) {
    is BackupMessage.Created -> stringResource(R.string.backup_created, summary.contentsText())
    is BackupMessage.Restored -> stringResource(R.string.backup_restored, summary.contentsText())
    is BackupMessage.Failed -> stringResource(
        when (error) {
            BackupError.NOT_A_BACKUP -> R.string.backup_error_not_a_backup
            BackupError.WRONG_PASSWORD -> R.string.backup_error_wrong_password
            BackupError.UNSUPPORTED_VERSION, BackupError.INCOMPATIBLE -> R.string.backup_error_incompatible
            BackupError.TOO_LARGE -> R.string.backup_error_too_large
            BackupError.READ_FAILED -> R.string.backup_error_read
            BackupError.WRITE_FAILED -> R.string.backup_error_write
            BackupError.RESTORE_FAILED -> R.string.backup_error_restore
        },
    )
}

private fun BackupSummary.createdAtText(): String = createdAt.atZone(ZoneId.systemDefault()).format(SummaryDateFormatter)

@Composable
private fun BackupSummary.contentsText(): String {
    val accounts = pluralStringResource(R.plurals.backup_accounts, accountCount, accountCount)
    val transactions = pluralStringResource(R.plurals.backup_transactions, transactionCount, transactionCount)
    val categories = pluralStringResource(R.plurals.backup_categories, categoryCount, categoryCount)
    if (purchaseCount == 0) return stringResource(R.string.backup_contents, accounts, transactions, categories)
    val purchases = pluralStringResource(R.plurals.backup_purchases, purchaseCount, purchaseCount)
    return stringResource(R.string.backup_contents_with_purchases, accounts, transactions, categories, purchases)
}
