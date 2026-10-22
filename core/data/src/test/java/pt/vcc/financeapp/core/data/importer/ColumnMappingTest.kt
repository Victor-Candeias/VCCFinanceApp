package pt.vcc.financeapp.core.data.importer

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.financeapp.core.model.ColumnMapping
import pt.vcc.financeapp.core.model.ImportDateFormat
import pt.vcc.financeapp.core.model.TransactionType

class ColumnMappingTest {
    private fun table(vararg rows: String) = ImportTable(rows.map { it.split(";") })

    @Test
    fun detectsHeaderAfterPreambleWithDebitCredit() {
        val table = table(
            "Consultar saldos e movimentos",
            "Conta;0001234",
            "Data Mov.;Data Valor;Descrição;Débito;Crédito;Saldo contabilístico",
            "02/01/2026;02/01/2026;Café;1,50;;1.234,50",
            "03/01/2026;03/01/2026;Salário;;2.000,00;3.234,50",
        )
        val mapping = ColumnMappingDetector.detect(table)

        assertEquals(
            ColumnMapping(
                headerRowIndex = 2, dateColumn = 0, descriptionColumn = 2, debitColumn = 3, creditColumn = 4,
                valueDateColumn = 1, balanceColumn = 5, dateFormat = ImportDateFormat.DAY_MONTH_YEAR_SLASH,
            ),
            mapping,
        )
        assertEquals("data mov|data valor|descricao|debito|credito|saldo contabilistico", ColumnMappingDetector.headerSignature(table, 2))

        val rows = ImportRowParser.parse(table, mapping)
        assertEquals(2, rows.size)
        assertEquals(LocalDate.of(2026, 1, 2), rows[0].bookingDate)
        assertEquals(TransactionType.EXPENSE, rows[0].type)
        assertEquals(150L, rows[0].amountMinor)
        assertEquals(123450L, rows[0].balanceMinor)
        assertEquals(TransactionType.INCOME, rows[1].type)
        assertEquals(200000L, rows[1].amountMinor)
        assertEquals(3, rows[0].rowIndex)
    }

    @Test
    fun singleSignedAmountIsoDatesAndReference() {
        val table = table(
            "Date;Description;Amount (EUR);Reference",
            "2026-01-05;Supermercado   Pingo;-45,67 EUR;ABC1",
            "2026-01-06;Reembolso;10;",
        )
        val mapping = ColumnMappingDetector.detect(table)
        assertEquals(2, mapping.amountColumn)
        assertEquals(3, mapping.referenceColumn)
        assertEquals(ImportDateFormat.ISO, mapping.dateFormat)

        val rows = ImportRowParser.parse(table, mapping)
        assertEquals("Supermercado Pingo", rows[0].description)
        assertEquals(TransactionType.EXPENSE, rows[0].type)
        assertEquals(4567L, rows[0].amountMinor)
        assertEquals("ABC1", rows[0].reference)
        assertNull(rows[1].reference)

        val inverted = ImportRowParser.parse(table, mapping.copy(invertAmounts = true))
        assertEquals(TransactionType.INCOME, inverted[0].type)
    }

    @Test
    fun invalidRowsAreReportedNotDropped() {
        val table = table(
            "Data;Descritivo;Valor",
            "31/02/2026;Data impossível;-1,00",
            "01/02/2026;Valor inválido;abc",
            "02/02/2026;Zero;0,00",
            "03/02/2026;;-2,00",
            "Saldo final;;",
        )
        val rows = ImportRowParser.parse(table, ColumnMappingDetector.detect(table))

        assertEquals(
            listOf(RowError.INVALID_DATE, RowError.INVALID_AMOUNT, RowError.ZERO_AMOUNT, RowError.MISSING_DESCRIPTION, RowError.INVALID_DATE),
            rows.map { it.error },
        )
    }

    @Test
    fun dateFormatDetection() {
        assertEquals(ImportDateFormat.DAY_MONTH_YEAR_DASH, ColumnMappingDetector.detectDateFormat(listOf("01-02-2026", "13-02-2026")))
        assertEquals(ImportDateFormat.DAY_MONTH_SHORT_YEAR_SLASH, ColumnMappingDetector.detectDateFormat(listOf("01/02/26 10:00")))
        assertEquals(ImportDateFormat.DAY_MONTH_YEAR_DOT, ColumnMappingDetector.detectDateFormat(listOf("01.02.2026")))
    }

    @Test
    fun infersColumnsWithoutHeader() {
        val table = table("02/01/2026;Café Central;-1,50", "03/01/2026;Padaria;-2,10")
        val mapping = ColumnMappingDetector.detect(table)

        assertEquals(-1, mapping.headerRowIndex)
        assertEquals(0, mapping.dateColumn)
        assertEquals(1, mapping.descriptionColumn)
        assertEquals(2, mapping.amountColumn)
        assertTrue(mapping.isComplete)
        assertEquals("", ColumnMappingDetector.headerSignature(table, -1))
        assertEquals(2, ImportRowParser.parse(table, mapping).size)
    }
}
