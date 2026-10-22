package pt.vcc.financeapp.feature.finance.importexport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.vcc.financeapp.core.data.importer.CandidateStatus
import pt.vcc.financeapp.core.data.importer.ImportCandidate
import pt.vcc.financeapp.core.data.importer.ImportReconciliation
import pt.vcc.financeapp.core.data.importer.ImportResult
import pt.vcc.financeapp.core.data.importer.ImportReview
import pt.vcc.financeapp.core.data.importer.RowError
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.TransactionType
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.DropdownField
import pt.vcc.financeapp.feature.finance.ui.formatted
import pt.vcc.financeapp.feature.finance.ui.toLocalDate

/** Conta de destino; muda também as casas decimais usadas na leitura dos valores. */
@Composable
internal fun AccountSelector(accounts: List<Account>, selectedId: String?, onSelect: (String) -> Unit) {
    if (accounts.isEmpty()) {
        Text(stringResource(R.string.import_no_accounts), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val selected = accounts.firstOrNull { it.id == selectedId }
    DropdownField(
        label = stringResource(R.string.import_account_label),
        selectedText = selected?.let { "${it.name} (${it.currency})" } ?: stringResource(R.string.import_account_none),
        options = accounts.map { it.id to "${it.name} (${it.currency})" },
        onSelect = onSelect,
        modifier = Modifier.fillMaxWidth(),
        supportingText = if (selected == null) stringResource(R.string.import_account_required) else null,
        isError = selected == null,
    )
}

/** Revisão das linhas classificadas, com escolha das que são gravadas. */
@Composable
internal fun ImportReviewContent(
    review: ImportReview,
    currency: String,
    selectedRows: Set<Int>,
    isWorking: Boolean,
    commitFailed: Boolean,
    onToggle: (Int) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ReviewSummary(review) }
        if (commitFailed) {
            item { Text(stringResource(R.string.import_commit_failed), color = MaterialTheme.colorScheme.error) }
        }
        item {
            Button(onClick = onConfirm, enabled = !isWorking && selectedRows.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(pluralStringResource(R.plurals.import_review_confirm, selectedRows.size, selectedRows.size))
            }
        }
        item {
            OutlinedButton(onClick = onBack, enabled = !isWorking, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.import_review_back))
            }
        }
        items(review.candidates, key = { it.row.rowIndex }) { candidate ->
            CandidateRow(candidate, currency, candidate.row.rowIndex in selectedRows, enabled = !isWorking, onToggle = onToggle)
            HorizontalDivider()
        }
    }
}

@Composable
private fun ReviewSummary(review: ImportReview) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.import_review_title), style = MaterialTheme.typography.titleMedium)
            val new = review.count(CandidateStatus.NEW)
            val duplicates = review.count(CandidateStatus.DUPLICATE)
            val possible = review.count(CandidateStatus.POSSIBLE_DUPLICATE)
            val invalid = review.count(CandidateStatus.INVALID)
            Text(pluralStringResource(R.plurals.import_review_new, new, new))
            if (duplicates > 0) Text(pluralStringResource(R.plurals.import_review_duplicates, duplicates, duplicates))
            if (possible > 0) {
                Text(pluralStringResource(R.plurals.import_review_possible, possible, possible), color = MaterialTheme.colorScheme.tertiary)
                Text(stringResource(R.string.import_review_hint), style = MaterialTheme.typography.bodySmall)
            }
            if (invalid > 0) {
                Text(pluralStringResource(R.plurals.import_invalid_rows, invalid, invalid), color = MaterialTheme.colorScheme.error)
            }
            review.previousImport?.let { previous ->
                Text(
                    stringResource(R.string.import_review_previous, previous.importedAt.toLocalDate().formatted()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun CandidateRow(candidate: ImportCandidate, currency: String, checked: Boolean, enabled: Boolean, onToggle: (Int) -> Unit) {
    val row = candidate.row
    val selectable = candidate.isSelectable && enabled
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = selectable, role = Role.Checkbox, onValueChange = { onToggle(row.rowIndex) }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = selectable)
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(row.bookingDate?.formatted(), row.description.ifEmpty { null }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            statusText(candidate)?.let { (text, isError) ->
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (row.type != null) {
            val money = Money(row.amountMinor, currency).formatted()
            Text(
                if (row.type == TransactionType.EXPENSE) "-$money" else money,
                style = MaterialTheme.typography.bodyMedium,
                color = if (row.type == TransactionType.EXPENSE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun statusText(candidate: ImportCandidate): Pair<String, Boolean>? = when (candidate.status) {
    CandidateStatus.NEW -> null
    CandidateStatus.DUPLICATE -> stringResource(R.string.import_status_duplicate) to false
    CandidateStatus.POSSIBLE_DUPLICATE -> candidate.match?.let {
        stringResource(R.string.import_status_possible, it.description, it.bookingDate.toLocalDate().formatted()) to false
    }
    CandidateStatus.INVALID -> stringResource(
        R.string.import_row_error,
        candidate.row.rowIndex + 1,
        stringResource(candidate.row.error?.messageRes() ?: R.string.import_row_error_amount),
    ) to true
}

/** Resultado da gravação. */
@Composable
internal fun ImportDoneCard(result: ImportResult, onAnother: () -> Unit, onClose: () -> Unit) {
    val batch = result.batch
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.import_done_title), style = MaterialTheme.typography.titleMedium)
            Text(pluralStringResource(R.plurals.import_done_imported, batch.importedRows, batch.importedRows))
            if (batch.duplicateRows > 0) {
                Text(pluralStringResource(R.plurals.import_done_skipped, batch.duplicateRows, batch.duplicateRows))
            }
            if (batch.errorRows > 0) {
                Text(pluralStringResource(R.plurals.import_invalid_rows, batch.errorRows, batch.errorRows))
            }
        }
    }
    result.reconciliation?.let { ReconciliationCard(it) }
    Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_done_close)) }
    OutlinedButton(onClick = onAnother, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_done_another)) }
}

/** Saldo final do extrato contra o saldo calculado pela app no mesmo dia. */
@Composable
private fun ReconciliationCard(reconciliation: ImportReconciliation) {
    val container = if (reconciliation.isReconciled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.import_reconciliation_title, reconciliation.date.formatted()), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.import_reconciliation_statement, reconciliation.statementBalance.formatted()))
            Text(stringResource(R.string.import_reconciliation_app, reconciliation.appBalance.formatted()))
            if (reconciliation.isReconciled) {
                Text(stringResource(R.string.import_reconciliation_ok), fontWeight = FontWeight.Bold)
            } else {
                Text(stringResource(R.string.import_reconciliation_diff, reconciliation.difference.formatted()), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.import_reconciliation_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

internal fun RowError.messageRes(): Int = when (this) {
    RowError.INVALID_DATE -> R.string.import_row_error_date
    RowError.MISSING_DESCRIPTION -> R.string.import_row_error_description
    RowError.INVALID_AMOUNT -> R.string.import_row_error_amount
    RowError.ZERO_AMOUNT -> R.string.import_row_error_zero
}
