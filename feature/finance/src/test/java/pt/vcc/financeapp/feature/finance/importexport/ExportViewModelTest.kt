package pt.vcc.financeapp.feature.finance.importexport

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.vcc.financeapp.core.data.exporter.DocumentWriter
import pt.vcc.financeapp.core.data.exporter.ExportDataUseCase
import pt.vcc.financeapp.core.data.exporter.ExportFormat
import pt.vcc.financeapp.core.data.exporter.ExportResult
import pt.vcc.financeapp.core.data.testing.FakeAccountRepository
import pt.vcc.financeapp.core.data.testing.FakeCategoryRepository
import pt.vcc.financeapp.core.data.testing.FakeTransactionRepository
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.Transaction
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.model.TransactionType

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private val written = ByteArrayOutputStream()
    private val writer = object : DocumentWriter {
        override suspend fun write(uri: String, block: (OutputStream) -> Unit) = block(written)
    }
    private val transactions = FakeTransactionRepository(
        listOf(tx("oct", "2026-10-02T10:00:00Z"), tx("sep", "2026-09-30T10:00:00Z"), tx("old", "2025-03-01T10:00:00Z")),
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun countsMovementsPerScopeAndExportsTheChosenFormat() {
        val vm = ExportViewModel(
            ExportDataUseCase(FakeAccountRepository(), FakeCategoryRepository(), transactions, writer, { _, _ -> }, clock, Dispatchers.Main),
            clock,
        )
        assertEquals(1, vm.uiState.value.transactionCount)
        assertEquals("vccfinance-2026-10.csv", vm.uiState.value.fileName)

        vm.selectScope(ExportScope.YEAR)
        assertEquals(2, vm.uiState.value.transactionCount)
        vm.previousPeriod()
        assertEquals(1, vm.uiState.value.transactionCount)
        assertEquals("vccfinance-2025.csv", vm.uiState.value.fileName)

        vm.selectScope(ExportScope.ALL)
        vm.selectFormat(ExportFormat.JSON)
        assertEquals(3, vm.uiState.value.transactionCount)
        assertEquals("vccfinance-completo.json", vm.uiState.value.fileName)

        vm.export("content://destino", "Todos os movimentos")
        assertEquals(ExportResult.Exported(3), vm.uiState.value.result)
        assertTrue(written.toString(Charsets.UTF_8.name()).contains("\"label\": \"Todos os movimentos\""))
    }

    private fun tx(id: String, date: String) = Transaction(
        id = id,
        accountId = "acc",
        type = TransactionType.EXPENSE,
        amount = Money(100, "EUR"),
        description = id,
        bookingDate = Instant.parse(date),
        source = TransactionSource.MANUAL,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
