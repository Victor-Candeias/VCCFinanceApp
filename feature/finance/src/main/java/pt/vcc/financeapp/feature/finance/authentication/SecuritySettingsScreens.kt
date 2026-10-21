package pt.vcc.financeapp.feature.finance.authentication

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.financeapp.feature.finance.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecurityScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    // Os insets do sistema já são tratados pelo Scaffold da app.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding -> content(Modifier.padding(padding)) }
}

/** Definições de segurança (entrada "Segurança" do menu Mais). */
@Composable
fun SecuritySettingsScreen(onBack: () -> Unit, onChangePin: () -> Unit) {
    SecurityScaffold(title = stringResource(R.string.security_title), onBack = onBack) { modifier ->
        LazyColumn(modifier) {
            item(key = "change_pin") {
                ListItem(
                    modifier = Modifier.clickable(onClick = onChangePin),
                    headlineContent = { Text(stringResource(R.string.security_change_pin)) },
                    supportingContent = { Text(stringResource(R.string.security_change_pin_description)) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun ChangePinScreen(onBack: () -> Unit, viewModel: ChangePinViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val doneMessage = stringResource(R.string.change_pin_done)
    LaunchedEffect(state.isFinished) {
        if (state.isFinished) {
            Toast.makeText(context, doneMessage, Toast.LENGTH_SHORT).show()
            onBack()
        }
    }
    val (title, subtitle) = when (state.step) {
        ChangePinStep.CURRENT -> R.string.change_pin_current_title to R.string.change_pin_current_subtitle
        ChangePinStep.NEW -> R.string.change_pin_new_title to R.string.pin_setup_create_subtitle
        ChangePinStep.CONFIRM -> R.string.change_pin_confirm_title to R.string.pin_setup_confirm_subtitle
    }
    SecurityScaffold(title = stringResource(R.string.security_change_pin), onBack = onBack) { modifier ->
        PinEntryLayout(
            title = stringResource(title),
            subtitle = stringResource(subtitle),
            enteredDigits = state.enteredDigits,
            message = state.error?.let { stringResource(it.messageRes()) }
                ?: unlockMessage(lockoutSeconds = 0, remainingAttempts = state.remainingAttempts),
            enabled = !state.isWorking,
            onDigit = viewModel::onDigit,
            onDelete = viewModel::onDelete,
            onConfirm = viewModel::onConfirm,
            modifier = modifier,
            applySystemInsets = false,
        )
    }
}
