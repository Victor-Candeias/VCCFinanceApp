package pt.vcc.financeapp.core.data.importer

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import pt.vcc.financeapp.core.data.importer.ImportParseException.Reason

class XlsxParserTest {
    @Test
    fun readsFirstSheetWithSharedStringsDatesAndNumbers() {
        val table = XlsxParser.parse(
            xlsx(
                sheet = """
                    <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="D1" t="inlineStr"><is><t>Valor</t></is></c></row>
                    <row r="3"><c r="A3" s="1"><v>46024</v></c><c r="B3" t="s"><v>2</v></c><c r="D3"><v>-12.300000000000001</v></c></row>
                    <row r="4"><c r="A4" s="2"><v>46025.5</v></c><c r="B4" t="str"><v>Fórmula</v></c><c r="D4" s="3"><v>1234.5</v></c></row>
                """,
            ),
        )

        assertEquals(listOf("Data", "Descrição", "", "Valor"), table.rows[0])
        assertEquals(listOf("2026-01-02", "Café Central", "", "-12,3"), table.rows[1])
        assertEquals(listOf("2026-01-03", "Fórmula", "", "1234,5"), table.rows[2])
    }

    @Test
    fun date1904System() {
        val table = XlsxParser.parse(xlsx(sheet = """<row><c r="A1" s="1"><v>0</v></c></row>""", date1904 = true))
        assertEquals("1904-01-01", table.cell(0, 0))
    }

    @Test
    fun helpers() {
        assertEquals(0, XlsxParser.columnIndex("A1"))
        assertEquals(27, XlsxParser.columnIndex("AB12"))
        assertEquals("0", XlsxParser.formatNumber("0.0"))
        assertEquals("1000000", XlsxParser.formatNumber("1E6"))
    }

    @Test
    fun rejectsDoctypeAndInvalidZip() {
        val doctype = xlsx(sheet = "", sheetXml = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY a "b">]><worksheet/>""")
        assertEquals(Reason.INVALID_FILE, assertThrows(ImportParseException::class.java) { XlsxParser.parse(doctype) }.reason)
        assertEquals(Reason.INVALID_FILE, assertThrows(ImportParseException::class.java) { XlsxParser.parse("PK".toByteArray()) }.reason)
    }

    @Test
    fun detectorUsesSignature() {
        assertEquals(pt.vcc.financeapp.core.model.ImportFormat.XLSX, ImportFormatDetector.detect("a.xlsx", xlsx(sheet = "")))
        assertEquals(pt.vcc.financeapp.core.model.ImportFormat.CSV, ImportFormatDetector.detect("a.CSV", "a;b".toByteArray()))
        val ole = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0)
        assertEquals(Reason.UNSUPPORTED_FORMAT, assertThrows(ImportParseException::class.java) { ImportFormatDetector.detect("a.xls", ole) }.reason)
        assertEquals(Reason.UNSUPPORTED_FORMAT, assertThrows(ImportParseException::class.java) { ImportFormatDetector.detect("a.pdf", "%PDF-1.7".toByteArray()) }.reason)
    }

    private fun xlsx(sheet: String, date1904: Boolean = false, sheetXml: String? = null): ByteArray {
        val ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        val rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        val parts = mapOf(
            "xl/workbook.xml" to """<?xml version="1.0"?><workbook xmlns="$ns" xmlns:r="$rel"><workbookPr date1904="${if (date1904) 1 else 0}"/><sheets><sheet name="Movimentos" sheetId="1" r:id="rId7"/><sheet name="Outra" sheetId="2" r:id="rId8"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId7" Type="t" Target="worksheets/sheet2.xml"/><Relationship Id="rId8" Type="t" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/sharedStrings.xml" to """<?xml version="1.0"?><sst xmlns="$ns"><si><t>Data</t></si><si><r><t>Descri</t></r><r><t>ção</t></r><rPh><t>x</t></rPh></si><si><t xml:space="preserve"> Café Central </t></si></sst>""",
            "xl/styles.xml" to """<?xml version="1.0"?><styleSheet xmlns="$ns"><numFmts count="1"><numFmt numFmtId="164" formatCode="dd/mm/yyyy;@"/><numFmt numFmtId="165" formatCode="#,##0.00\ &quot;€&quot;"/></numFmts><cellXfs count="4"><xf numFmtId="0"/><xf numFmtId="14"/><xf numFmtId="164"/><xf numFmtId="165"/></cellXfs></styleSheet>""",
            "xl/worksheets/sheet1.xml" to """<?xml version="1.0"?><worksheet xmlns="$ns"><sheetData><row><c t="inlineStr"><is><t>errado</t></is></c></row></sheetData></worksheet>""",
            "xl/worksheets/sheet2.xml" to (sheetXml ?: """<?xml version="1.0"?><worksheet xmlns="$ns"><sheetData>$sheet</sheetData></worksheet>"""),
        )
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            parts.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
