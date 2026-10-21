package pt.vcc.financeapp.feature.finance.transactions

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import pt.vcc.financeapp.core.data.usecase.SaveTransactionUseCase
import pt.vcc.financeapp.core.model.TransactionStatus
import pt.vcc.financeapp.core.model.TransactionType
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.DateField
import pt.vcc.financeapp.feature.finance.ui.DropdownField
import pt.vcc.financeapp.feature.finance.ui.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.isFinished) { if (state.isFinished) onBack() }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = {
                    Text(stringResource(if (state.isNew) R.string.transaction_editor_new_title else R.string.transaction_editor_edit_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (!state.isNew && !state.isTransfer && !state.isLoading) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            TransactionEditorError.NOT_FOUND in state.errors ->
                Text(stringResource(R.string.transaction_editor_not_found), Modifier.padding(padding).padding(16.dp))
            state.isTransfer ->
                Text(stringResource(R.string.transaction_editor_transfer_readonly), Modifier.padding(padding).padding(16.dp))
            state.accounts.isEmpty() ->
                Text(stringResource(R.string.transaction_editor_no_accounts), Modifier.padding(padding).padding(16.dp))
            else -> TransactionForm(state, viewModel, Modifier.padding(padding))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.transaction_delete_title)) },
            text = { Text(stringResource(R.string.transaction_delete_message)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionForm(
    state: TransactionEditorUiState,
    viewModel: TransactionEditorViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                TransactionType.EXPENSE to R.string.transaction_type_expense,
                TransactionType.INCOME to R.string.transaction_type_income,
            ).forEach { (type, label) ->
                FilterChip(
                    selected = state.type == type,
                    onClick = { viewModel.onTypeChange(type) },
                    label = { Text(stringResource(label)) },
                )
            }
        }

        val accountError = when {
            TransactionEditorError.NO_ACCOUNT in state.errors -> stringResource(R.string.transaction_error_no_account)
            TransactionEditorError.ACCOUNT_INACTIVE in state.errors -> stringResource(R.string.transaction_error_account_inactive)
            else -> null
        }
        DropdownField(
            label = stringResource(R.string.transaction_field_account),
            selectedText = state.selectedAccount?.name ?: stringResource(R.string.transaction_choose_account),
            options = state.accounts.map { it.id to "${it.name} (${it.currency})" },
            onSelect = viewModel::onAccountChange,
            supportingText = accountError,
            isError = accountError != null,
        )

        val amountInvalid = TransactionEditorError.INVALID_AMOUNT in state.errors
        OutlinedTextField(
            value = state.amount,
            onValueChange = viewModel::onAmountChange,
            label = { Text(stringResource(R.string.transaction_field_amount)) },
            placeholder = { Text("0,00") },
            suffix = { Text(state.selectedAccount?.currency.orEmpty()) },
            isError = amountInvalid,
            supportingText = if (amountInvalid) {
                { Text(stringResource(R.string.transaction_error_amount)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        val descriptionError = when {
            TransactionEditorError.BLANK_DESCRIPTION in state.errors -> stringResource(R.string.transaction_error_blank_description)
            TransactionEditorError.DESCRIPTION_TOO_LONG in state.errors ->
                stringResource(R.string.transaction_error_description_too_long, SaveTransactionUseCase.MAX_DESCRIPTION_LENGTH)
            else -> null
        }
        OutlinedTextField(
            value = state.description,
            onValueChange = viewModel::onDescriptionChange,
            label = { Text(stringResource(R.string.transaction_field_description)) },
            isError = descriptionError != null,
            supportingText = descriptionError?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        DateField(stringResource(R.string.transaction_field_date), state.date, viewModel::onDateChange)

        val noCategory = stringResource(R.string.transaction_no_category)
        DropdownField(
            label = stringResource(R.string.transaction_field_category),
            selectedText = state.categories.find { it.id == state.categoryId }?.label ?: noCategory,
            options = listOf<Pair<String?, String>>(null to noCategory) + state.categoriesForType.map { it.id to it.label },
            onSelect = viewModel::onCategoryChange,
            supportingText = if (TransactionEditorError.CATEGORY_MISMATCH in state.errors) {
                stringResource(R.string.transaction_error_category)
            } else {
                null
            },
            isError = TransactionEditorError.CATEGORY_MISMATCH in state.errors,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.transaction_field_pending))
                Text(
                    stringResource(R.string.transaction_pending_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.status == TransactionStatus.PENDING, onCheckedChange = viewModel::onPendingChange)
        }

        OutlinedTextField(
            value = state.notes,
            onValueChange = viewModel::onNotesChange,
            label = { Text(stringResource(R.string.transaction_field_notes)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(onClick = viewModel::save, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_save))
        }
    }
}
