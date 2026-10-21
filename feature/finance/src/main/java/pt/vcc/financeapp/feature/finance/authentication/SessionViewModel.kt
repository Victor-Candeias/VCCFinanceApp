package pt.vcc.financeapp.feature.finance.authentication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.security.PinManager
import pt.vcc.financeapp.core.security.SessionManager
import pt.vcc.financeapp.core.security.SessionState

/** Estado da sessão para a raiz da app decidir entre onboarding, desbloqueio e conteúdo. */
@HiltViewModel
class SessionViewModel @Inject constructor(
    pinManager: PinManager,
    sessionManager: SessionManager,
) : ViewModel() {
    val sessionState: StateFlow<SessionState> = sessionManager.state

    init {
        viewModelScope.launch {
            sessionManager.initialize(pinConfigured = pinManager.isPinConfigured.first())
        }
    }
}
