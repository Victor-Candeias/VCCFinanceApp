package pt.vcc.financeapp.core.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CsvParserTest {
    @Test
    fun semicolonWithDecimalCommaAndWindows1252() {
        val text = "Data Mov.;Descritivo;Débito;Crédito\r\n02/01/2026;Café;1.234,50;\r\n03/01/2026;Salário;;2.000,00\r\n"
        val table = CsvParser.parse(text.toByteArray(charset("windows-1252")))

        assertEquals(listOf("Data Mov.", "Descritivo", "Débito", "Crédito"), table.rows[0])
        assertEquals(listOf("02/01/2026", "Café", "1.234,50", ""), table.rows[1])
        assertEquals("2.000,00", table.cell(2, 3))
        assertEquals(4, table.columnCount)
    }

    @Test
    fun utf8BomAndCommaDelimiterWithQuotes() {
        val text = "\uFEFFdate,description,amount\n2026-01-02,\"Loja, Lda\",\"-12,30\"\n2026-01-03,\"Diz \"\"olá\"\"\",5\n"
        val table = CsvParser.parse(text.toByteArray(Charsets.UTF_8))

        assertEquals(listOf("date", "description", "amount"), table.rows[0])
        assertEquals(listOf("2026-01-02", "Loja, Lda", "-12,30"), table.rows[1])
        assertEquals("Diz \"olá\"", table.cell(2, 1))
    }

    @Test
    fun quotedFieldWithNewLineAndBlankLines() {
        val text = "a;b\n\n\"linha 1\nlinha 2\";x\n;\n"
        val table = CsvParser.parse(text.toByteArray())

        assertEquals(2, table.rows.size)
        assertEquals("linha 1\nlinha 2", table.cell(1, 0))
    }

    @Test
    fun detectsTabAndPipe() {
        assertEquals('\t', CsvParser.detectDelimiter("a\tb\tc\n1\t2\t3\n"))
        assertEquals('|', CsvParser.detectDelimiter("a|b\n1|2\n"))
        assertEquals(';', CsvParser.detectDelimiter("data;valor\n01/01/2026;1,5\n02/01/2026;2,5\n"))
    }

    @Test
    fun utf16LittleEndian() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "x;y\n1;2".toByteArray(Charsets.UTF_16LE)
        assertEquals(listOf("1", "2"), CsvParser.parse(bytes).rows[1])
    }

    @Test
    fun emptyAndBinaryAreRejected() {
        assertEquals(ImportParseException.Reason.EMPTY, assertThrows(ImportParseException::class.java) { CsvParser.parse(" \n\n".toByteArray()) }.reason)
        assertEquals(
            ImportParseException.Reason.UNSUPPORTED_FORMAT,
            assertThrows(ImportParseException::class.java) { CsvParser.parse(byteArrayOf(1, 0, 2, 0)) }.reason,
        )
    }
}
