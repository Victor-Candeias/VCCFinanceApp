package pt.vcc.financeapp.feature.finance.importexport

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.vcc.financeapp.core.data.importer.DocumentReader
import pt.vcc.financeapp.core.data.importer.ImportDocument
import pt.vcc.financeapp.core.data.importer.ImportParseException
import pt.vcc.financeapp.core.data.importer.ReadImportFileUseCase
import pt.vcc.financeapp.core.data.testing.FakeImportProfileRepository
import pt.vcc.financeapp.core.model.ColumnMapping
import pt.vcc.financeapp.core.model.ImportFormat

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
    private val viewModel by lazy { ImportViewModel(ReadImportFileUseCase(reader, dispatcher), profiles, clock, dispatcher) }

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
}
