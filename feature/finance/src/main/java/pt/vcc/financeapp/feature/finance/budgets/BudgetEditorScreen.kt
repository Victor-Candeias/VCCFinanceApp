package pt.vcc.financeapp.feature.finance.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.financeapp.core.data.usecase.BudgetValidationError
import pt.vcc.financeapp.core.model.BudgetPeriod
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.DropdownField
import pt.vcc.financeapp.feature.finance.ui.LoadingState
import pt.vcc.financeapp.feature.finance.ui.labelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetEditorScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BudgetEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.isFinished) { if (state.isFinished) onBack() }

    // Os insets do sistema já são tratados pelo Scaffold da app.
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(if (state.isNew) R.string.budget_editor_new_title else R.string.budget_editor_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            BudgetValidationError.NOT_FOUND in state.errors ->
                Text(stringResource(R.string.budget_not_found), Modifier.padding(padding).padding(16.dp))
            else -> BudgetForm(state, viewModel, onDelete = { confirmDelete = true }, modifier = Modifier.padding(padding))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.budget_delete_title)) },
            text = { Text(stringResource(R.string.budget_delete_message)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun BudgetForm(
    state: BudgetEditorUiState,
    viewModel: BudgetEditorViewModel,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val nameError = when {
            BudgetValidationError.BLANK_NAME in state.errors -> stringResource(R.string.budget_error_blank_name)
            BudgetValidationError.NAME_TOO_LONG in state.errors -> stringResource(R.string.budget_error_name_too_long)
            else -> null
        }
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = { Text(stringResource(R.string.budget_field_name)) },
            isError = nameError != null,
            supportingText = nameError?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        val allExpenses = stringResource(R.string.budget_all_expenses)
        val categoryInvalid = BudgetValidationError.INVALID_CATEGORY in state.errors
        DropdownField(
            label = stringResource(R.string.budget_field_category),
            selectedText = state.categories.find { it.id == state.categoryId }?.label ?: allExpenses,
            options = listOf<Pair<String?, String>>(null to allExpenses) + state.categories.map { it.id to it.label },
            onSelect = viewModel::onCategoryChange,
            supportingText = stringResource(if (categoryInvalid) R.string.budget_error_category else R.string.budget_category_hint),
            isError = categoryInvalid,
        )
        DropdownField(
            label = stringResource(R.string.budget_field_period),
            selectedText = stringResource(state.period.labelRes),
            options = BudgetPeriod.entries.map { it to stringResource(it.labelRes) },
            onSelect = viewModel::onPeriodChange,
        )
        val amountInvalid = BudgetValidationError.INVALID_AMOUNT in state.errors
        OutlinedTextField(
            value = state.limit,
            onValueChange = viewModel::onLimitChange,
            label = { Text(stringResource(R.string.budget_field_limit)) },
            placeholder = { Text("0,00") },
            isError = amountInvalid,
            supportingText = if (amountInvalid) {
                { Text(stringResource(R.string.budget_error_amount)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        val currencyInvalid = BudgetValidationError.INVALID_CURRENCY in state.errors
        OutlinedTextField(
            value = state.currency,
            onValueChange = viewModel::onCurrencyChange,
            label = { Text(stringResource(R.string.budget_field_currency)) },
            isError = currencyInvalid,
            supportingText = if (currencyInvalid) {
                { Text(stringResource(R.string.account_error_invalid_currency)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )
        val thresholdInvalid = BudgetValidationError.INVALID_THRESHOLD in state.errors
        OutlinedTextField(
            value = state.alertThreshold,
            onValueChange = viewModel::onAlertThresholdChange,
            label = { Text(stringResource(R.string.budget_field_threshold)) },
            isError = thresholdInvalid,
            supportingText = {
                Text(stringResource(if (thresholdInvalid) R.string.budget_error_threshold else R.string.budget_threshold_hint))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = viewModel::save, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_save))
        }
        if (!state.isNew) {
            OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.budget_delete))
            }
        }
    }
}
