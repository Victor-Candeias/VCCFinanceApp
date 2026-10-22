package pt.vcc.financeapp.core.data.exporter

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.financeapp.core.data.importer.CsvParser
import pt.vcc.financeapp.core.data.importer.XlsxParser
import pt.vcc.financeapp.core.data.testing.FakeAccountRepository
import pt.vcc.financeapp.core.data.testing.FakeCategoryRepository
import pt.vcc.financeapp.core.data.testing.FakeTransactionRepository
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.Category
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.Transaction
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.model.TransactionStatus
import pt.vcc.financeapp.core.model.TransactionType

class ExportersTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    private val account = Account(
        id = "acc",
        name = "CGD",
        type = AccountType.BANK_ACCOUNT,
        openingBalance = Money(10_000, "EUR"),
        ibanEncrypted = "segredo-cifrado",
        createdAt = Instant.EPOCH,
    )
    private val categories = listOf(
        Category("food", "Alimentação", icon = "", color = "", type = TransactionType.EXPENSE),
        Category("rest", "Restaurantes", icon = "", color = "", type = TransactionType.EXPENSE, parentCategoryId = "food"),
    )
    private val transactions = listOf(
        tx("1", "2026-10-01T23:30:00Z", TransactionType.EXPENSE, 1_250, "Jantar; \"amigos\"", "rest"),
        tx("2", "2026-10-02T10:00:00Z", TransactionType.INCOME, 150_000, "=SALARIO()", null, notes = "linha 1\nlinha 2"),
        tx("3", "2026-10-03T10:00:00Z", TransactionType.EXPENSE, 999, "Pendente", "food", status = TransactionStatus.PENDING),
    )
    private val data = ExportData(
        generatedAt = Instant.parse("2026-10-20T08:00:00Z"),
        zone = zone,
        range = ExportRange(null, null, "Outubro 2026"),
        accounts = listOf(account),
        categories = categories,
        transactions = transactions,
    )

    @Test
    fun csvIsReadableByTheImporterAndGuardsAgainstFormulas() {
        val bytes = ByteArrayOutputStream().also { CsvExporter.write(data, it) }.toByteArray()
        assertTrue(bytes.take(3) == listOf(0xEF, 0xBB, 0xBF).map(Int::toByte))

        val rows = CsvParser.parse(bytes).rows
        assertEquals(ExportText.TRANSACTION_HEADERS, rows[0])
        // A hora de Lisboa (UTC+1 em outubro) passa o primeiro movimento para 2 de outubro.
        assertEquals(
            listOf("2026-10-02", "", "CGD", "Despesa", "Jantar; \"amigos\"", "Alimentação › Restaurantes", "-12,50", "EUR", "Confirmado", ""),
            rows[1],
        )
        assertEquals("'=SALARIO()", rows[2][4])
        assertEquals("1500,00", rows[2][6])
        assertEquals("linha 1\nlinha 2", rows[2][9])
        assertEquals("Pendente", rows[3][8])
        assertFalse(String(bytes, Charsets.UTF_8).contains("segredo"))
    }

    @Test
    fun xlsxFirstSheetRoundTripsThroughTheImporter() {
        val bytes = ByteArrayOutputStream().also { XlsxExporter.write(data, it) }.toByteArray()

        val rows = XlsxParser.parse(bytes).rows
        assertEquals(ExportText.TRANSACTION_HEADERS, rows[0])
        assertEquals("2026-10-02", rows[1][0])
        assertEquals("-12,5", rows[1][6])
        assertEquals("Jantar; \"amigos\"", rows[1][4])
        assertEquals("=SALARIO()", rows[2][4])
        assertEquals(4, rows.size)
        assertEquals("AA", XlsxExporter.columnName(26))
    }

    @Test
    fun jsonKeepsIdentifiersAndMinorUnits() {
        val bytes = ByteArrayOutputStream().also { JsonExporter.write(data, it) }.toByteArray()
        val root = Json.parseToJsonElement(String(bytes, Charsets.UTF_8)).jsonObject

        assertEquals(JsonExporter.FORMAT, root.getValue("format").jsonPrimitive.content)
        val first = root.getValue("transactions").jsonArray[0].jsonObject
        assertEquals("rest", first.getValue("categoryId").jsonPrimitive.content)
        assertEquals(1_250L, first.getValue("amountMinor").jsonPrimitive.long)
        val totals = root.getValue("totals").jsonArray.single().jsonObject
        assertEquals(148_750L, totals.getValue("netMinor").jsonPrimitive.long)
        assertFalse(root.getValue("accounts").jsonArray.single().jsonObject.containsKey("iban"))
        assertFalse(String(bytes, Charsets.UTF_8).contains("segredo"))
    }

    @Test
    fun useCaseFiltersPeriodAndReportsFailures() = runTest {
        val written = ByteArrayOutputStream()
        var failWrite = false
        val writer = object : DocumentWriter {
            override suspend fun write(uri: String, block: (OutputStream) -> Unit) {
                if (failWrite) throw IOException("sem espaço")
                block(written)
            }
        }
        var pdfCalls = 0
        val useCase = ExportDataUseCase(
            FakeAccountRepository(listOf(account)),
            FakeCategoryRepository(categories),
            FakeTransactionRepository(transactions),
            writer,
            { _, _ -> pdfCalls++ },
            Clock.fixed(Instant.parse("2026-10-20T08:00:00Z"), ZoneOffset.UTC),
        )
        val range = ExportRange(Instant.parse("2026-10-02T00:00:00Z"), Instant.parse("2026-10-03T00:00:00Z"), "2 de outubro")

        assertEquals(ExportResult.Exported(1), useCase(ExportFormat.CSV, range, "content://x"))
        assertEquals(2, CsvParser.parse(written.toByteArray()).rows.size)
        assertEquals(ExportResult.Exported(3), useCase(ExportFormat.PDF, ExportRange(null, null, "Tudo"), "content://x"))
        assertEquals(1, pdfCalls)

        failWrite = true
        assertEquals(ExportResult.Failed, useCase(ExportFormat.JSON, range, "content://x"))
    }

    private fun tx(
        id: String,
        date: String,
        type: TransactionType,
        amount: Long,
        description: String,
        categoryId: String?,
        notes: String? = null,
        status: TransactionStatus = TransactionStatus.BOOKED,
    ) = Transaction(
        id = id,
        accountId = account.id,
        type = type,
        amount = Money(amount, "EUR"),
        description = description,
        bookingDate = Instant.parse(date),
        source = TransactionSource.MANUAL,
        status = status,
        categoryId = categoryId,
        notes = notes,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
