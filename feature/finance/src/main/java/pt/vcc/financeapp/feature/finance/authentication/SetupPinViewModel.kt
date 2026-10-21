package pt.vcc.financeapp.feature.finance.authentication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.security.PinManager
import pt.vcc.financeapp.core.security.PinPolicy
import pt.vcc.financeapp.core.security.PinValidationError
import pt.vcc.financeapp.core.security.SessionManager

enum class SetupStep { WELCOME, CREATE, CONFIRM }

enum class SetupPinError { TOO_SHORT, TOO_SIMPLE, MISMATCH, SAVE_FAILED }

data class SetupPinUiState(
    val step: SetupStep = SetupStep.WELCOME,
    val enteredDigits: Int = 0,
    val error: SetupPinError? = null,
    val isSaving: Boolean = false,
)

@HiltViewModel
class SetupPinViewModel @Inject constructor(
    private val pinManager: PinManager,
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val buffer = PinBuffer()
    private var firstPin: CharArray? = null

    private val _uiState = MutableStateFlow(SetupPinUiState())
    val uiState: StateFlow<SetupPinUiState> = _uiState.asStateFlow()

    fun start() {
        _uiState.update { it.copy(step = SetupStep.CREATE) }
    }

    fun onDigit(digit: Char) {
        if (_uiState.value.isSaving) return
        if (buffer.append(digit)) _uiState.update { it.copy(enteredDigits = buffer.length) }
    }

    fun onDelete() {
        buffer.removeLast()
        _uiState.update { it.copy(enteredDigits = buffer.length) }
    }

    fun onConfirm() {
        val state = _uiState.value
        if (state.isSaving) return
        when (state.step) {
            SetupStep.WELCOME -> Unit
            SetupStep.CREATE -> acceptFirstPin()
            SetupStep.CONFIRM -> acceptConfirmation()
        }
    }

    /** Volta ao passo de criação, descartando o PIN escolhido. */
    fun onBack() {
        if (_uiState.value.step != SetupStep.CONFIRM || _uiState.value.isSaving) return
        resetFirstPin()
        buffer.clear()
        _uiState.update { it.copy(step = SetupStep.CREATE, enteredDigits = 0, error = null) }
    }

    private fun acceptFirstPin() {
        val pin = buffer.copy()
        buffer.clear()
        val error = when (PinPolicy.validate(pin)) {
            null -> null
            PinValidationError.TOO_SIMPLE -> SetupPinError.TOO_SIMPLE
            else -> SetupPinError.TOO_SHORT
        }
        if (error != null) {
            pin.fill('\u0000')
            _uiState.update { it.copy(enteredDigits = 0, error = error) }
            return
        }
        firstPin = pin
        _uiState.update { it.copy(step = SetupStep.CONFIRM, enteredDigits = 0, error = null) }
    }

    private fun acceptConfirmation() {
        val confirmation = buffer.copy()
        buffer.clear()
        val pin = firstPin ?: return
        val matches = pin.contentEquals(confirmation)
        confirmation.fill('\u0000')
        if (!matches) {
            resetFirstPin()
            _uiState.update { it.copy(step = SetupStep.CREATE, enteredDigits = 0, error = SetupPinError.MISMATCH) }
            return
        }
        _uiState.update { it.copy(enteredDigits = 0, error = null, isSaving = true) }
        viewModelScope.launch {
            val saved = runCatching { pinManager.configurePin(pin) }.isSuccess
            resetFirstPin()
            if (saved) {
                sessionManager.unlock()
            } else {
                _uiState.update {
                    it.copy(step = SetupStep.CREATE, isSaving = false, error = SetupPinError.SAVE_FAILED)
                }
            }
        }
    }

    private fun resetFirstPin() {
        firstPin?.fill('\u0000')
        firstPin = null
    }

    override fun onCleared() {
        buffer.clear()
        resetFirstPin()
    }
}
