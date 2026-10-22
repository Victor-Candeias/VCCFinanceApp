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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.vcc.financeapp.core.data.importer.CandidateStatus
import pt.vcc.financeapp.core.data.importer.ColumnMappingDetector
import pt.vcc.financeapp.core.data.importer.CommitImportUseCase
import pt.vcc.financeapp.core.data.importer.ImportCommitRequest
import pt.vcc.financeapp.core.data.importer.ImportResult
import pt.vcc.financeapp.core.data.importer.ImportFile
import pt.vcc.financeapp.core.data.importer.ImportParseException
import pt.vcc.financeapp.core.data.importer.ImportRowParser
import pt.vcc.financeapp.core.data.importer.ImportTable
import pt.vcc.financeapp.core.data.importer.ParsedImportRow
import pt.vcc.financeapp.core.data.importer.ImportReview
import pt.vcc.financeapp.core.data.importer.ReadImportFileUseCase
import pt.vcc.financeapp.core.data.importer.RevertImportUseCase
import pt.vcc.financeapp.core.data.importer.ReviewImportUseCase
import pt.vcc.financeapp.core.data.repository.AccountRepository
import pt.vcc.financeapp.core.data.repository.ImportBatchRepository
import pt.vcc.financeapp.core.data.repository.ImportProfileRepository
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.ColumnMapping
import pt.vcc.financeapp.core.model.ImportBatch
import pt.vcc.financeapp.core.model.ImportProfile
import pt.vcc.financeapp.core.model.currencyFractionDigits

enum class ImportStep { MAPPING, REVIEW, DONE }

/** Entrada do histórico de importações com o nome da conta (vazio se a conta já não existir). */
data class ImportHistoryItem(val batch: ImportBatch, val accountName: String)

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
    /** Conta de destino; define também as casas decimais usadas para ler os valores. */
    val accountId: String? = null,
    val isParsing: Boolean = false,
    val step: ImportStep = ImportStep.MAPPING,
    val review: ImportReview? = null,
    /** `rowIndex` das linhas a importar. */
    val selectedRows: Set<Int> = emptySet(),
    val isWorking: Boolean = false,
    val commitFailed: Boolean = false,
    val result: ImportResult? = null,
    /** Importação a ser revertida a partir do histórico. */
    val revertingId: String? = null,
    val revertFailed: Boolean = false,
) {
    val validRows: Int get() = parsedRows.count { it.isValid }
    val invalidRows: Int get() = parsedRows.size - validRows
    val canReview: Boolean get() = accountId != null && !isParsing && !isWorking && validRows > 0
}

/** Leitura do extrato, mapeamento das colunas, perfis por banco, revisão de duplicados e gravação. */
@HiltViewModel
class ImportViewModel internal constructor(
    private val readImportFile: ReadImportFileUseCase,
    private val profileRepository: ImportProfileRepository,
    private val accountRepository: AccountRepository,
    private val reviewImport: ReviewImportUseCase,
    private val commitImport: CommitImportUseCase,
    private val revertImport: RevertImportUseCase,
    importBatchRepository: ImportBatchRepository,
    private val clock: Clock,
    private val computeDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @Inject
    constructor(
        readImportFile: ReadImportFileUseCase,
        profileRepository: ImportProfileRepository,
        accountRepository: AccountRepository,
        reviewImport: ReviewImportUseCase,
        commitImport: CommitImportUseCase,
        revertImport: RevertImportUseCase,
        importBatchRepository: ImportBatchRepository,
        clock: Clock,
    ) : this(
        readImportFile,
        profileRepository,
        accountRepository,
        reviewImport,
        commitImport,
        revertImport,
        importBatchRepository,
        clock,
        Dispatchers.Default,
    )

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()
    val profiles: StateFlow<List<ImportProfile>> = profileRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts: StateFlow<List<Account>> = accountRepository.observeActiveAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history: StateFlow<List<ImportHistoryItem>> =
        combine(importBatchRepository.observeAll(), accountRepository.observeAllAccounts()) { batches, accounts ->
            val names = accounts.associate { it.id to it.name }
            batches.map { ImportHistoryItem(it, names[it.accountId].orEmpty()) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private var readJob: Job? = null
    private var parseJob: Job? = null

    fun onFileSelected(uri: String) {
        readJob?.cancel()
        parseJob?.cancel()
        val previousAccount = _uiState.value.accountId
        _uiState.value = ImportUiState(isReading = true)
        readJob = viewModelScope.launch {
            val result = try {
                val file = readImportFile(uri)
                val active = accountRepository.observeActiveAccounts().first()
                val accountId = previousAccount?.takeIf { id -> active.any { it.id == id } } ?: active.singleOrNull()?.id
                val saved = findProfile(file.table)
                val mapping = saved?.mapping ?: withContext(computeDispatcher) { ColumnMappingDetector.detect(file.table) }
                ImportUiState(
                    file = file,
                    mapping = mapping,
                    profile = saved,
                    profileAutoApplied = saved != null,
                    profileName = saved?.name ?: defaultProfileName(file.fileName),
                    accountId = accountId,
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

    fun selectAccount(id: String) {
        if (_uiState.value.accountId == id) return
        _uiState.update { it.copy(accountId = id) }
        reparse()
    }

    /** Classifica as linhas válidas contra os movimentos da conta e passa à revisão. */
    fun review() {
        val state = _uiState.value
        val file = state.file ?: return
        val accountId = state.accountId ?: return
        if (!state.canReview) return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            val review = reviewImport(accountId, file.sha256, state.parsedRows)
            _uiState.update {
                it.copy(
                    isWorking = false,
                    step = ImportStep.REVIEW,
                    review = review,
                    selectedRows = review.candidates.filter { c -> c.status == CandidateStatus.NEW }.map { c -> c.row.rowIndex }.toSet(),
                    commitFailed = false,
                )
            }
        }
    }

    fun toggleRow(rowIndex: Int) {
        val candidate = _uiState.value.review?.candidates?.firstOrNull { it.row.rowIndex == rowIndex } ?: return
        if (!candidate.isSelectable) return
        _uiState.update { state ->
            val selected = if (rowIndex in state.selectedRows) state.selectedRows - rowIndex else state.selectedRows + rowIndex
            state.copy(selectedRows = selected)
        }
    }

    fun backToMapping() {
        _uiState.update { it.copy(step = ImportStep.MAPPING, review = null, selectedRows = emptySet(), commitFailed = false) }
    }

    /** Grava o lote e os movimentos escolhidos numa única transação. */
    fun confirmImport() {
        val state = _uiState.value
        val file = state.file ?: return
        val accountId = state.accountId ?: return
        val review = state.review ?: return
        if (state.isWorking || state.step != ImportStep.REVIEW || state.selectedRows.isEmpty()) return
        _uiState.update { it.copy(isWorking = true, commitFailed = false) }
        viewModelScope.launch {
            val request = ImportCommitRequest(
                accountId = accountId,
                fileName = file.fileName,
                format = file.format,
                fileSha256 = file.sha256,
                profileName = state.profile?.takeIf { it.mapping == state.mapping }?.name,
                candidates = review.candidates,
                selectedRows = state.selectedRows,
            )
            val result = try {
                commitImport(request)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            _uiState.update {
                if (result == null) it.copy(isWorking = false, commitFailed = true) else it.copy(isWorking = false, step = ImportStep.DONE, result = result)
            }
        }
    }

    /** Apaga os movimentos e o saldo de uma importação concluída e marca-a como revertida. */
    fun revertBatch(id: String) {
        if (_uiState.value.revertingId != null) return
        _uiState.update { it.copy(revertingId = id, revertFailed = false) }
        viewModelScope.launch {
            val deleted = try {
                revertImport(id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            _uiState.update { it.copy(revertingId = null, revertFailed = deleted == null) }
        }
    }

    fun clear() {
        readJob?.cancel()
        parseJob?.cancel()
        _uiState.value = ImportUiState(accountId = _uiState.value.accountId)
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
            _uiState.update { it.copy(parsedRows = emptyList(), isParsing = false) }
            return
        }
        val accountId = state.accountId
        _uiState.update { it.copy(isParsing = true) }
        parseJob = viewModelScope.launch {
            val digits = accountId?.let { accountRepository.getById(it) }?.currency?.let(::currencyFractionDigits) ?: DEFAULT_FRACTION_DIGITS
            val rows = withContext(computeDispatcher) { ImportRowParser.parse(table, mapping, digits) }
            _uiState.update {
                if (it.mapping == mapping && it.accountId == accountId) it.copy(parsedRows = rows, isParsing = false) else it
            }
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
        private const val DEFAULT_FRACTION_DIGITS = 2
    }
}
