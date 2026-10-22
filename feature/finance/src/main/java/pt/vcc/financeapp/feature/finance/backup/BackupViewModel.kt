package pt.vcc.financeapp.feature.finance.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.data.backup.BackupError
import pt.vcc.financeapp.core.data.backup.BackupManager
import pt.vcc.financeapp.core.data.backup.BackupResult
import pt.vcc.financeapp.core.data.backup.BackupSummary
import pt.vcc.financeapp.core.data.backup.OpenedBackup

enum class BackupPasswordIssue { TOO_SHORT, MISMATCH }

sealed interface BackupMessage {
    data class Created(val summary: BackupSummary) : BackupMessage
    data class Restored(val summary: BackupSummary) : BackupMessage
    data class Failed(val error: BackupError) : BackupMessage
}

data class BackupUiState(
    val today: LocalDate,
    val password: String = "",
    val confirmation: String = "",
    val restoreUri: String? = null,
    val restorePassword: String = "",
    /** Backup decifrado à espera da confirmação de que os dados atuais vão ser substituídos. */
    val pending: OpenedBackup? = null,
    val isBusy: Boolean = false,
    val message: BackupMessage? = null,
) {
    val passwordIssue: BackupPasswordIssue?
        get() = when {
            password.length < MIN_PASSWORD_LENGTH -> BackupPasswordIssue.TOO_SHORT
            password != confirmation -> BackupPasswordIssue.MISMATCH
            else -> null
        }

    val canCreate: Boolean get() = passwordIssue == null && !isBusy

    val canOpen: Boolean get() = restoreUri != null && restorePassword.isNotEmpty() && !isBusy

    /** Nome sugerido ao seletor de destino, ex.: `vccfinance-backup-2026-10-22.vccbackup`. */
    val fileName: String get() = "vccfinance-backup-$today.$EXTENSION"

    companion object {
        const val MIN_PASSWORD_LENGTH = 10
        const val EXTENSION = "vccbackup"
        const val MIME_TYPE = "application/octet-stream"
    }
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backups: BackupManager,
    clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BackupUiState(today = LocalDate.now(clock)))
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun updatePassword(value: String) = _uiState.update { it.copy(password = value, message = null) }

    fun updateConfirmation(value: String) = _uiState.update { it.copy(confirmation = value, message = null) }

    fun selectRestoreFile(uri: String) = _uiState.update { it.copy(restoreUri = uri, restorePassword = "", message = null) }

    fun updateRestorePassword(value: String) = _uiState.update { it.copy(restorePassword = value, message = null) }

    /** Escreve o backup no documento [uri] escolhido pelo utilizador. */
    fun create(uri: String) {
        val state = _uiState.value
        if (!state.canCreate) return
        run(state) {
            when (val result = backups.create(uri, state.password.toCharArray())) {
                is BackupResult.Created -> it.copy(password = "", confirmation = "", message = BackupMessage.Created(result.summary))
                else -> it.copy(message = failure(result))
            }
        }
    }

    /** Decifra e valida o ficheiro escolhido; os dados só mudam depois de [confirmRestore]. */
    fun open() {
        val state = _uiState.value
        val uri = state.restoreUri
        if (!state.canOpen || uri == null) return
        run(state) {
            when (val result = backups.open(uri, state.restorePassword.toCharArray())) {
                is BackupResult.Opened -> it.copy(pending = result.backup)
                else -> it.copy(message = failure(result))
            }
        }
    }

    fun cancelRestore() = _uiState.update { it.copy(pending = null) }

    fun confirmRestore() {
        val state = _uiState.value
        val backup = state.pending ?: return
        if (state.isBusy) return
        run(state.copy(pending = null)) {
            when (val result = backups.restore(backup)) {
                is BackupResult.Restored ->
                    it.copy(restoreUri = null, restorePassword = "", message = BackupMessage.Restored(result.summary))
                else -> it.copy(message = failure(result))
            }
        }
    }

    private fun run(state: BackupUiState, block: suspend (BackupUiState) -> BackupUiState) {
        _uiState.value = state.copy(isBusy = true, message = null)
        viewModelScope.launch {
            val next = block(_uiState.value)
            _uiState.value = next.copy(isBusy = false)
        }
    }

    private fun failure(result: BackupResult): BackupMessage =
        BackupMessage.Failed((result as? BackupResult.Failed)?.error ?: BackupError.RESTORE_FAILED)
}
