package pt.vcc.financeapp.feature.finance.authentication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.data.recovery.AppRecoveryManager

data class ResetAppUiState(
    val confirmation: String = "",
    val isErasing: Boolean = false,
    val failed: Boolean = false,
    /** Dados apagados: a UI tem de reiniciar a app. */
    val isErased: Boolean = false,
) {
    val canErase: Boolean get() = confirmation.trim() == ResetAppViewModel.CONFIRMATION_WORD && !isErasing && !isErased
}

/** Apaga todos os dados (PIN esquecido ou chaves perdidas) depois de o utilizador escrever [CONFIRMATION_WORD]. */
@HiltViewModel
class ResetAppViewModel @Inject constructor(
    private val recoveryManager: AppRecoveryManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ResetAppUiState())
    val uiState: StateFlow<ResetAppUiState> = _uiState.asStateFlow()

    fun onConfirmationChange(value: String) {
        _uiState.update { it.copy(confirmation = value.take(MAX_INPUT), failed = false) }
    }

    fun onDismiss() {
        if (!_uiState.value.isErasing) _uiState.value = ResetAppUiState()
    }

    fun erase() {
        if (!_uiState.value.canErase) return
        _uiState.update { it.copy(isErasing = true, failed = false) }
        viewModelScope.launch {
            val erased = try {
                recoveryManager.eraseAllData()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            _uiState.update { it.copy(isErasing = false, isErased = erased, failed = !erased) }
        }
    }

    companion object {
        const val CONFIRMATION_WORD = "APAGAR"
        private const val MAX_INPUT = 20
    }
}
