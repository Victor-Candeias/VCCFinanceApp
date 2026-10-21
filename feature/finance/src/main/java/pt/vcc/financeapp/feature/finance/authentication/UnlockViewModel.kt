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
import pt.vcc.financeapp.core.security.SessionManager

data class UnlockUiState(
    val enteredDigits: Int = 0,
    val wrongPin: Boolean = false,
    val isVerifying: Boolean = false,
)

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val pinManager: PinManager,
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val buffer = PinBuffer()

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    fun onDigit(digit: Char) {
        if (_uiState.value.isVerifying) return
        if (buffer.append(digit)) _uiState.update { it.copy(enteredDigits = buffer.length, wrongPin = false) }
    }

    fun onDelete() {
        if (_uiState.value.isVerifying) return
        buffer.removeLast()
        _uiState.update { it.copy(enteredDigits = buffer.length) }
    }

    fun onConfirm() {
        if (_uiState.value.isVerifying || buffer.length == 0) return
        val pin = buffer.copy()
        buffer.clear()
        _uiState.update { it.copy(enteredDigits = 0, isVerifying = true) }
        viewModelScope.launch {
            val valid = try {
                pinManager.verifyPin(pin)
            } finally {
                pin.fill('\u0000')
            }
            if (valid) {
                sessionManager.unlock()
                _uiState.update { it.copy(isVerifying = false) }
            } else {
                _uiState.update { it.copy(isVerifying = false, wrongPin = true) }
            }
        }
    }

    override fun onCleared() {
        buffer.clear()
    }
}
