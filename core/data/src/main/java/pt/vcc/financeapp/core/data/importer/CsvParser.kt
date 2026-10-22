package pt.vcc.financeapp.core.data.importer

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import pt.vcc.financeapp.core.data.importer.ImportParseException.Reason

/**
 * CSV dos bancos: deteta a codificação (BOM, UTF-8 válido ou Windows-1252) e o separador (`;`,
 * `,`, tabulação ou `|`), e respeita aspas com `""` e quebras de linha dentro de campos (RFC 4180).
 */
object CsvParser {
    private val DELIMITERS = charArrayOf(';', ',', '\t', '|')
    private const val SAMPLE_LINES = 30

    fun parse(bytes: ByteArray): ImportTable {
        val text = decode(bytes)
        if (text.isBlank()) throw ImportParseException(Reason.EMPTY)
        if (text.any { it == '\u0000' }) throw ImportParseException(Reason.UNSUPPORTED_FORMAT, "Ficheiro binário")
        val delimiter = detectDelimiter(text)
        val rows = parseRows(text, delimiter).filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) throw ImportParseException(Reason.EMPTY)
        return ImportTable(rows)
    }

    fun decode(bytes: ByteArray): String {
        if (bytes.startsWith(0xEF, 0xBB, 0xBF)) return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        if (bytes.startsWith(0xFF, 0xFE)) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.startsWith(0xFE, 0xFF)) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            // Excel em Portugal grava CSV em Windows-1252 ("Débito", "Crédito").
            String(bytes, legacyCharset())
        }
    }

    private fun legacyCharset(): Charset =
        runCatching { Charset.forName("windows-1252") }.getOrDefault(Charsets.ISO_8859_1)

    /** Separador com mais linhas de amostra com o mesmo número (> 1) de campos; empate favorece `;`. */
    internal fun detectDelimiter(text: String): Char {
        val sample = rawRecords(text.take(64 * 1024), SAMPLE_LINES).filter { it.isNotBlank() }
        var best = DELIMITERS.first()
        var bestScore = 0
        for (delimiter in DELIMITERS) {
            val counts = sample.map { line -> parseRows(line, delimiter).firstOrNull()?.size ?: 0 }
            val mode = counts.filter { it > 1 }.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: continue
            val score = mode.value * 1000 + mode.key
            if (score > bestScore) {
                best = delimiter
                bestScore = score
            }
        }
        return best
    }

    /** Registos em texto bruto (uma quebra de linha dentro de aspas não termina o registo). */
    private fun rawRecords(text: String, max: Int): List<String> {
        val records = ArrayList<String>()
        var start = 0
        var inQuotes = false
        var i = 0
        while (i < text.length && records.size < max) {
            when (text[i]) {
                '"' -> inQuotes = !inQuotes
                '\n', '\r' -> if (!inQuotes) {
                    records.add(text.substring(start, i))
                    if (text[i] == '\r' && text.getOrNull(i + 1) == '\n') i++
                    start = i + 1
                }
            }
            i++
        }
        if (start < text.length && records.size < max) records.add(text.substring(start))
        return records
    }
    internal fun parseRows(text: String, delimiter: Char, maxRows: Int = ImportLimits.MAX_ROWS + 1): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var quoted = false
        var i = 0

        fun endField() {
            row.add(if (quoted) field.toString() else field.toString().trim())
            if (row.size > ImportLimits.MAX_COLUMNS) throw ImportParseException(Reason.UNSUPPORTED_FORMAT, "Demasiadas colunas")
            field.setLength(0)
            quoted = false
        }

        fun endRow() {
            endField()
            rows.add(row)
            if (rows.size > ImportLimits.MAX_ROWS) throw ImportParseException(Reason.TOO_MANY_ROWS)
            row = ArrayList()
        }

        while (i < text.length && rows.size < maxRows) {
            val c = text[i]
            when {
                inQuotes && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                inQuotes && c == '"' -> inQuotes = false
                inQuotes -> field.append(c)
                c == '"' && field.isBlank() -> { field.setLength(0); inQuotes = true; quoted = true }
                c == delimiter -> endField()
                c == '\r' -> { if (text.getOrNull(i + 1) == '\n') i++; endRow() }
                c == '\n' -> endRow()
                else -> field.append(c)
            }
            i++
        }
        if (rows.size < maxRows && (field.isNotEmpty() || row.isNotEmpty() || quoted)) endRow()
        return rows
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }
}
