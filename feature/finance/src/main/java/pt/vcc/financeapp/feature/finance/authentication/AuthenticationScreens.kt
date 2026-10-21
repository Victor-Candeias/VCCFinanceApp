package pt.vcc.financeapp.feature.finance.authentication

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.financeapp.core.security.BiometricPromptTexts
import pt.vcc.financeapp.core.security.SessionState
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.LoadingState

/**
 * Mostra [content] apenas com a sessão desbloqueada; antes disso mostra o onboarding (primeiro
 * arranque) ou o ecrã de desbloqueio.
 */
@Composable
fun AuthenticationGate(
    viewModel: SessionViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.sessionState.collectAsStateWithLifecycle()
    // O conteúdo sai da composição enquanto bloqueado; o estado guardável (navegação) é restaurado ao desbloquear.
    val stateHolder = rememberSaveableStateHolder()
    when (state) {
        SessionState.INITIALIZING -> Surface(Modifier.fillMaxSize()) { LoadingState() }
        SessionState.SETUP_REQUIRED -> SetupPinScreen()
        SessionState.LOCKED -> UnlockScreen()
        SessionState.UNLOCKED -> stateHolder.SaveableStateProvider(UNLOCKED_CONTENT_KEY) { content() }
    }
}

private const val UNLOCKED_CONTENT_KEY = "unlocked-content"

@Composable
internal fun SetupPinScreen(viewModel: SetupPinViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.step == SetupStep.WELCOME) {
        WelcomeContent(onStart = viewModel::start)
        return
    }
    BackHandler(enabled = state.step == SetupStep.CONFIRM) { viewModel.onBack() }
    val creating = state.step == SetupStep.CREATE
    PinEntryLayout(
        title = stringResource(if (creating) R.string.pin_setup_create_title else R.string.pin_setup_confirm_title),
        subtitle = stringResource(
            if (creating) R.string.pin_setup_create_subtitle else R.string.pin_setup_confirm_subtitle,
        ),
        enteredDigits = state.enteredDigits,
        message = state.error?.let { stringResource(it.messageRes()) },
        enabled = !state.isSaving,
        onDigit = viewModel::onDigit,
        onDelete = viewModel::onDelete,
        onConfirm = viewModel::onConfirm,
        footer = {
            if (state.isSaving) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            }
        },
    )
}

@Composable
private fun WelcomeContent(onStart: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.onboarding_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onStart) { Text(stringResource(R.string.onboarding_start)) }
        }
    }
}

@Composable
internal fun UnlockScreen(viewModel: UnlockViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    val texts = biometricPromptTexts(R.string.biometric_unlock_subtitle)
    LaunchedEffect(Unit) { viewModel.onShown() }
    LaunchedEffect(state.biometricPromptPending) {
        if (state.biometricPromptPending && activity != null) viewModel.authenticateWithBiometric(activity, texts)
    }
    PinEntryLayout(
        title = stringResource(R.string.unlock_title),
        subtitle = stringResource(R.string.unlock_subtitle),
        enteredDigits = state.enteredDigits,
        message = unlockMessage(state.lockoutSeconds, state.remainingAttempts)
            ?: if (state.biometricInvalidated) stringResource(R.string.biometric_invalidated) else null,
        enabled = state.canEnterPin,
        onDigit = viewModel::onDigit,
        onDelete = viewModel::onDelete,
        onConfirm = viewModel::onConfirm,
        footer = {
            if (state.isVerifying) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            } else if (state.canUseBiometric) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = viewModel::onBiometricRequested) {
                    Text(stringResource(R.string.biometric_use))
                }
            }
        },
    )
}

@Composable
internal fun biometricPromptTexts(subtitleRes: Int) = BiometricPromptTexts(
    title = stringResource(R.string.biometric_prompt_title),
    subtitle = stringResource(subtitleRes),
    negativeButton = stringResource(R.string.biometric_use_pin),
)

@Composable
internal fun unlockMessage(lockoutSeconds: Long, remainingAttempts: Int?): String? = when {
    lockoutSeconds > 0 -> pluralStringResource(R.plurals.pin_locked_out, lockoutSeconds.toInt(), lockoutSeconds.toInt())
    remainingAttempts == null -> null
    remainingAttempts == 0 -> stringResource(R.string.pin_wrong_last_attempt)
    else -> pluralStringResource(R.plurals.pin_wrong_remaining, remainingAttempts, remainingAttempts)
}

internal fun SetupPinError.messageRes(): Int = when (this) {
    SetupPinError.TOO_SHORT -> R.string.pin_error_length
    SetupPinError.TOO_SIMPLE -> R.string.pin_error_too_simple
    SetupPinError.MISMATCH -> R.string.pin_error_mismatch
    SetupPinError.SAVE_FAILED -> R.string.pin_error_save_failed
}
