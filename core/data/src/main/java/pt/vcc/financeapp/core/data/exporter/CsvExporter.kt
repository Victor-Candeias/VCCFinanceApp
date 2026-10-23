package pt.vcc.financeapp.core.data.exporter

import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * CSV em UTF-8 com BOM, separador `;` e vírgula decimal (abre diretamente no Excel em português).
 * Os campos de texto que começam por `=`, `+`, `-`, `@` ou tabulação são prefixados com `'` para
 * não serem interpretados como fórmulas.
 */
object CsvExporter {
    private const val SEPARATOR = ';'
    private const val LINE_END = "\r\n"

    fun write(data: ExportData, out: OutputStream) {
        val writer = OutputStreamWriter(out, Charsets.UTF_8)
        writer.write("\uFEFF")
        writer.write(ExportText.TRANSACTION_HEADERS.joinToString(SEPARATOR.toString(), postfix = LINE_END) { escape(it) })
        data.transactions.forEach { tx ->
            val purchase = data.purchaseOf(tx.id)
            val fields = listOf(
                data.localDate(tx.bookingDate).toString(),
                tx.valueDate?.let { data.localDate(it).toString() }.orEmpty(),
                text(data.accountName(tx.accountId)),
                ExportText.type(tx.type),
                text(tx.description),
                text(data.categoryPath(tx.categoryId)),
                ExportText.decimal(ExportText.signedMinor(tx), tx.amount.currency).toPlainString().replace('.', ','),
                tx.amount.currency,
                ExportText.status(tx.status),
                text(tx.notes.orEmpty()),
                ExportText.paymentMethod(purchase?.purchase?.paymentMethod),
                text(purchase?.let(ExportText::itemsSummary).orEmpty()),
            )
            writer.write(fields.joinToString(SEPARATOR.toString(), postfix = LINE_END) { escape(it) })
        }
        writer.flush()
    }

    private fun text(value: String): String =
        if (value.isNotEmpty() && value[0] in FORMULA_PREFIXES) "'$value" else value

    private fun escape(value: String): String =
        if (value.any { it == SEPARATOR || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private val FORMULA_PREFIXES = setOf('=', '+', '-', '@', '\t', '\r')
}
