package pt.vcc.financeapp.feature.finance.importexport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.vcc.financeapp.core.model.ImportStatus
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.formatted
import pt.vcc.financeapp.feature.finance.ui.toLocalDate

/** Importações anteriores; as concluídas podem ser revertidas após confirmação. */
@Composable
internal fun ImportHistorySection(
    history: List<ImportHistoryItem>,
    revertingId: String?,
    revertFailed: Boolean,
    onRevert: (String) -> Unit,
) {
    if (history.isEmpty()) return
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    Text(stringResource(R.string.import_history_title), style = MaterialTheme.typography.titleMedium)
    if (revertFailed) {
        Text(stringResource(R.string.import_history_revert_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
    history.forEach { item ->
        val batch = item.batch
        ListItem(
            headlineContent = { Text(batch.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(
                            R.string.import_history_summary,
                            batch.importedAt.toLocalDate().formatted(),
                            item.accountName.ifEmpty { stringResource(R.string.import_history_unknown_account) },
                        ),
                    )
                    Text(
                        pluralStringResource(R.plurals.import_done_imported, batch.importedRows, batch.importedRows) +
                            if (batch.duplicateRows > 0) {
                                " · " + pluralStringResource(R.plurals.import_done_skipped, batch.duplicateRows, batch.duplicateRows)
                            } else {
                                ""
                            },
                    )
                }
            },
            trailingContent = {
                when (batch.status) {
                    ImportStatus.COMPLETED -> TextButton(onClick = { pending = batch.id }, enabled = revertingId == null) {
                        Text(stringResource(R.string.import_history_revert))
                    }
                    ImportStatus.REVERTED -> Text(
                        stringResource(R.string.import_history_reverted),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    else -> Unit
                }
            },
        )
    }
    val pendingItem = pending?.let { id -> history.firstOrNull { it.batch.id == id } }
    if (pendingItem != null) {
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.import_history_revert_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.import_history_revert_message,
                        pendingItem.batch.importedRows,
                        pendingItem.batch.importedRows,
                        pendingItem.batch.fileName,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    onRevert(pendingItem.batch.id)
                }) { Text(stringResource(R.string.import_history_revert)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
