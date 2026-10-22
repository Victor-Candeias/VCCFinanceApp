package pt.vcc.financeapp.core.data.importer

import java.time.LocalDate
import pt.vcc.financeapp.core.model.AmountParser
import pt.vcc.financeapp.core.model.ColumnMapping
import pt.vcc.financeapp.core.model.TransactionType

/** Linha do extrato interpretada; [error] não nulo indica que não pode ser importada. */
data class ParsedImportRow(
    /** Índice na [ImportTable] (0 = primeira linha do ficheiro). */
    val rowIndex: Int,
    val bookingDate: LocalDate?,
    val valueDate: LocalDate?,
    val description: String,
    val type: TransactionType?,
    /** Valor absoluto em unidades mínimas. */
    val amountMinor: Long,
    val reference: String?,
    val balanceMinor: Long?,
    val error: RowError? = null,
) {
    val isValid: Boolean get() = error == null
}

enum class RowError { INVALID_DATE, MISSING_DESCRIPTION, INVALID_AMOUNT, ZERO_AMOUNT }

/** Converte as linhas de dados (abaixo do cabeçalho) em movimentos, segundo um [ColumnMapping] completo. */
object ImportRowParser {
    private val CURRENCY_CODE = Regex("(?i)\\b(eur|usd|gbp|chf|brl)\\b")
    private val WHITESPACE = Regex("\\s+")

    fun parse(table: ImportTable, mapping: ColumnMapping, fractionDigits: Int = 2): List<ParsedImportRow> {
        require(mapping.isComplete) { "Mapeamento incompleto" }
        val mappedColumns = listOfNotNull(
            mapping.dateColumn, mapping.descriptionColumn, mapping.amountColumn, mapping.debitColumn, mapping.creditColumn,
        )
        return table.rows.withIndex()
            .drop(mapping.headerRowIndex + 1)
            .filter { (_, row) -> mappedColumns.any { row.getOrNull(it).orEmpty().isNotBlank() } }
            .map { (index, row) -> parseRow(index, row, mapping, fractionDigits) }
    }

    private fun parseRow(index: Int, row: List<String>, mapping: ColumnMapping, fractionDigits: Int): ParsedImportRow {
        fun cell(column: Int?): String = column?.let { row.getOrNull(it) }.orEmpty().trim()

        val bookingDate = ColumnMappingDetector.parseDate(cell(mapping.dateColumn), mapping.dateFormat)
        val valueDate = mapping.valueDateColumn?.let { ColumnMappingDetector.parseDate(cell(it), mapping.dateFormat) }
        val description = cell(mapping.descriptionColumn).replace(WHITESPACE, " ")
        val signed = signedAmount(mapping, ::cell, fractionDigits)
        val balance = mapping.balanceColumn?.let { amount(cell(it), fractionDigits) }
        val reference = mapping.referenceColumn?.let(::cell)?.takeIf { it.isNotEmpty() }
        val error = when {
            bookingDate == null -> RowError.INVALID_DATE
            signed == null -> RowError.INVALID_AMOUNT
            signed == 0L -> RowError.ZERO_AMOUNT
            description.isEmpty() -> RowError.MISSING_DESCRIPTION
            else -> null
        }
        val type = signed?.takeIf { it != 0L }?.let { if (it < 0) TransactionType.EXPENSE else TransactionType.INCOME }
        return ParsedImportRow(
            rowIndex = index,
            bookingDate = bookingDate,
            valueDate = valueDate,
            description = description,
            type = type,
            amountMinor = signed?.let(Math::abs) ?: 0L,
            reference = reference,
            balanceMinor = balance,
            error = error,
        )
    }

    /** Valor com sinal (negativo = despesa) ou `null` se inválido. */
    private fun signedAmount(mapping: ColumnMapping, cell: (Int?) -> String, fractionDigits: Int): Long? {
        val value = if (mapping.amountColumn != null) {
            amount(cell(mapping.amountColumn), fractionDigits)
        } else {
            val debitText = cell(mapping.debitColumn)
            val creditText = cell(mapping.creditColumn)
            val debit = if (debitText.isEmpty()) 0L else amount(debitText, fractionDigits)?.let(Math::abs) ?: return null
            val credit = if (creditText.isEmpty()) 0L else amount(creditText, fractionDigits)?.let(Math::abs) ?: return null
            if (debit != 0L && credit != 0L) return null
            credit - debit
        }
        return if (mapping.invertAmounts) value?.let { -it } else value
    }

    private fun amount(text: String, fractionDigits: Int): Long? {
        val cleaned = text.replace(CURRENCY_CODE, "").trim()
        if (cleaned.isEmpty()) return null
        return AmountParser.parseOrNull(cleaned, fractionDigits)
    }
}
