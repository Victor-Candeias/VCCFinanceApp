package pt.vcc.financeapp.feature.finance.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.Iban
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.DropdownField
import pt.vcc.financeapp.feature.finance.ui.LoadingState
import pt.vcc.financeapp.feature.finance.ui.labelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditorScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmToggle by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.isFinished) { if (state.isFinished) onBack() }

    // Os insets do sistema já são tratados pelo Scaffold da app.
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = {
                    Text(stringResource(if (state.isNew) R.string.account_editor_new_title else R.string.account_editor_edit_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        if (AccountEditorError.NOT_FOUND in state.errors) {
            Text(stringResource(R.string.account_editor_not_found), Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        AccountForm(
            state = state,
            viewModel = viewModel,
            onToggleActive = { confirmToggle = true },
            modifier = Modifier.padding(padding),
        )
    }

    if (confirmToggle) {
        AlertDialog(
            onDismissRequest = { confirmToggle = false },
            title = {
                Text(stringResource(if (state.isActive) R.string.account_deactivate_title else R.string.account_reactivate_title))
            },
            text = {
                Text(stringResource(if (state.isActive) R.string.account_deactivate_message else R.string.account_reactivate_message))
            },
            confirmButton = {
                TextButton(onClick = { confirmToggle = false; viewModel.toggleActive() }) {
                    Text(stringResource(if (state.isActive) R.string.account_deactivate else R.string.account_reactivate))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmToggle = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun AccountForm(
    state: AccountEditorUiState,
    viewModel: AccountEditorViewModel,
    onToggleActive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val nameError = when {
            AccountEditorError.BLANK_NAME in state.errors -> stringResource(R.string.account_error_blank_name)
            AccountEditorError.NAME_TOO_LONG in state.errors -> stringResource(R.string.account_error_name_too_long)
            else -> null
        }
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = { Text(stringResource(R.string.account_field_name)) },
            isError = nameError != null,
            supportingText = nameError?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownField(
            label = stringResource(R.string.account_field_type),
            selectedText = stringResource(state.type.labelRes),
            options = AccountType.entries.map { it to stringResource(it.labelRes) },
            onSelect = viewModel::onTypeChange,
        )
        OutlinedTextField(
            value = state.institutionName,
            onValueChange = viewModel::onInstitutionChange,
            label = { Text(stringResource(R.string.account_field_institution)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val ibanInvalid = AccountEditorError.INVALID_IBAN in state.errors
        OutlinedTextField(
            value = if (state.isIbanRevealed) state.iban else Iban.mask(state.iban),
            onValueChange = viewModel::onIbanChange,
            readOnly = !state.isIbanRevealed,
            label = { Text(stringResource(R.string.account_field_iban)) },
            isError = ibanInvalid,
            supportingText = {
                Text(
                    stringResource(
                        when {
                            ibanInvalid -> R.string.account_error_invalid_iban
                            state.isIbanUnreadable && !state.isIbanChanged -> R.string.account_iban_unreadable
                            else -> R.string.account_iban_hint
                        },
                    ),
                )
            },
            trailingIcon = if (state.isIbanRevealed) null else {
                { TextButton(onClick = viewModel::revealIban) { Text(stringResource(R.string.account_iban_show)) } }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        val currencyError = when {
            AccountEditorError.INVALID_CURRENCY in state.errors -> stringResource(R.string.account_error_invalid_currency)
            AccountEditorError.CURRENCY_LOCKED in state.errors || state.isCurrencyLocked ->
                stringResource(R.string.account_currency_locked)
            else -> null
        }
        OutlinedTextField(
            value = state.currency,
            onValueChange = viewModel::onCurrencyChange,
            label = { Text(stringResource(R.string.account_field_currency)) },
            enabled = !state.isCurrencyLocked,
            isError = AccountEditorError.INVALID_CURRENCY in state.errors || AccountEditorError.CURRENCY_LOCKED in state.errors,
            supportingText = currencyError?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )
        val amountInvalid = AccountEditorError.INVALID_AMOUNT in state.errors
        OutlinedTextField(
            value = state.openingBalance,
            onValueChange = viewModel::onOpeningBalanceChange,
            label = { Text(stringResource(R.string.account_field_opening_balance)) },
            placeholder = { Text("0,00") },
            isError = amountInvalid,
            supportingText = {
                Text(stringResource(if (amountInvalid) R.string.amount_error_invalid else R.string.account_opening_balance_hint))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.account_field_default))
                Text(
                    stringResource(if (state.isActive) R.string.account_default_hint else R.string.account_default_inactive_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.isDefault, onCheckedChange = viewModel::onDefaultChange, enabled = state.isActive)
        }
        Button(onClick = viewModel::save, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_save))
        }
        if (!state.isNew) {
            OutlinedButton(onClick = onToggleActive, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.isActive) R.string.account_deactivate else R.string.account_reactivate))
            }
        }
    }
}
