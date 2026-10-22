package pt.vcc.financeapp.feature.finance.authentication

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.financeapp.feature.finance.R

/** Ecrã mostrado quando as chaves de segurança se perderam e os dados já não podem ser lidos. */
@Composable
internal fun RecoveryRequiredScreen() {
    var showReset by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.recovery_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.recovery_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { showReset = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.recovery_erase)) }
        }
    }
    if (showReset) ResetAppDialog(onDismiss = { showReset = false })
}

/** Confirmação forte (escrever [ResetAppViewModel.CONFIRMATION_WORD]) antes de apagar todos os dados. */
@Composable
internal fun ResetAppDialog(onDismiss: () -> Unit, viewModel: ResetAppViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    LaunchedEffect(state.isErased) {
        if (state.isErased && activity != null) restartApp(activity)
    }
    val dismiss = {
        viewModel.onDismiss()
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = { if (!state.isErasing) dismiss() },
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.reset_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.reset_dialog_message))
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.confirmation,
                    onValueChange = viewModel::onConfirmationChange,
                    label = { Text(stringResource(R.string.reset_dialog_prompt, ResetAppViewModel.CONFIRMATION_WORD)) },
                    singleLine = true,
                    enabled = !state.isErasing,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.failed) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.reset_dialog_failed), color = MaterialTheme.colorScheme.error)
                }
                if (state.isErasing) {
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::erase,
                enabled = state.canErase,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.reset_dialog_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = dismiss, enabled = !state.isErasing) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Recomeça a app num processo novo: a base de dados (singleton) ficou fechada e as chaves foram
 * apagadas, por isso nada do processo atual pode ser reutilizado.
 */
private fun restartApp(activity: Activity) {
    val launch = activity.packageManager.getLaunchIntentForPackage(activity.packageName) ?: return
    activity.startActivity(Intent.makeRestartActivityTask(launch.component))
    activity.finishAffinity()
    Runtime.getRuntime().exit(0)
}
