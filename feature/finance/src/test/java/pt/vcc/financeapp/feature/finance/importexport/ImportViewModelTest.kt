package pt.vcc.financeapp.feature.finance.importexport

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.vcc.financeapp.core.data.importer.CandidateStatus
import pt.vcc.financeapp.core.data.importer.CommitImportUseCase
import pt.vcc.financeapp.core.data.importer.DocumentReader
import pt.vcc.financeapp.core.data.importer.ImportDocument
import pt.vcc.financeapp.core.data.importer.ImportParseException
import pt.vcc.financeapp.core.data.importer.ReadImportFileUseCase
import pt.vcc.financeapp.core.data.importer.RevertImportUseCase
import pt.vcc.financeapp.core.data.importer.ReviewImportUseCase
import pt.vcc.financeapp.core.data.testing.FakeAccountRepository
import pt.vcc.financeapp.core.data.testing.FakeImportBatchRepository
import pt.vcc.financeapp.core.data.testing.FakeImportProfileRepository
import pt.vcc.financeapp.core.data.testing.FakeTransactionRepository
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.ColumnMapping
import pt.vcc.financeapp.core.model.ImportFormat
import pt.vcc.financeapp.core.model.ImportStatus
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.Transaction
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.model.TransactionType

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val documents = mutableMapOf<String, ImportDocument>()
    private val reader = object : DocumentReader {
        override suspend fun read(uri: String): ImportDocument =
            documents[uri] ?: throw ImportParseException(ImportParseException.Reason.READ_FAILED)
    }
    private val profiles = FakeImportProfileRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC)
    private val accounts = FakeAccountRepository()
    private val transactions = FakeTransactionRepository()
    private val batches = FakeImportBatchRepository(transactions)
    private val viewModel by lazy {
        ImportViewModel(
            ReadImportFileUseCase(reader, dispatcher),
            profiles,
            accounts,
            ReviewImportUseCase(transactions, batches, clock, dispatcher),
            CommitImportUseCase(accounts, transactions, batches, clock),
            RevertImportUseCase(batches),
            batches,
            clock,
            dispatcher,
        )
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun readsCsvIntoPreview() {
        documents["content://a"] = ImportDocument("extrato.csv", "Data;Valor\n01/01/2026;-1,50\n".toByteArray())

        viewModel.onFileSelected("content://a")

        val state = viewModel.uiState.value
        assertFalse(state.isReading)
        assertNull(state.error)
        assertEquals(ImportFormat.CSV, state.file?.format)
        assertEquals(listOf("01/01/2026", "-1,50"), state.file?.table?.rows?.get(1))
        assertEquals(64, state.file?.sha256?.length)
    }

    @Test
    fun reportsUnsupportedAndReadErrors() {
        documents["content://pdf"] = ImportDocument("extrato.pdf", "%PDF-1.7".toByteArray())

        viewModel.onFileSelected("content://pdf")
        assertEquals(ImportParseException.Reason.UNSUPPORTED_FORMAT, viewModel.uiState.value.error)

        viewModel.onFileSelected("content://missing")
        assertEquals(ImportParseException.Reason.READ_FAILED, viewModel.uiState.value.error)

        viewModel.clear()
        assertEquals(ImportUiState(), viewModel.uiState.value)
    }

    @Test
    fun detectsMappingAndSavesProfileThatIsReappliedByHeader() {
        documents["content://cgd"] = ImportDocument(
            "extrato_cgd.csv",
            "Data Mov.;Descritivo;Débito;Crédito\n02/01/2026;Café;1,50;\n03/01/2026;Salário;;2.000,00\n".toByteArray(),
        )
        viewModel.onFileSelected("content://cgd")
        var state = viewModel.uiState.value
        assertEquals(ColumnMapping(headerRowIndex = 0, dateColumn = 0, descriptionColumn = 1, debitColumn = 2, creditColumn = 3), state.mapping)
        assertEquals(2, state.validRows)
        assertEquals("extrato cgd", state.profileName)

        viewModel.updateMapping { it.copy(invertAmounts = true) }
        viewModel.onProfileNameChange("CGD")
        viewModel.saveProfile()
        assertTrue(viewModel.uiState.value.profileSaved)
        val saved = profiles.profiles.value.single()
        assertEquals("data mov|descritivo|debito|credito", saved.headerSignature)
        assertTrue(saved.mapping.invertAmounts)

        // Outro extrato do mesmo banco: o perfil é reconhecido pelo cabeçalho.
        documents["content://cgd2"] = ImportDocument("fevereiro.csv", "Data Mov.;Descritivo;Débito;Crédito\n05/02/2026;Livraria;9,90;\n".toByteArray())
        viewModel.onFileSelected("content://cgd2")
        state = viewModel.uiState.value
        assertTrue(state.profileAutoApplied)
        assertEquals(saved.mapping, state.mapping)
        assertEquals("CGD", state.profileName)
    }

    @Test
    fun switchingAmountModeKeepsMappingConsistent() {
        documents["content://x"] = ImportDocument("x.csv", "Data;Descrição;Montante\n01/01/2026;A;-1,00\n".toByteArray())
        viewModel.onFileSelected("content://x")

        viewModel.setDebitCredit(true)
        var mapping = viewModel.uiState.value.mapping!!
        assertNull(mapping.amountColumn)
        assertEquals(2, mapping.debitColumn)

        viewModel.setDebitCredit(false)
        mapping = viewModel.uiState.value.mapping!!
        assertEquals(2, mapping.amountColumn)
        assertNull(mapping.debitColumn)

        viewModel.updateMapping { it.copy(descriptionColumn = null) }
        assertEquals(0, viewModel.uiState.value.parsedRows.size)
    }

    @Test
    fun reviewsAndImportsOnlySelectedRowsIntoTheAccount() {
        val account = Account("cgd", "CGD", AccountType.BANK_ACCOUNT, Money(0, "EUR"), createdAt = clock.instant())
        accounts.accounts.value = listOf(account)
        val manual = Transaction(
            id = "manual",
            accountId = account.id,
            type = TransactionType.EXPENSE,
            amount = Money(150, "EUR"),
            description = "Café da manhã",
            bookingDate = LocalDate.parse("2026-01-02").atTime(12, 0).toInstant(ZoneOffset.UTC),
            source = TransactionSource.MANUAL,
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
        )
        transactions.transactions.value = listOf(manual)
        val csv = "Data Mov.;Descritivo;Débito;Crédito\n02/01/2026;Café;1,50;\n03/01/2026;Salário;;2.000,00\n".toByteArray()
        documents["content://cgd"] = ImportDocument("extrato_cgd.csv", csv)

        viewModel.onFileSelected("content://cgd")
        assertEquals(account.id, viewModel.uiState.value.accountId)
        viewModel.review()
        var state = viewModel.uiState.value
        assertEquals(ImportStep.REVIEW, state.step)
        assertEquals(listOf(CandidateStatus.POSSIBLE_DUPLICATE, CandidateStatus.NEW), state.review?.candidates?.map { it.status })
        assertEquals(setOf(2), state.selectedRows)

        viewModel.toggleRow(1)
        viewModel.toggleRow(1)
        viewModel.confirmImport()
        state = viewModel.uiState.value
        assertEquals(ImportStep.DONE, state.step)
        assertEquals(1, state.result?.batch?.importedRows)
        assertEquals(1, state.result?.batch?.duplicateRows)
        val imported = transactions.transactions.value.single { it.id != manual.id }
        assertEquals(TransactionType.INCOME, imported.type)
        assertEquals(Money(200_000, "EUR"), imported.amount)
        assertEquals(state.result?.batch?.id, imported.importBatchId)
        assertNull(state.result?.reconciliation)

        // O mesmo ficheiro outra vez: aviso e a linha importada aparece como duplicada.
        viewModel.onFileSelected("content://cgd")
        viewModel.review()
        state = viewModel.uiState.value
        assertNotNull(state.review?.previousImport)
        assertEquals(listOf(CandidateStatus.POSSIBLE_DUPLICATE, CandidateStatus.DUPLICATE), state.review?.candidates?.map { it.status })
        assertTrue(state.selectedRows.isEmpty())

        // Histórico com o nome da conta; reverter apaga só os movimentos importados.
        val collector = CoroutineScope(dispatcher).launch { viewModel.history.collect {} }
        val item = viewModel.history.value.single()
        assertEquals("CGD", item.accountName)
        viewModel.revertBatch(item.batch.id)
        assertEquals(ImportStatus.REVERTED, viewModel.history.value.single().batch.status)
        assertEquals(listOf(manual.id), transactions.transactions.value.map { it.id })
        assertNull(viewModel.uiState.value.revertingId)
        assertFalse(viewModel.uiState.value.revertFailed)
        viewModel.revertBatch(item.batch.id)
        assertTrue(viewModel.uiState.value.revertFailed)
        collector.cancel()
    }
}
