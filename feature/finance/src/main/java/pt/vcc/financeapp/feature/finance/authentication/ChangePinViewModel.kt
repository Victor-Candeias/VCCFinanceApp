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
import pt.vcc.financeapp.core.security.PinVerification
import pt.vcc.financeapp.core.security.SessionManager

enum class ChangePinStep { CURRENT, NEW, CONFIRM }

data class ChangePinUiState(
    val step: ChangePinStep = ChangePinStep.CURRENT,
    val enteredDigits: Int = 0,
    val error: SetupPinError? = null,
    /** Tentativas restantes depois de errar o PIN atual. */
    val remainingAttempts: Int? = null,
    val isWorking: Boolean = false,
    val isFinished: Boolean = false,
)

/**
 * Alterar PIN: PIN atual → PIN novo → confirmação. O PIN atual é verificado logo no primeiro passo
 * (com o limite de tentativas) e outra vez em [PinManager.changePin] no fim. Se a verificação
 * entrar em bloqueio, a sessão é bloqueada e o ecrã de desbloqueio mostra a espera.
 */
@HiltViewModel
class ChangePinViewModel @Inject constructor(
    private val pinManager: PinManager,
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val buffer = PinBuffer()
    private var currentPin: CharArray? = null
    private var newPin: CharArray? = null

    private val _uiState = MutableStateFlow(ChangePinUiState())
    val uiState: StateFlow<ChangePinUiState> = _uiState.asStateFlow()

    fun onDigit(digit: Char) {
        if (_uiState.value.isWorking) return
        if (buffer.append(digit)) _uiState.update { it.copy(enteredDigits = buffer.length) }
    }

    fun onDelete() {
        if (_uiState.value.isWorking) return
        buffer.removeLast()
        _uiState.update { it.copy(enteredDigits = buffer.length) }
    }

    fun onConfirm() {
        if (_uiState.value.isWorking || buffer.length == 0) return
        val pin = buffer.copy()
        buffer.clear()
        _uiState.update { it.copy(enteredDigits = 0) }
        when (_uiState.value.step) {
            ChangePinStep.CURRENT -> verifyCurrent(pin)
            ChangePinStep.NEW -> acceptNew(pin)
            ChangePinStep.CONFIRM -> confirmNew(pin)
        }
    }

    private fun verifyCurrent(pin: CharArray) {
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            val result = pinManager.verifyPin(pin)
            if (result == PinVerification.Success) {
                currentPin = pin
                _uiState.update {
                    it.copy(step = ChangePinStep.NEW, isWorking = false, error = null, remainingAttempts = null)
                }
            } else {
                pin.fill('\u0000')
                handleFailure(result)
            }
        }
    }

    private fun acceptNew(pin: CharArray) {
        val error = when (PinPolicy.validate(pin)) {
            null -> null
            PinValidationError.TOO_SIMPLE -> SetupPinError.TOO_SIMPLE
            else -> SetupPinError.TOO_SHORT
        }
        if (error != null) {
            pin.fill('\u0000')
            _uiState.update { it.copy(error = error) }
            return
        }
        newPin = pin
        _uiState.update { it.copy(step = ChangePinStep.CONFIRM, error = null) }
    }

    private fun confirmNew(confirmation: CharArray) {
        val pin = newPin ?: return
        val matches = pin.contentEquals(confirmation)
        confirmation.fill('\u0000')
        if (!matches) {
            clear(newPin)
            newPin = null
            _uiState.update { it.copy(step = ChangePinStep.NEW, error = SetupPinError.MISMATCH) }
            return
        }
        val current = currentPin ?: return
        _uiState.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            val result = runCatching { pinManager.changePin(current, pin) }
                .getOrElse {
                    _uiState.update { it.copy(isWorking = false, error = SetupPinError.SAVE_FAILED) }
                    return@launch
                }
            if (result == PinVerification.Success) {
                clearPins()
                _uiState.update { it.copy(isWorking = false, isFinished = true) }
            } else {
                handleFailure(result)
            }
        }
    }

    private fun handleFailure(result: PinVerification) {
        when (result) {
            is PinVerification.Wrong -> {
                clearPins()
                _uiState.update {
                    it.copy(
                        step = ChangePinStep.CURRENT,
                        isWorking = false,
                        remainingAttempts = result.remainingAttempts,
                        error = null,
                    )
                }
            }
            is PinVerification.LockedOut -> {
                clearPins()
                _uiState.update { ChangePinUiState() }
                sessionManager.lock()
            }
            PinVerification.Success -> Unit
        }
    }

    private fun clear(pin: CharArray?) {
        pin?.fill('\u0000')
    }

    private fun clearPins() {
        clear(currentPin)
        clear(newPin)
        currentPin = null
        newPin = null
    }

    override fun onCleared() {
        buffer.clear()
        clearPins()
    }
}
