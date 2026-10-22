package pt.vcc.financeapp.core.data.importer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import pt.vcc.financeapp.core.data.importer.ImportParseException.Reason

/**
 * Lê a primeira folha de um XLSX (Office Open XML) sem bibliotecas externas: ZIP + SAX. Só são
 * lidas as partes necessárias e com limite de tamanho descomprimido; partes com DOCTYPE são
 * rejeitadas (nunca existem em XLSX legítimos e evitam ataques de entidades XML).
 */
object XlsxParser {
    private const val WORKBOOK = "xl/workbook.xml"
    private const val WORKBOOK_RELS = "xl/_rels/workbook.xml.rels"
    private const val SHARED_STRINGS = "xl/sharedStrings.xml"
    private const val STYLES = "xl/styles.xml"
    private const val WORKSHEETS = "xl/worksheets/"

    fun parse(bytes: ByteArray): ImportTable {
        val parts = readParts(bytes)
        val workbook = parts[WORKBOOK] ?: throw ImportParseException(Reason.INVALID_FILE, "Sem workbook.xml")
        val info = WorkbookHandler().also { parse(workbook, it) }
        val sheetPath = info.firstSheetRelId
            ?.let { id -> parts[WORKBOOK_RELS]?.let { rels -> RelationshipsHandler().also { parse(rels, it) }.targets[id] } }
            ?.let(::resolveTarget)
            ?.takeIf(parts::containsKey)
            ?: parts.keys.filter { it.startsWith(WORKSHEETS) && it.endsWith(".xml") }.minOrNull()
            ?: throw ImportParseException(Reason.INVALID_FILE, "Sem folhas")
        val sharedStrings = parts[SHARED_STRINGS]?.let { SharedStringsHandler().also { h -> parse(it, h) }.strings }.orEmpty()
        val dateStyles = parts[STYLES]?.let { StylesHandler().also { h -> parse(it, h) }.dateStyleIndexes() }.orEmpty()
        val sheet = SheetHandler(sharedStrings, dateStyles, info.date1904)
        parse(parts.getValue(sheetPath), sheet)
        val rows = sheet.rows.filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) throw ImportParseException(Reason.EMPTY)
        return ImportTable(rows)
    }

    private fun resolveTarget(target: String): String =
        if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("./")

    private fun readParts(bytes: ByteArray): Map<String, ByteArray> {
        val parts = HashMap<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    val wanted = name == WORKBOOK || name == WORKBOOK_RELS || name == SHARED_STRINGS || name == STYLES ||
                        (name.startsWith(WORKSHEETS) && name.endsWith(".xml") && !name.contains("/_rels/"))
                    if (!wanted || entry.isDirectory) continue
                    val content = readLimited(zip, ImportLimits.MAX_XLSX_UNCOMPRESSED_BYTES - total)
                    total += content.size
                    parts[name] = content
                }
            }
        } catch (e: ZipException) {
            throw ImportParseException(Reason.INVALID_FILE, e.message, e)
        } catch (e: IOException) {
            throw ImportParseException(Reason.INVALID_FILE, e.message, e)
        }
        // Um XLSX protegido por palavra-passe é um contentor OLE, não um ZIP: não chega aqui.
        if (WORKBOOK !in parts) throw ImportParseException(Reason.INVALID_FILE, "Sem workbook.xml")
        return parts
    }

    private fun readLimited(input: InputStream, remaining: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var read = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            read += n
            if (read > remaining) throw ImportParseException(Reason.TOO_LARGE, "XLSX demasiado grande descomprimido")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun parse(xml: ByteArray, handler: DefaultHandler) {
        val head = String(xml, 0, minOf(xml.size, 4096), Charsets.UTF_8)
        if (head.contains("<!DOCTYPE", ignoreCase = true)) throw ImportParseException(Reason.INVALID_FILE, "DOCTYPE não permitido")
        try {
            val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
            listOf(
                "http://xml.org/sax/features/external-general-entities",
                "http://xml.org/sax/features/external-parameter-entities",
            ).forEach { feature -> runCatching { factory.setFeature(feature, false) } }
            factory.newSAXParser().parse(ByteArrayInputStream(xml), handler)
        } catch (e: ImportParseException) {
            throw e
        } catch (e: SAXException) {
            throw ImportParseException(Reason.INVALID_FILE, e.message, e)
        } catch (e: IOException) {
            throw ImportParseException(Reason.INVALID_FILE, e.message, e)
        }
    }

    /** Índice de coluna (0 = A) a partir de uma referência como `AB12`; -1 se inválida. */
    internal fun columnIndex(reference: String): Int {
        var index = 0
        var letters = 0
        for (c in reference) {
            if (c !in 'A'..'Z') break
            index = index * 26 + (c - 'A' + 1)
            letters++
        }
        return if (letters == 0) -1 else index - 1
    }

    /** Número com vírgula decimal e sem ruído de vírgula flutuante (`12.300000000000001` → `12,3`). */
    internal fun formatNumber(raw: String): String {
        val value = raw.trim().toBigDecimalOrNull() ?: return raw.trim()
        val rounded = value.round(MathContext(15)).stripTrailingZeros()
        val plain = if (rounded.compareTo(BigDecimal.ZERO) == 0) "0" else rounded.toPlainString()
        return plain.replace('.', ',')
    }

    /** Data a partir do número de série do Excel (sistema 1900, ou 1904 nos ficheiros antigos do Mac). */
    internal fun serialToDate(serial: Double, date1904: Boolean): LocalDate? {
        if (serial.isNaN() || serial < 0 || serial > 2_958_465) return null
        val base = if (date1904) LocalDate.of(1904, 1, 1) else LocalDate.of(1899, 12, 30)
        return base.plusDays(kotlin.math.floor(serial).toLong())
    }

    private class WorkbookHandler : DefaultHandler() {
        var firstSheetRelId: String? = null
        var date1904 = false

        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            when (localName) {
                "workbookPr" -> attributes.getValue("date1904")?.let { date1904 = it == "1" || it.equals("true", true) }
                "sheet" -> if (firstSheetRelId == null) {
                    firstSheetRelId = (0 until attributes.length)
                        .firstOrNull { attributes.getLocalName(it) == "id" }
                        ?.let(attributes::getValue)
                }
            }
        }
    }

    private class RelationshipsHandler : DefaultHandler() {
        val targets = HashMap<String, String>()

        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            if (localName == "Relationship") {
                val id = attributes.getValue("Id") ?: return
                val target = attributes.getValue("Target") ?: return
                targets[id] = target
            }
        }
    }

    private class SharedStringsHandler : DefaultHandler() {
        val strings = ArrayList<String>()
        private val current = StringBuilder()
        private var inText = false
        private var phoneticDepth = 0

        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            when (localName) {
                "si" -> current.setLength(0)
                "rPh" -> phoneticDepth++
                "t" -> inText = phoneticDepth == 0
            }
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            when (localName) {
                "si" -> strings.add(current.toString())
                "rPh" -> phoneticDepth--
                "t" -> inText = false
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inText) current.appendRange(ch, start, start + length)
        }
    }

    private class StylesHandler : DefaultHandler() {
        private val customFormats = HashMap<Int, String>()
        private val cellFormats = ArrayList<Int>()
        private var inCellXfs = false

        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            when (localName) {
                "numFmt" -> {
                    val id = attributes.getValue("numFmtId")?.toIntOrNull() ?: return
                    customFormats[id] = attributes.getValue("formatCode").orEmpty()
                }
                "cellXfs" -> inCellXfs = true
                "xf" -> if (inCellXfs) cellFormats.add(attributes.getValue("numFmtId")?.toIntOrNull() ?: 0)
            }
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            if (localName == "cellXfs") inCellXfs = false
        }

        fun dateStyleIndexes(): Set<Int> = cellFormats.indices.filter { isDateFormat(cellFormats[it]) }.toSet()

        private fun isDateFormat(id: Int): Boolean {
            if (id in 14..17 || id == 22 || id in 27..36 || id in 50..58) return true
            val code = customFormats[id] ?: return false
            val cleaned = code.replace(Regex("\"[^\"]*\"|\\[[^]]*]|\\\\."), "").lowercase()
            return cleaned.contains('d') || cleaned.contains('y')
        }
    }

    private class SheetHandler(
        private val sharedStrings: List<String>,
        private val dateStyles: Set<Int>,
        private val date1904: Boolean,
    ) : DefaultHandler() {
        val rows = ArrayList<List<String>>()
        private var row: ArrayList<String>? = null
        private var cellColumn = 0
        private var cellType: String? = null
        private var cellStyle = 0
        private val value = StringBuilder()
        private var capture = false
        private var inInlineString = false

        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            when (localName) {
                "row" -> row = ArrayList()
                "c" -> {
                    val current = row ?: return
                    cellColumn = attributes.getValue("r")?.let(::columnIndex)?.takeIf { it >= 0 } ?: current.size
                    if (cellColumn >= ImportLimits.MAX_COLUMNS) {
                        throw ImportParseException(Reason.UNSUPPORTED_FORMAT, "Demasiadas colunas")
                    }
                    cellType = attributes.getValue("t")
                    cellStyle = attributes.getValue("s")?.toIntOrNull() ?: 0
                    value.setLength(0)
                }
                "is" -> inInlineString = true
                "v" -> capture = true
                "t" -> capture = inInlineString
            }
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            when (localName) {
                "v", "t" -> capture = false
                "is" -> inInlineString = false
                "c" -> row?.let { current ->
                    while (current.size <= cellColumn) current.add("")
                    current[cellColumn] = cellText()
                }
                "row" -> {
                    row?.let(rows::add)
                    row = null
                    if (rows.size > ImportLimits.MAX_ROWS) throw ImportParseException(Reason.TOO_MANY_ROWS)
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (capture) value.appendRange(ch, start, start + length)
        }

        private fun cellText(): String {
            val raw = value.toString()
            return when (cellType) {
                "s" -> raw.trim().toIntOrNull()?.let(sharedStrings::getOrNull).orEmpty().trim()
                "inlineStr", "str" -> raw.trim()
                "e" -> ""
                "b" -> raw.trim()
                "d" -> raw.trim().take(10)
                else -> {
                    if (cellStyle in dateStyles) {
                        raw.trim().toDoubleOrNull()?.let { serialToDate(it, date1904) }?.toString() ?: raw.trim()
                    } else {
                        formatNumber(raw)
                    }
                }
            }
        }
    }
}
