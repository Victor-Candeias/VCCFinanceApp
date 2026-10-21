package pt.vcc.financeapp.core.security

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SessionState {
    /** Ainda não se sabe se há PIN configurado. */
    INITIALIZING,

    /** Primeiro arranque: é preciso criar o PIN. */
    SETUP_REQUIRED,
    LOCKED,
    UNLOCKED,
}

/** Estado da sessão da app; os dados financeiros só são mostrados em [SessionState.UNLOCKED]. */
@Singleton
class SessionManager @Inject constructor() {
    private val _state = MutableStateFlow(SessionState.INITIALIZING)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Define o estado inicial em função de haver PIN; não altera uma sessão já iniciada. */
    fun initialize(pinConfigured: Boolean) {
        _state.compareAndSet(
            SessionState.INITIALIZING,
            if (pinConfigured) SessionState.LOCKED else SessionState.SETUP_REQUIRED,
        )
    }

    fun unlock() {
        _state.value = SessionState.UNLOCKED
    }

    fun lock() {
        if (_state.value == SessionState.UNLOCKED) _state.value = SessionState.LOCKED
    }
}
