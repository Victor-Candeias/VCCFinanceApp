package pt.vcc.financeapp.feature.finance.authentication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.security.AutoLockController
import pt.vcc.financeapp.core.security.PinManager
import pt.vcc.financeapp.core.security.SecurityStore
import pt.vcc.financeapp.core.security.SessionManager
import pt.vcc.financeapp.core.security.SessionState

/**
 * Estado da sessão para a raiz da app decidir entre onboarding, desbloqueio e conteúdo. Mantém o
 * [AutoLockController] com o tempo de bloqueio configurado.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    pinManager: PinManager,
    sessionManager: SessionManager,
    securityStore: SecurityStore,
    autoLockController: AutoLockController,
) : ViewModel() {
    val sessionState: StateFlow<SessionState> = sessionManager.state

    init {
        viewModelScope.launch {
            sessionManager.initialize(pinConfigured = pinManager.isPinConfigured.first())
        }
        viewModelScope.launch {
            securityStore.preferences
                .map { it.autoLockTimeoutSeconds }
                .distinctUntilChanged()
                .collect(autoLockController::updateTimeout)
        }
    }
}
