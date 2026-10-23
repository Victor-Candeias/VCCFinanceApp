package pt.vcc.financeapp.core.data.exporter

import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Livro XLSX (Office Open XML) escrito diretamente em ZIP, sem bibliotecas: folhas `Movimentos`,
 * `Contas`, `Categorias`, `Compras`, `Artigos` e `Resumo`. Datas como datas do Excel e montantes como números, com
 * texto em `inlineStr` (sem tabela de strings partilhadas).
 */
object XlsxExporter {
    private const val STYLE_DATE = 1
    private const val STYLE_AMOUNT = 2
    private const val STYLE_HEADER = 3

    /** Dias entre 1899-12-30 (origem das datas do Excel) e 1970-01-01. */
    private const val EXCEL_EPOCH_OFFSET = 25_569L

    private sealed interface Cell {
        data class Text(val value: String, val style: Int = 0) : Cell
        data class Number(val value: BigDecimal, val style: Int = 0) : Cell
        data class Date(val value: LocalDate) : Cell
        data object Empty : Cell
    }

    private class Sheet(val name: String, val widths: List<Int>, val rows: List<List<Cell>>)

    fun write(data: ExportData, out: OutputStream) {
        val sheets = listOf(transactions(data), accounts(data), categories(data), purchases(data), purchaseItems(data), summary(data))
        val zip = ZipOutputStream(out)
        fun entry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        entry("[Content_Types].xml", contentTypes(sheets.size))
        entry("_rels/.rels", ROOT_RELS)
        entry("xl/workbook.xml", workbook(sheets))
        entry("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
        entry("xl/styles.xml", STYLES)
        sheets.forEachIndexed { index, sheet -> entry("xl/worksheets/sheet${index + 1}.xml", sheetXml(sheet)) }
        zip.finish()
        zip.flush()
    }

    private fun header(vararg names: String): List<Cell> = names.map { Cell.Text(it, STYLE_HEADER) }

    private fun text(value: String?): Cell = if (value.isNullOrEmpty()) Cell.Empty else Cell.Text(value)

    private fun transactions(data: ExportData) = Sheet(
        name = "Movimentos",
        widths = listOf(12, 12, 22, 14, 40, 30, 14, 8, 12, 30, 12, 50),
        rows = listOf(header(*ExportText.TRANSACTION_HEADERS.toTypedArray())) + data.transactions.map { tx ->
            val purchase = data.purchaseOf(tx.id)
            listOf(
                Cell.Date(data.localDate(tx.bookingDate)),
                tx.valueDate?.let { Cell.Date(data.localDate(it)) } ?: Cell.Empty,
                text(data.accountName(tx.accountId)),
                Cell.Text(ExportText.type(tx.type)),
                text(tx.description),
                text(data.categoryPath(tx.categoryId)),
                Cell.Number(ExportText.decimal(ExportText.signedMinor(tx), tx.amount.currency), STYLE_AMOUNT),
                Cell.Text(tx.amount.currency),
                Cell.Text(ExportText.status(tx.status)),
                text(tx.notes),
                text(ExportText.paymentMethod(purchase?.purchase?.paymentMethod)),
                text(purchase?.let(ExportText::itemsSummary)),
            )
        },
    )

    private fun purchases(data: ExportData) = Sheet(
        name = "Compras",
        widths = listOf(12, 18, 24, 10, 22, 14, 8, 10, 30),
        rows = listOf(header("Data", "Tipo", "Estabelecimento", "Pagamento", "Conta", "Total", "Moeda", "Artigos", "Notas")) +
            data.purchases.map { (purchase, items) ->
                listOf(
                    Cell.Date(data.localDate(purchase.occurredAt)),
                    Cell.Text(ExportText.purchaseType(purchase.type)),
                    text(data.merchantName(purchase.merchantId)),
                    text(ExportText.paymentMethod(purchase.paymentMethod)),
                    text(purchase.accountId?.let(data::accountName)),
                    Cell.Number(ExportText.decimal(purchase.totalMinor, purchase.currency), STYLE_AMOUNT),
                    Cell.Text(purchase.currency),
                    Cell.Number(BigDecimal.valueOf(items.size.toLong())),
                    text(purchase.notes),
                )
            },
    )

    private fun purchaseItems(data: ExportData) = Sheet(
        name = "Artigos",
        widths = listOf(12, 24, 30, 10, 8, 14, 12, 14, 22),
        rows = listOf(header("Data", "Estabelecimento", "Artigo", "Quantidade", "Unidade", "Preço unitário", "Desconto", "Total", "Categoria do produto")) +
            data.purchases.flatMap { (purchase, items) ->
                items.sortedBy { it.position }.map { item ->
                    listOf(
                        Cell.Date(data.localDate(purchase.occurredAt)),
                        text(data.merchantName(purchase.merchantId)),
                        Cell.Text(item.name),
                        Cell.Number(item.quantity.stripTrailingZeros()),
                        text(item.unit),
                        Cell.Number(ExportText.decimal(item.unitPriceMinor, purchase.currency), STYLE_AMOUNT),
                        Cell.Number(ExportText.decimal(item.discountMinor, purchase.currency), STYLE_AMOUNT),
                        Cell.Number(ExportText.decimal(item.totalMinor, purchase.currency), STYLE_AMOUNT),
                        text(item.categoryName),
                    )
                }
            },
    )

    private fun accounts(data: ExportData) = Sheet(
        name = "Contas",
        widths = listOf(24, 20, 24, 8, 16, 8),
        rows = listOf(header("Nome", "Tipo", "Instituição", "Moeda", "Saldo inicial", "Ativa")) +
            data.accounts.map { account ->
                listOf(
                    Cell.Text(account.name),
                    Cell.Text(ExportText.accountType(account.type)),
                    text(account.institutionName),
                    Cell.Text(account.currency),
                    Cell.Number(ExportText.decimal(account.openingBalance), STYLE_AMOUNT),
                    Cell.Text(ExportText.yesNo(account.isActive)),
                )
            },
    )

    private fun categories(data: ExportData) = Sheet(
        name = "Categorias",
        widths = listOf(24, 24, 12, 8, 8),
        rows = listOf(header("Nome", "Categoria principal", "Tipo", "Sistema", "Ativa")) +
            data.categories.map { category ->
                listOf(
                    Cell.Text(category.name),
                    text(data.categoryName(category.parentCategoryId)),
                    Cell.Text(ExportText.type(category.type)),
                    Cell.Text(ExportText.yesNo(category.isSystem)),
                    Cell.Text(ExportText.yesNo(category.isActive)),
                )
            },
    )

    private fun summary(data: ExportData) = Sheet(
        name = "Resumo",
        widths = listOf(22, 16, 16, 16),
        rows = listOf(
            listOf(Cell.Text("Período", STYLE_HEADER), Cell.Text(data.range.label)),
            listOf(Cell.Text("Gerado em", STYLE_HEADER), Cell.Date(data.localDate(data.generatedAt))),
            listOf(Cell.Text("Movimentos", STYLE_HEADER), Cell.Number(BigDecimal.valueOf(data.transactions.size.toLong()))),
            emptyList(),
            header("Moeda", "Receitas", "Despesas", "Saldo"),
        ) + data.totals.map { totals ->
            listOf(
                Cell.Text(totals.currency),
                Cell.Number(ExportText.decimal(totals.income), STYLE_AMOUNT),
                Cell.Number(ExportText.decimal(totals.expenses), STYLE_AMOUNT),
                Cell.Number(ExportText.decimal(totals.net), STYLE_AMOUNT),
            )
        },
    )

    private fun sheetXml(sheet: Sheet): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="$MAIN_NS">""")
        append("<cols>")
        sheet.widths.forEachIndexed { index, width ->
            append("""<col min="${index + 1}" max="${index + 1}" width="$width" customWidth="1"/>""")
        }
        append("</cols><sheetData>")
        sheet.rows.forEachIndexed { rowIndex, row ->
            val r = rowIndex + 1
            append("""<row r="$r">""")
            row.forEachIndexed { columnIndex, cell ->
                val ref = columnName(columnIndex) + r
                when (cell) {
                    is Cell.Text -> append("""<c r="$ref" t="inlineStr"${style(cell.style)}><is><t xml:space="preserve">${xml(cell.value)}</t></is></c>""")
                    is Cell.Number -> append("""<c r="$ref"${style(cell.style)}><v>${cell.value.toPlainString()}</v></c>""")
                    is Cell.Date -> append("""<c r="$ref"${style(STYLE_DATE)}><v>${cell.value.toEpochDay() + EXCEL_EPOCH_OFFSET}</v></c>""")
                    Cell.Empty -> Unit
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun style(index: Int): String = if (index == 0) "" else """ s="$index""""

    internal fun columnName(index: Int): String {
        var n = index + 1
        val name = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            name.insert(0, 'A' + rem)
            n = (n - 1) / 26
        }
        return name.toString()
    }

    /** Escapa XML e remove caracteres de controlo inválidos em XML 1.0. */
    private fun xml(value: String): String = buildString(value.length) {
        value.forEach { c ->
            when {
                c == '&' -> append("&amp;")
                c == '<' -> append("&lt;")
                c == '>' -> append("&gt;")
                c == '"' -> append("&quot;")
                c < ' ' && c != '\t' && c != '\n' && c != '\r' -> Unit
                c == '\uFFFE' || c == '\uFFFF' -> Unit
                else -> append(c)
            }
        }
    }

    private fun contentTypes(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        (1..sheetCount).forEach {
            append("""<Override PartName="/xl/worksheets/sheet$it.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun workbook(sheets: List<Sheet>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="$MAIN_NS" xmlns:r="$REL_NS"><sheets>""")
        sheets.forEachIndexed { index, sheet ->
            append("""<sheet name="${xml(sheet.name)}" sheetId="${index + 1}" r:id="rId${index + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        (1..sheetCount).forEach {
            append("""<Relationship Id="rId$it" Type="$REL_NS/worksheet" Target="worksheets/sheet$it.xml"/>""")
        }
        append("""<Relationship Id="rId${sheetCount + 1}" Type="$REL_NS/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
        "</Relationships>"

    // Estilos: 0 normal, 1 data (formato 14), 2 montante (formato 4, #,##0.00), 3 cabeçalho a negrito.
    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
        """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
        """<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>""" +
        """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="4">""" +
        """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
        """<xf numFmtId="14" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """<xf numFmtId="4" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
        "</cellXfs>" +
        """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
        "</styleSheet>"
}
