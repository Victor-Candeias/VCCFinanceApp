package pt.vcc.financeapp.feature.finance.importexport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.vcc.financeapp.core.data.importer.ColumnMappingDetector
import pt.vcc.financeapp.core.data.importer.ImportFile
import pt.vcc.financeapp.core.data.importer.ImportParseException
import pt.vcc.financeapp.core.data.importer.ImportRowParser
import pt.vcc.financeapp.core.data.importer.ImportTable
import pt.vcc.financeapp.core.data.importer.ParsedImportRow
import pt.vcc.financeapp.core.data.importer.ReadImportFileUseCase
import pt.vcc.financeapp.core.data.repository.ImportProfileRepository
import pt.vcc.financeapp.core.model.ColumnMapping
import pt.vcc.financeapp.core.model.ImportProfile

data class ImportUiState(
    val isReading: Boolean = false,
    val file: ImportFile? = null,
    val error: ImportParseException.Reason? = null,
    val mapping: ColumnMapping? = null,
    /** Linhas interpretadas com o [mapping] atual (vazio enquanto o mapeamento estiver incompleto). */
    val parsedRows: List<ParsedImportRow> = emptyList(),
    /** Perfil em uso: reconhecido pelo cabeçalho, escolhido ou acabado de guardar. */
    val profile: ImportProfile? = null,
    val profileAutoApplied: Boolean = false,
    val profileName: String = "",
    val profileSaved: Boolean = false,
) {
    val validRows: Int get() = parsedRows.count { it.isValid }
    val invalidRows: Int get() = parsedRows.size - validRows
}

/** Leitura do extrato, mapeamento das colunas e perfis por banco. */
@HiltViewModel
class ImportViewModel internal constructor(
    private val readImportFile: ReadImportFileUseCase,
    private val profileRepository: ImportProfileRepository,
    private val clock: Clock,
    private val computeDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @Inject
    constructor(
        readImportFile: ReadImportFileUseCase,
        profileRepository: ImportProfileRepository,
        clock: Clock,
    ) : this(readImportFile, profileRepository, clock, Dispatchers.Default)

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()
    val profiles: StateFlow<List<ImportProfile>> = profileRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private var readJob: Job? = null
    private var parseJob: Job? = null

    fun onFileSelected(uri: String) {
        readJob?.cancel()
        parseJob?.cancel()
        _uiState.value = ImportUiState(isReading = true)
        readJob = viewModelScope.launch {
            val result = try {
                val file = readImportFile(uri)
                val saved = findProfile(file.table)
                val mapping = saved?.mapping ?: withContext(computeDispatcher) { ColumnMappingDetector.detect(file.table) }
                ImportUiState(
                    file = file,
                    mapping = mapping,
                    profile = saved,
                    profileAutoApplied = saved != null,
                    profileName = saved?.name ?: defaultProfileName(file.fileName),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportParseException) {
                ImportUiState(error = e.reason)
            } catch (_: Exception) {
                ImportUiState(error = ImportParseException.Reason.READ_FAILED)
            }
            _uiState.value = result
            reparse()
        }
    }

    fun updateMapping(transform: (ColumnMapping) -> ColumnMapping) {
        val current = _uiState.value.mapping ?: return
        val updated = transform(current)
        if (updated == current) return
        _uiState.update { it.copy(mapping = updated, profileSaved = false, profileAutoApplied = false) }
        reparse()
    }

    /** Alterna entre uma coluna de valor com sinal e o par débito/crédito. */
    fun setDebitCredit(enabled: Boolean) = updateMapping { mapping ->
        if (enabled) {
            mapping.copy(amountColumn = null, debitColumn = mapping.debitColumn ?: mapping.amountColumn)
        } else {
            mapping.copy(amountColumn = mapping.amountColumn ?: mapping.debitColumn ?: mapping.creditColumn, debitColumn = null, creditColumn = null)
        }
    }

    fun applyProfile(profile: ImportProfile) {
        val table = _uiState.value.file?.table ?: return
        val mapping = profile.mapping.takeIf { it.fitsTable(table) } ?: return
        _uiState.update {
            it.copy(mapping = mapping, profile = profile, profileAutoApplied = false, profileName = profile.name, profileSaved = false)
        }
        reparse()
    }

    fun onProfileNameChange(name: String) {
        _uiState.update { it.copy(profileName = name.take(MAX_PROFILE_NAME), profileSaved = false) }
    }

    /** Guarda o mapeamento atual; atualiza o perfil em uso se o nome não mudou. */
    fun saveProfile() {
        val state = _uiState.value
        val table = state.file?.table ?: return
        val mapping = state.mapping?.takeIf { it.isComplete } ?: return
        val name = state.profileName.trim().takeIf { it.isNotEmpty() } ?: return
        val now = clock.instant()
        val existing = state.profile?.takeIf { it.name == name }
            ?: profiles.value.firstOrNull { it.name.equals(name, ignoreCase = true) }
        val profile = ImportProfile(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name,
            headerSignature = ColumnMappingDetector.headerSignature(table, mapping.headerRowIndex),
            mapping = mapping,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        viewModelScope.launch {
            profileRepository.save(profile)
            _uiState.update { it.copy(profile = profile, profileName = name, profileSaved = true) }
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            profileRepository.delete(id)
            _uiState.update { if (it.profile?.id == id) it.copy(profile = null, profileSaved = false) else it }
        }
    }

    fun clear() {
        readJob?.cancel()
        parseJob?.cancel()
        _uiState.value = ImportUiState()
    }

    private suspend fun findProfile(table: ImportTable): ImportProfile? {
        for (row in 0 until minOf(HEADER_SEARCH_ROWS, table.rows.size)) {
            val signature = ColumnMappingDetector.headerSignature(table, row)
            if (signature.isEmpty()) continue
            val profile = profileRepository.findBySignature(signature) ?: continue
            if (profile.mapping.headerRowIndex == row && profile.mapping.fitsTable(table)) return profile
        }
        return null
    }

    private fun reparse() {
        parseJob?.cancel()
        val state = _uiState.value
        val table = state.file?.table
        val mapping = state.mapping
        if (table == null || mapping == null || !mapping.isComplete) {
            _uiState.update { it.copy(parsedRows = emptyList()) }
            return
        }
        parseJob = viewModelScope.launch {
            val rows = withContext(computeDispatcher) { ImportRowParser.parse(table, mapping) }
            _uiState.update { if (it.mapping == mapping) it.copy(parsedRows = rows) else it }
        }
    }

    private fun ColumnMapping.fitsTable(table: ImportTable): Boolean =
        listOfNotNull(dateColumn, descriptionColumn, amountColumn, debitColumn, creditColumn, valueDateColumn, referenceColumn, balanceColumn)
            .all { it < table.columnCount } && headerRowIndex < table.rows.size

    private fun defaultProfileName(fileName: String): String =
        fileName.substringBeforeLast('.').replace('_', ' ').trim().take(MAX_PROFILE_NAME)

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
        const val HEADER_SEARCH_ROWS = 15
        const val MAX_PROFILE_NAME = 40
    }
}
