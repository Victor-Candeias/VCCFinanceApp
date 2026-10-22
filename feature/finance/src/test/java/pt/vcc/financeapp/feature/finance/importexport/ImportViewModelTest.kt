package pt.vcc.financeapp.feature.finance.importexport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import pt.vcc.financeapp.core.data.importer.DocumentReader
import pt.vcc.financeapp.core.data.importer.ImportDocument
import pt.vcc.financeapp.core.data.importer.ImportParseException
import pt.vcc.financeapp.core.data.importer.ReadImportFileUseCase
import pt.vcc.financeapp.core.model.ImportFormat

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val documents = mutableMapOf<String, ImportDocument>()
    private val reader = object : DocumentReader {
        override suspend fun read(uri: String): ImportDocument =
            documents[uri] ?: throw ImportParseException(ImportParseException.Reason.READ_FAILED)
    }
    private val viewModel by lazy { ImportViewModel(ReadImportFileUseCase(reader, dispatcher)) }

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
}
