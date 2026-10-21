package pt.vcc.financeapp.feature.finance.authentication

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.security.BiometricAuthenticator
import pt.vcc.financeapp.core.security.BiometricAvailability
import pt.vcc.financeapp.core.security.BiometricPromptTexts
import pt.vcc.financeapp.core.security.BiometricResult
import pt.vcc.financeapp.core.security.SecurityPreferences
import pt.vcc.financeapp.core.security.SecurityStore

enum class BiometricNotice { ENROLL_FAILED }

data class SecuritySettingsUiState(
    val biometricEnabled: Boolean = false,
    val biometricAvailability: BiometricAvailability = BiometricAvailability.UNAVAILABLE,
    val autoLockSeconds: Int = SecurityPreferences.DEFAULT_AUTO_LOCK_SECONDS,
    val enrollPending: Boolean = false,
    val notice: BiometricNotice? = null,
)

@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    private val securityStore: SecurityStore,
    private val biometricAuthenticator: BiometricAuthenticator,
) : ViewModel() {
    private val local = MutableStateFlow(SecuritySettingsUiState(biometricAvailability = biometricAuthenticator.availability()))

    val uiState: StateFlow<SecuritySettingsUiState> =
        combine(securityStore.preferences, local) { prefs, state ->
            state.copy(biometricEnabled = prefs.biometricEnabled, autoLockSeconds = prefs.autoLockTimeoutSeconds)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    /** Atualiza a disponibilidade (o utilizador pode ter registado biometrias entretanto). */
    fun onShown() {
        local.update { it.copy(biometricAvailability = biometricAuthenticator.availability()) }
    }

    fun onBiometricToggled(enabled: Boolean) {
        if (enabled) {
            if (local.value.biometricAvailability == BiometricAvailability.AVAILABLE) {
                local.update { it.copy(enrollPending = true, notice = null) }
            }
        } else {
            biometricAuthenticator.disable()
            viewModelScope.launch { securityStore.update { it.copy(biometricEnabled = false) } }
        }
    }

    /** Ativar exige uma autenticação biométrica bem-sucedida com a chave nova. */
    fun enrollBiometric(activity: FragmentActivity, texts: BiometricPromptTexts) {
        if (!local.value.enrollPending) return
        local.update { it.copy(enrollPending = false) }
        biometricAuthenticator.enroll(activity, texts, ::onEnrollResult)
    }

    fun onEnrollResult(result: BiometricResult) {
        when (result) {
            BiometricResult.Success -> viewModelScope.launch { securityStore.update { it.copy(biometricEnabled = true) } }
            BiometricResult.Cancelled -> Unit
            BiometricResult.KeyInvalidated, is BiometricResult.Error ->
                local.update { it.copy(notice = BiometricNotice.ENROLL_FAILED) }
        }
    }

    fun onAutoLockSelected(seconds: Int) {
        if (seconds !in SecurityPreferences.AUTO_LOCK_OPTIONS_SECONDS) return
        viewModelScope.launch { securityStore.update { it.copy(autoLockTimeoutSeconds = seconds) } }
    }
}
