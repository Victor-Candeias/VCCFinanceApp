package pt.vcc.financeapp.feature.finance.authentication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.security.PinManager
import pt.vcc.financeapp.core.security.PinVerification
import pt.vcc.financeapp.core.security.SessionManager

data class UnlockUiState(
    val enteredDigits: Int = 0,
    /** Tentativas restantes depois de um PIN errado; `null` sem erro a mostrar. */
    val remainingAttempts: Int? = null,
    /** Segundos até ser possível tentar de novo; 0 sem bloqueio. */
    val lockoutSeconds: Long = 0,
    val isVerifying: Boolean = false,
) {
    val canEnterPin: Boolean get() = !isVerifying && lockoutSeconds == 0L
}

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val pinManager: PinManager,
    private val sessionManager: SessionManager,
    private val clock: Clock,
) : ViewModel() {
    private val buffer = PinBuffer()
    private var countdown: Job? = null

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { pinManager.lockoutEnd()?.let(::startLockout) }
    }

    fun onDigit(digit: Char) {
        if (!_uiState.value.canEnterPin) return
        if (buffer.append(digit)) _uiState.update { it.copy(enteredDigits = buffer.length) }
    }

    fun onDelete() {
        if (!_uiState.value.canEnterPin) return
        buffer.removeLast()
        _uiState.update { it.copy(enteredDigits = buffer.length) }
    }

    fun onConfirm() {
        if (!_uiState.value.canEnterPin || buffer.length == 0) return
        val pin = buffer.copy()
        buffer.clear()
        _uiState.update { it.copy(enteredDigits = 0, isVerifying = true) }
        viewModelScope.launch {
            val result = try {
                pinManager.verifyPin(pin)
            } finally {
                pin.fill('\u0000')
            }
            _uiState.update { it.copy(isVerifying = false) }
            when (result) {
                PinVerification.Success -> {
                    _uiState.update { it.copy(remainingAttempts = null) }
                    sessionManager.unlock()
                }
                is PinVerification.Wrong -> _uiState.update { it.copy(remainingAttempts = result.remainingAttempts) }
                is PinVerification.LockedOut -> startLockout(result.until)
            }
        }
    }

    /** Contagem decrescente feita com `delay`, para não depender de o relógio avançar nos testes. */
    private fun startLockout(until: Instant) {
        val millis = until.toEpochMilli() - clock.millis()
        if (millis <= 0) return
        countdown?.cancel()
        countdown = viewModelScope.launch {
            var seconds = (millis + 999) / 1000
            while (seconds > 0) {
                _uiState.update { it.copy(lockoutSeconds = seconds, remainingAttempts = null) }
                delay(1_000)
                seconds--
            }
            _uiState.update { it.copy(lockoutSeconds = 0) }
        }
    }

    override fun onCleared() {
        buffer.clear()
    }
}
