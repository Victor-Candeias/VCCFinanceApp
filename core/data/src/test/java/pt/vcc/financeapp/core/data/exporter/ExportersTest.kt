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
import pt.vcc.financeapp.core.data.testing.FakeMerchantRepository
import pt.vcc.financeapp.core.data.testing.FakePurchaseRepository
import pt.vcc.financeapp.core.data.testing.FakeTransactionRepository
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.Category
import pt.vcc.financeapp.core.model.Merchant
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.PaymentMethod
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.core.model.PurchaseDetail
import pt.vcc.financeapp.core.model.PurchaseItem
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.core.model.PurchaseType
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
        tx("4", "2026-10-04T10:00:00Z", TransactionType.EXPENSE, 220, "Compra Pingo Doce", "food").copy(source = TransactionSource.PURCHASE),
    )
    private val purchase = PurchaseDetail(
        Purchase(
            id = "p1", type = PurchaseType.QUICK, status = PurchaseStatus.COMPLETED, occurredAt = Instant.parse("2026-10-04T10:00:00Z"),
            totalMinor = 220, merchantId = "pingo", paymentMethod = PaymentMethod.CASH, accountId = account.id, transactionId = "4",
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH, completedAt = Instant.parse("2026-10-04T10:00:00Z"),
        ),
        listOf(
            PurchaseItem("i1", "p1", "Leite", java.math.BigDecimal("2"), 95, position = 0),
            PurchaseItem("i2", "p1", "=Pão", java.math.BigDecimal.ONE, 30, position = 1),
        ),
    )
    private val merchant = Merchant("pingo", "Pingo Doce", "pingo doce", createdAt = Instant.EPOCH)
    private val data = ExportData(
        generatedAt = Instant.parse("2026-10-20T08:00:00Z"),
        zone = zone,
        range = ExportRange(null, null, "Outubro 2026"),
        accounts = listOf(account),
        categories = categories,
        transactions = transactions,
        purchases = listOf(purchase),
        merchants = listOf(merchant),
    )

    @Test
    fun csvIsReadableByTheImporterAndGuardsAgainstFormulas() {
        val bytes = ByteArrayOutputStream().also { CsvExporter.write(data, it) }.toByteArray()
        assertTrue(bytes.take(3) == listOf(0xEF, 0xBB, 0xBF).map(Int::toByte))

        val rows = CsvParser.parse(bytes).rows
        assertEquals(ExportText.TRANSACTION_HEADERS, rows[0])
        // A hora de Lisboa (UTC+1 em outubro) passa o primeiro movimento para 2 de outubro.
        assertEquals(
            listOf("2026-10-02", "", "CGD", "Despesa", "Jantar; \"amigos\"", "Alimentação › Restaurantes", "-12,50", "EUR", "Confirmado", "", "", ""),
            rows[1],
        )
        assertEquals("'=SALARIO()", rows[2][4])
        assertEquals("1500,00", rows[2][6])
        assertEquals("linha 1\nlinha 2", rows[2][9])
        assertEquals("Pendente", rows[3][8])
        assertEquals(listOf("Dinheiro", "Leite ×2 1,90; =Pão 0,30"), rows[4].takeLast(2))
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
        assertEquals(5, rows.size)
        assertEquals("Dinheiro", rows[4][10])
        assertEquals("AA", XlsxExporter.columnName(26))
        val zipNames = java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }
        assertTrue("xl/worksheets/sheet6.xml" in zipNames)
        val workbook = java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.first { it.name == "xl/workbook.xml" }
            zip.readBytes().toString(Charsets.UTF_8)
        }
        assertTrue(workbook.contains("name=\"Compras\"") && workbook.contains("name=\"Artigos\""))
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
        assertEquals(148_530L, totals.getValue("netMinor").jsonPrimitive.long)
        assertFalse(root.getValue("accounts").jsonArray.single().jsonObject.containsKey("iban"))
        val exportedPurchase = root.getValue("purchases").jsonArray.single().jsonObject
        assertEquals("CASH", exportedPurchase.getValue("paymentMethod").jsonPrimitive.content)
        assertEquals("Pingo Doce", exportedPurchase.getValue("merchantName").jsonPrimitive.content)
        assertEquals("4", exportedPurchase.getValue("transactionId").jsonPrimitive.content)
        val firstItem = exportedPurchase.getValue("items").jsonArray[0].jsonObject
        assertEquals("2", firstItem.getValue("quantity").jsonPrimitive.content)
        assertEquals(190L, firstItem.getValue("totalMinor").jsonPrimitive.long)
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
        val purchases = FakePurchaseRepository().apply {
            this.purchases.value = listOf(
                purchase.purchase,
                purchase.purchase.copy(id = "active", status = PurchaseStatus.ACTIVE, transactionId = null),
            )
            items.value = purchase.items
        }
        val useCase = ExportDataUseCase(
            FakeAccountRepository(listOf(account)),
            FakeCategoryRepository(categories),
            FakeTransactionRepository(transactions),
            purchases,
            FakeMerchantRepository(listOf(merchant)),
            writer,
            { _, _ -> pdfCalls++ },
            Clock.fixed(Instant.parse("2026-10-20T08:00:00Z"), ZoneOffset.UTC),
        )
        val range = ExportRange(Instant.parse("2026-10-02T00:00:00Z"), Instant.parse("2026-10-03T00:00:00Z"), "2 de outubro")

        assertEquals(ExportResult.Exported(1), useCase(ExportFormat.CSV, range, "content://x"))
        assertEquals(2, CsvParser.parse(written.toByteArray()).rows.size)
        assertEquals(ExportResult.Exported(4), useCase(ExportFormat.PDF, ExportRange(null, null, "Tudo"), "content://x"))
        assertEquals(1, pdfCalls)
        assertEquals(listOf("p1"), useCase.load(ExportRange(null, null, "Tudo")).purchases.map { it.purchase.id })
        assertEquals(emptyList<PurchaseDetail>(), useCase.load(range).purchases)

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
