package pt.vcc.financeapp.feature.finance.importexport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.data.importer.ImportFile
import pt.vcc.financeapp.core.data.importer.ImportParseException
import pt.vcc.financeapp.core.data.importer.ReadImportFileUseCase

data class ImportUiState(
    val isReading: Boolean = false,
    val file: ImportFile? = null,
    val error: ImportParseException.Reason? = null,
)

/** Leitura do extrato escolhido no seletor de ficheiros e pré-visualização da tabela obtida. */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val readImportFile: ReadImportFileUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()
    private var readJob: Job? = null

    fun onFileSelected(uri: String) {
        readJob?.cancel()
        _uiState.value = ImportUiState(isReading = true)
        readJob = viewModelScope.launch {
            val result = try {
                ImportUiState(file = readImportFile(uri))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportParseException) {
                ImportUiState(error = e.reason)
            } catch (_: Exception) {
                ImportUiState(error = ImportParseException.Reason.READ_FAILED)
            }
            _uiState.value = result
        }
    }

    fun clear() {
        readJob?.cancel()
        _uiState.value = ImportUiState()
    }

    companion object {
        /** Tipos MIME aceites no seletor; muitos gestores de ficheiros marcam CSV como texto ou binário. */
        val MIME_TYPES = arrayOf(
            "text/csv",
            "text/comma-separated-values",
            "text/plain",
            "text/tab-separated-values",
            "application/csv",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/octet-stream",
        )
        const val PREVIEW_ROWS = 20
    }
}
