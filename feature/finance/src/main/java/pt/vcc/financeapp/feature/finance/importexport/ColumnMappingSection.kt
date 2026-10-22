package pt.vcc.financeapp.feature.finance.importexport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.text.NumberFormat
import pt.vcc.financeapp.core.data.importer.ImportTable
import pt.vcc.financeapp.core.data.importer.ParsedImportRow
import pt.vcc.financeapp.core.data.importer.RowError
import pt.vcc.financeapp.core.model.ColumnMapping
import pt.vcc.financeapp.core.model.ImportDateFormat
import pt.vcc.financeapp.core.model.ImportProfile
import pt.vcc.financeapp.core.model.TransactionType
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.DropdownField
import pt.vcc.financeapp.feature.finance.ui.PortugueseLocale
import pt.vcc.financeapp.feature.finance.ui.formatted

private const val RESULT_PREVIEW_ROWS = 5
private const val ERROR_PREVIEW_ROWS = 5

/** Escolha das colunas do extrato para cada campo do movimento. */
@Composable
internal fun ColumnMappingSection(
    table: ImportTable,
    mapping: ColumnMapping,
    profiles: List<ImportProfile>,
    onMappingChange: ((ColumnMapping) -> ColumnMapping) -> Unit,
    onDebitCreditChange: (Boolean) -> Unit,
    onApplyProfile: (ImportProfile) -> Unit,
) {
    val none = stringResource(R.string.import_column_none)
    val header = table.rows.getOrNull(mapping.headerRowIndex)
    val columns: List<Pair<Int?, String>> = listOf<Pair<Int?, String>>(null to none) +
        (0 until table.columnCount).map { column -> column to columnLabel(column, header, table, mapping.headerRowIndex) }
    fun label(column: Int?): String = columns.firstOrNull { it.first == column }?.second ?: none

    Text(stringResource(R.string.import_mapping_title), style = MaterialTheme.typography.titleMedium)
    if (profiles.isNotEmpty()) {
        DropdownField(
            label = stringResource(R.string.import_profile_apply),
            selectedText = stringResource(R.string.import_profile_choose),
            options = profiles.map { it to it.name },
            onSelect = onApplyProfile,
        )
    }
    val headerOptions: List<Pair<Int, String>> = listOf(-1 to stringResource(R.string.import_header_none)) +
        (0 until minOf(ImportViewModel.HEADER_SEARCH_ROWS, table.rows.size)).map { row ->
            row to stringResource(R.string.import_header_row, row + 1, table.rows[row].filter { it.isNotBlank() }.joinToString(" · ").take(40))
        }
    DropdownField(
        label = stringResource(R.string.import_header_label),
        selectedText = headerOptions.firstOrNull { it.first == mapping.headerRowIndex }?.second.orEmpty(),
        options = headerOptions,
        onSelect = { row -> onMappingChange { it.copy(headerRowIndex = row) } },
    )
    ColumnField(R.string.import_field_date, label(mapping.dateColumn), columns, mapping.dateColumn == null) { c ->
        onMappingChange { it.copy(dateColumn = c) }
    }
    DropdownField(
        label = stringResource(R.string.import_field_date_format),
        selectedText = mapping.dateFormat.pattern,
        options = ImportDateFormat.entries.map { it to it.pattern },
        onSelect = { format -> onMappingChange { it.copy(dateFormat = format) } },
    )
    ColumnField(R.string.import_field_description, label(mapping.descriptionColumn), columns, mapping.descriptionColumn == null) { c ->
        onMappingChange { it.copy(descriptionColumn = c) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !mapping.usesDebitCredit,
            onClick = { onDebitCreditChange(false) },
            label = { Text(stringResource(R.string.import_amount_single)) },
        )
        FilterChip(
            selected = mapping.usesDebitCredit,
            onClick = { onDebitCreditChange(true) },
            label = { Text(stringResource(R.string.import_amount_debit_credit)) },
        )
    }
    if (mapping.usesDebitCredit) {
        val missing = mapping.debitColumn == null && mapping.creditColumn == null
        ColumnField(R.string.import_field_debit, label(mapping.debitColumn), columns, missing) { c ->
            onMappingChange { it.copy(debitColumn = c, amountColumn = null) }
        }
        ColumnField(R.string.import_field_credit, label(mapping.creditColumn), columns, missing) { c ->
            onMappingChange { it.copy(creditColumn = c, amountColumn = null) }
        }
    } else {
        ColumnField(R.string.import_field_amount, label(mapping.amountColumn), columns, mapping.amountColumn == null) { c ->
            onMappingChange { it.copy(amountColumn = c) }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = mapping.invertAmounts,
                role = Role.Switch,
                onValueChange = { value -> onMappingChange { it.copy(invertAmounts = value) } },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.import_invert), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.import_invert_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = mapping.invertAmounts, onCheckedChange = null)
    }
    ColumnField(R.string.import_field_value_date, label(mapping.valueDateColumn), columns, false) { c ->
        onMappingChange { it.copy(valueDateColumn = c) }
    }
    ColumnField(R.string.import_field_reference, label(mapping.referenceColumn), columns, false) { c ->
        onMappingChange { it.copy(referenceColumn = c) }
    }
    ColumnField(R.string.import_field_balance, label(mapping.balanceColumn), columns, false) { c ->
        onMappingChange { it.copy(balanceColumn = c) }
    }
}

@Composable
private fun ColumnField(
    labelRes: Int,
    selected: String,
    columns: List<Pair<Int?, String>>,
    isMissing: Boolean,
    onSelect: (Int?) -> Unit,
) {
    DropdownField(
        label = stringResource(labelRes),
        selectedText = selected,
        options = columns,
        onSelect = onSelect,
        supportingText = if (isMissing) stringResource(R.string.import_field_required) else null,
        isError = isMissing,
    )
}

private fun columnLabel(column: Int, header: List<String>?, table: ImportTable, headerRowIndex: Int): String {
    val letter = columnLetter(column)
    val name = header?.getOrNull(column)?.takeIf { it.isNotBlank() }
        ?: table.rows.drop(headerRowIndex + 1).firstNotNullOfOrNull { row -> row.getOrNull(column)?.takeIf { it.isNotBlank() } }
    return if (name == null) letter else "$letter · ${name.take(30)}"
}

private fun columnLetter(column: Int): String {
    var n = column + 1
    val letters = StringBuilder()
    while (n > 0) {
        val remainder = (n - 1) % 26
        letters.insert(0, 'A' + remainder)
        n = (n - 1) / 26
    }
    return letters.toString()
}

/** Resumo do resultado do mapeamento: contagens, primeiros movimentos e primeiras linhas com erro. */
@Composable
internal fun MappingResultCard(rows: List<ParsedImportRow>, validRows: Int, invalidRows: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.import_result_title), style = MaterialTheme.typography.titleMedium)
            Text(pluralStringResource(R.plurals.import_valid_rows, validRows, validRows), style = MaterialTheme.typography.bodyMedium)
            if (invalidRows > 0) {
                Text(
                    pluralStringResource(R.plurals.import_invalid_rows, invalidRows, invalidRows),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            rows.filter { it.isValid }.take(RESULT_PREVIEW_ROWS).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(row.bookingDate?.formatted().orEmpty(), style = MaterialTheme.typography.bodySmall)
                    Text(
                        row.description,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        signedAmount(row),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (row.type == TransactionType.EXPENSE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            rows.filterNot { it.isValid }.take(ERROR_PREVIEW_ROWS).forEach { row ->
                Text(
                    stringResource(R.string.import_row_error, row.rowIndex + 1, stringResource(row.error!!.messageRes())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Guardar o mapeamento como perfil para reconhecer extratos do mesmo banco. */
@Composable
internal fun SaveProfileSection(
    name: String,
    saved: Boolean,
    canSave: Boolean,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.import_profile_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        supportingText = { Text(stringResource(if (saved) R.string.import_profile_saved else R.string.import_profile_hint)) },
    )
    Button(onClick = onSave, enabled = canSave && name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.import_profile_save))
    }
}

private fun signedAmount(row: ParsedImportRow): String {
    val value = BigDecimal.valueOf(if (row.type == TransactionType.EXPENSE) -row.amountMinor else row.amountMinor, 2)
    return NumberFormat.getNumberInstance(PortugueseLocale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(value)
}
