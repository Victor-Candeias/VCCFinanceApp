package pt.vcc.financeapp.core.data.importer

import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import pt.vcc.financeapp.core.model.AmountParser
import pt.vcc.financeapp.core.model.ColumnMapping
import pt.vcc.financeapp.core.model.ImportDateFormat

/**
 * Proposta automática de [ColumnMapping]: procura a linha de cabeçalho nas primeiras linhas por
 * palavras-chave em português e inglês (sem acentos nem pontuação) e deteta o formato das datas
 * pelas primeiras linhas de dados. Sem cabeçalho, infere as colunas pelo conteúdo.
 */
object ColumnMappingDetector {
    private const val HEADER_SEARCH_ROWS = 15
    private const val SAMPLE_ROWS = 30

    private enum class Field { VALUE_DATE, BALANCE, DATE, DEBIT, CREDIT, REFERENCE, DESCRIPTION, AMOUNT }

    // Ordem importa: "data valor" é data-valor (não data nem montante) e "saldo" nunca é montante.
    private val KEYWORDS: List<Pair<Field, List<String>>> = listOf(
        Field.VALUE_DATE to listOf("data valor", "data de valor", "dt valor", "value date"),
        Field.BALANCE to listOf("saldo", "balance"),
        Field.DATE to listOf("data", "date", "dt mov", "dt lancamento", "fecha"),
        Field.DEBIT to listOf("debito", "debitos", "debit", "saida", "saidas", "levantamento", "levantamentos", "despesa"),
        Field.CREDIT to listOf("credito", "creditos", "credit", "entrada", "entradas", "deposito", "depositos", "receita"),
        Field.REFERENCE to listOf("referencia", "reference", "ref", "id", "n documento", "num documento", "numero documento"),
        Field.DESCRIPTION to listOf(
            "descritivo", "descricao", "description", "movimento", "detalhe", "detalhes", "designacao", "narrativa",
            "concept", "details", "memo", "estabelecimento", "comerciante", "merchant",
        ),
        Field.AMOUNT to listOf("montante", "valor", "amount", "importancia", "quantia", "importe"),
    )

    fun detect(table: ImportTable): ColumnMapping {
        val header = findHeaderRow(table)
        val mapping = if (header != null) {
            fromHeader(header.first, header.second)
        } else {
            inferWithoutHeader(table)
        }
        val dateColumn = mapping.dateColumn ?: return mapping
        return mapping.copy(dateFormat = detectDateFormat(sample(table, mapping.headerRowIndex, dateColumn)))
    }

    /** Cabeçalho normalizado usado para reconhecer extratos do mesmo banco; vazio sem cabeçalho. */
    fun headerSignature(table: ImportTable, headerRowIndex: Int): String {
        if (headerRowIndex < 0) return ""
        return table.rows.getOrNull(headerRowIndex).orEmpty().map(::normalize).joinToString("|").trimEnd('|')
    }

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    fun detectDateFormat(values: List<String>): ImportDateFormat {
        val cleaned = values.map(::dateText).filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return ImportDateFormat.DAY_MONTH_YEAR_SLASH
        return ImportDateFormat.entries.maxBy { format -> cleaned.count { parseDate(it, format) != null } }
    }

    fun parseDate(text: String, format: ImportDateFormat): LocalDate? {
        val value = dateText(text)
        if (value.isEmpty()) return null
        return try {
            LocalDate.parse(value, formatters.getValue(format))
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /** Remove a hora (`02/01/2026 10:15`, `2026-01-02T10:15`). */
    private fun dateText(text: String): String = text.trim().substringBefore(' ').substringBefore('T')

    private val formatters: Map<ImportDateFormat, DateTimeFormatter> = ImportDateFormat.entries.associateWith { format ->
        val pattern = format.pattern.replace("yyyy", "uuuu").replace("yy", "uu")
        DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT)
    }

    private fun findHeaderRow(table: ImportTable): Pair<Int, Map<Field, Int>>? {
        var best: Pair<Int, Map<Field, Int>>? = null
        for (rowIndex in 0 until minOf(HEADER_SEARCH_ROWS, table.rows.size)) {
            val fields = matchFields(table.rows[rowIndex])
            val usable = Field.DATE in fields && (Field.AMOUNT in fields || Field.DEBIT in fields || Field.CREDIT in fields)
            if (usable && fields.size > (best?.second?.size ?: 0)) best = rowIndex to fields
        }
        return best
    }

    private fun matchFields(row: List<String>): Map<Field, Int> {
        val result = LinkedHashMap<Field, Int>()
        row.forEachIndexed { column, cell ->
            val words = " ${normalize(cell)} "
            if (words.isBlank() || words.length > 60) return@forEachIndexed
            val field = KEYWORDS.firstOrNull { (_, keywords) -> keywords.any { words.contains(" $it ") } }?.first
            if (field != null && field !in result) result[field] = column
        }
        return result
    }

    private fun fromHeader(rowIndex: Int, fields: Map<Field, Int>): ColumnMapping {
        val amount = fields[Field.AMOUNT]
        val debit = fields[Field.DEBIT]
        val credit = fields[Field.CREDIT]
        val useDebitCredit = debit != null || credit != null
        return ColumnMapping(
            headerRowIndex = rowIndex,
            dateColumn = fields[Field.DATE],
            descriptionColumn = fields[Field.DESCRIPTION],
            amountColumn = if (useDebitCredit) null else amount,
            debitColumn = debit,
            creditColumn = credit,
            valueDateColumn = fields[Field.VALUE_DATE],
            referenceColumn = fields[Field.REFERENCE],
            balanceColumn = fields[Field.BALANCE],
        )
    }

    private fun inferWithoutHeader(table: ImportTable): ColumnMapping {
        val rows = table.rows.take(SAMPLE_ROWS)
        val columns = 0 until table.columnCount
        fun ratio(column: Int, predicate: (String) -> Boolean): Double {
            val values = rows.map { it.getOrNull(column).orEmpty() }.filter { it.isNotBlank() }
            return if (values.isEmpty()) 0.0 else values.count(predicate).toDouble() / values.size
        }
        val dateColumn = columns.filter { column ->
            ratio(column) { value -> ImportDateFormat.entries.any { parseDate(value, it) != null } } >= 0.8
        }.firstOrNull()
        val amountColumn = columns.filter { it != dateColumn }.firstOrNull { column ->
            ratio(column) { AmountParser.parseOrNull(it) != null } >= 0.8
        }
        val descriptionColumn = columns.filter { it != dateColumn && it != amountColumn }
            .maxByOrNull { column -> rows.sumOf { it.getOrNull(column).orEmpty().count(Char::isLetter) } }
        return ColumnMapping(
            headerRowIndex = -1,
            dateColumn = dateColumn,
            descriptionColumn = descriptionColumn,
            amountColumn = amountColumn,
        )
    }

    private fun sample(table: ImportTable, headerRowIndex: Int, column: Int): List<String> =
        table.rows.drop(headerRowIndex + 1).take(SAMPLE_ROWS).map { it.getOrNull(column).orEmpty() }
}
