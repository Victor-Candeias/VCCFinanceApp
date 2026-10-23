package pt.vcc.financeapp.feature.finance.purchases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.financeapp.core.data.usecase.CompletePurchaseError
import pt.vcc.financeapp.core.data.usecase.PlannedProgress
import pt.vcc.financeapp.core.data.usecase.PurchaseItemError
import pt.vcc.financeapp.core.designsystem.components.EmptyState
import pt.vcc.financeapp.core.model.Merchant
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.PurchaseItem
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.core.model.ShoppingListItem
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.DateField
import pt.vcc.financeapp.feature.finance.ui.DropdownField
import pt.vcc.financeapp.feature.finance.ui.LoadingState
import pt.vcc.financeapp.feature.finance.ui.formatAmountInput
import pt.vcc.financeapp.feature.finance.ui.formatted

/** Compra rápida ou planeada: editor de itens no topo (nunca tapado pelo teclado), itens e total. */
@Composable
fun PurchaseScreen(
    onBack: () -> Unit,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PurchaseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val removedMessage = stringResource(R.string.purchase_item_removed)
    val undo = stringResource(R.string.action_undo)

    LaunchedEffect(state.isClosed) { if (state.isClosed) onClosed() }
    LaunchedEffect(state.removedItem) {
        if (state.removedItem != null) {
            val result = snackbar.showSnackbar(removedMessage, actionLabel = undo, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.onUndoRemove() else viewModel.onRemovedShown()
        }
    }

    val purchase = state.purchase
    val title = purchase?.let { stringResource(it.type.labelRes) } ?: stringResource(R.string.purchases_title)
    PurchaseScaffold(
        title = state.listName ?: title,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbar,
        actions = {
            if (purchase?.status == PurchaseStatus.ACTIVE) {
                IconButton(onClick = { viewModel.onShowDialog(PurchaseDialog.CANCEL) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.purchase_cancel))
                }
            }
        },
        bottomBar = {
            if (purchase != null) TotalBar(state, onFinish = viewModel::onStartFinish)
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.notFound || purchase == null -> EmptyState(
                title = stringResource(R.string.purchases_title),
                message = stringResource(R.string.purchase_not_found),
                modifier = Modifier.padding(padding),
            )
            else -> PurchaseContent(state, viewModel, Modifier.padding(padding))
        }
    }

    when (state.dialog) {
        PurchaseDialog.MERCHANT -> MerchantPickerDialog(state.merchants, state.merchantName, viewModel::onMerchantChosen) {
            viewModel.onShowDialog(null)
        }
        PurchaseDialog.BUDGET -> BudgetDialog(state.purchase?.budgetMinor, viewModel::onBudgetChosen) { viewModel.onShowDialog(null) }
        PurchaseDialog.CANCEL -> AlertDialog(
            onDismissRequest = { viewModel.onShowDialog(null) },
            title = { Text(stringResource(R.string.purchase_cancel)) },
            text = { Text(stringResource(R.string.purchase_cancel_message)) },
            confirmButton = { TextButton(onClick = viewModel::onConfirmCancel) { Text(stringResource(R.string.purchase_cancel_confirm)) } },
            dismissButton = { TextButton(onClick = { viewModel.onShowDialog(null) }) { Text(stringResource(R.string.purchase_cancel_keep)) } },
        )
        null -> Unit
    }
    if (state.finish != null) FinishDialog(state, viewModel)
}

@Composable
private fun PurchaseContent(state: PurchaseUiState, viewModel: PurchaseViewModel, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { HeaderChips(state, viewModel) }
        state.remaining?.let { remaining ->
            item(key = "budget") { BudgetProgress(state.total, state.budget!!, remaining) }
        }
        item(key = "editor") { ItemEditor(state, viewModel) }
        val planned = state.planned
        if (planned != null) {
            item(key = "planned-progress") { PlannedProgressCard(planned, state) }
            if (planned.toBuy.isNotEmpty()) {
                item(key = "to-buy-title") { SectionTitle(stringResource(R.string.purchase_list_pending, planned.toBuy.size)) }
                items(planned.toBuy, key = { "to-buy-${it.id}" }) { ListLine(it, skipped = false, state.currency, viewModel) }
            }
            if (planned.skipped.isNotEmpty()) {
                item(key = "skipped-title") { SectionTitle(stringResource(R.string.purchase_list_skipped_title, planned.skipped.size)) }
                items(planned.skipped, key = { "skipped-${it.id}" }) { ListLine(it, skipped = true, state.currency, viewModel) }
            }
            item(key = "added-title") { SectionTitle(stringResource(R.string.purchase_list_added, planned.added.size)) }
            items(planned.added, key = { it.id }) { item -> PurchaseItemRow(item, state.currency, viewModel) }
            item(key = "extras-title") { SectionTitle(stringResource(R.string.purchase_list_extras, planned.extras.size)) }
            items(planned.extras, key = { it.id }) { item -> PurchaseItemRow(item, state.currency, viewModel) }
            return@LazyColumn
        }
        item(key = "items-title") { SectionTitle(stringResource(R.string.purchase_items_title, state.items.size)) }
        if (state.items.isEmpty()) {
            item(key = "items-empty") {
                Text(
                    stringResource(R.string.purchase_items_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.items, key = { it.id }) { item -> PurchaseItemRow(item, state.currency, viewModel) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderChips(state: PurchaseUiState, viewModel: PurchaseViewModel) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = { viewModel.onShowDialog(PurchaseDialog.MERCHANT) },
            label = { Text(state.merchantName ?: stringResource(R.string.purchase_choose_merchant)) },
            leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
        )
        AssistChip(
            onClick = { viewModel.onShowDialog(PurchaseDialog.BUDGET) },
            label = {
                Text(state.budget?.let { stringResource(R.string.purchase_budget_value, it.formatted()) } ?: stringResource(R.string.purchase_set_budget))
            },
        )
    }
}

@Composable
private fun BudgetProgress(total: Money, budget: Money, remaining: Money) {
    val exceeded = remaining.amountMinor < 0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { if (budget.amountMinor == 0L) 1f else (total.amountMinor.toFloat() / budget.amountMinor).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = if (exceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Text(
            if (exceeded) {
                stringResource(R.string.purchase_budget_exceeded, Money(-remaining.amountMinor, remaining.currency).formatted())
            } else {
                stringResource(R.string.purchase_budget_remaining, remaining.formatted())
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (exceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemEditor(state: PurchaseUiState, viewModel: PurchaseViewModel) {
    val editor = state.editor
    fun error(vararg codes: PurchaseItemError) = codes.firstOrNull { it in editor.errors }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (editor.isEditing) R.string.purchase_item_edit else R.string.purchase_item_add),
                style = MaterialTheme.typography.titleSmall,
            )
            val nameError = error(PurchaseItemError.BLANK_NAME, PurchaseItemError.NAME_TOO_LONG)
            OutlinedTextField(
                value = editor.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.purchase_item_name)) },
                singleLine = true,
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it.message()) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )
            if (state.suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.suggestions.forEach { product ->
                        SuggestionChip(onClick = { viewModel.onProductSelected(product) }, label = { Text(product.name) })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val priceError = error(PurchaseItemError.INVALID_PRICE)
                OutlinedTextField(
                    value = editor.price,
                    onValueChange = viewModel::onPriceChange,
                    modifier = Modifier.weight(1.4f),
                    label = { Text(stringResource(R.string.purchase_item_price)) },
                    singleLine = true,
                    isError = priceError != null,
                    supportingText = priceError?.let { { Text(it.message()) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )
                val quantityError = error(PurchaseItemError.INVALID_QUANTITY)
                OutlinedTextField(
                    value = editor.quantity,
                    onValueChange = { value -> viewModel.updateEditor { it.copy(quantity = value) } },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.purchase_item_quantity)) },
                    singleLine = true,
                    isError = quantityError != null,
                    supportingText = quantityError?.let { { Text(it.message()) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = editor.unit,
                    onValueChange = { value -> viewModel.updateEditor { it.copy(unit = value) } },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.purchase_item_unit_short)) },
                    singleLine = true,
                )
                val discountError = error(PurchaseItemError.INVALID_DISCOUNT)
                OutlinedTextField(
                    value = editor.discount,
                    onValueChange = { value -> viewModel.updateEditor { it.copy(discount = value) } },
                    modifier = Modifier.weight(1.4f),
                    label = { Text(stringResource(R.string.purchase_item_discount)) },
                    singleLine = true,
                    isError = discountError != null,
                    supportingText = discountError?.let { { Text(it.message()) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                )
            }
            error(PurchaseItemError.PURCHASE_CANCELLED, PurchaseItemError.PURCHASE_NOT_FOUND)?.let {
                Text(it.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    state.editorTotal?.let { stringResource(R.string.purchase_item_total, it.formatted()) }.orEmpty(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (editor.isEditing || editor.name.isNotEmpty()) {
                    OutlinedButton(onClick = viewModel::onClearEditor) { Text(stringResource(R.string.action_cancel)) }
                }
                Button(onClick = viewModel::onSaveItem) {
                    Text(stringResource(if (editor.isEditing) R.string.action_save else R.string.purchase_item_add_button))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun PlannedProgressCard(planned: PlannedProgress, state: PurchaseUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.purchase_list_progress, planned.handledCount, planned.plannedCount))
            LinearProgressIndicator(
                progress = { if (planned.plannedCount == 0) 1f else planned.handledCount.toFloat() / planned.plannedCount },
                modifier = Modifier.fillMaxWidth(),
            )
            if (planned.estimateMinor > 0) {
                EstimateText(planned.estimateMinor, state)
            }
        }
    }
}

/** Estimativa da lista face ao total real da compra. */
@Composable
private fun EstimateText(estimateMinor: Long, state: PurchaseUiState) {
    val difference = state.total.amountMinor - estimateMinor
    val text = stringResource(R.string.purchase_list_estimate, Money(estimateMinor, state.currency).formatted()) + " · " +
        stringResource(
            if (difference > 0) R.string.purchase_list_above_estimate else R.string.purchase_list_below_estimate,
            Money(kotlin.math.abs(difference), state.currency).formatted(),
        )
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (difference > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ListLine(item: ShoppingListItem, skipped: Boolean, currency: String, viewModel: PurchaseViewModel) {
    ListItem(
        modifier = Modifier.clickable(enabled = !skipped) { viewModel.onListItemSelected(item) },
        headlineContent = {
            Text(item.name, color = if (skipped) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
        },
        supportingContent = {
            Text(
                listOfNotNull(
                    "${item.quantity.formattedQuantity()} ${item.unit.orEmpty()}".trim(),
                    item.estimatedPriceMinor?.let { Money(it, currency).formatted() },
                ).joinToString(" · "),
            )
        },
        trailingContent = {
            TextButton(onClick = { viewModel.onSkipListItem(item, !skipped) }) {
                Text(stringResource(if (skipped) R.string.purchase_list_restore else R.string.purchase_list_skip))
            }
        },
    )
}

@Composable
private fun PurchaseItemRow(item: PurchaseItem, currency: String, viewModel: PurchaseViewModel) {
    val price = Money(item.unitPriceMinor, currency).formatted()
    val detail = buildString {
        append(item.quantity.formattedQuantity())
        item.unit?.let { append(' ').append(it) }
        append(" × ").append(price)
        if (item.discountMinor > 0) append(" − ").append(Money(item.discountMinor, currency).formatted())
    }
    ListItem(
        modifier = Modifier.clickable { viewModel.onEditItem(item) },
        headlineContent = { Text(item.name) },
        supportingContent = { Text(listOfNotNull(detail, item.categoryName).joinToString(" · ")) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Money(item.totalMinor, currency).formatted(), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { viewModel.onRemoveItem(item) }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.purchase_item_remove, item.name))
                }
            }
        },
    )
    HorizontalDivider()
}

@Composable
private fun TotalBar(state: PurchaseUiState, onFinish: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.purchase_total), style = MaterialTheme.typography.labelMedium)
                Text(state.total.formatted(), style = MaterialTheme.typography.headlineSmall)
            }
            Button(onClick = onFinish, enabled = state.items.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (state.isCompleted) R.string.purchase_save_changes else R.string.purchase_finish))
            }
        }
    }
}

@Composable
private fun MerchantPickerDialog(merchants: List<Merchant>, current: String?, onChosen: (String?) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current.orEmpty()) }
    val key = Merchant.normalize(name)
    val matches = merchants.filter { key.isEmpty() || key in it.normalizedName }.take(8)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.merchants_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                matches.forEach { merchant ->
                    ListItem(
                        modifier = Modifier.clickable { onChosen(merchant.name) },
                        headlineContent = { Text(merchant.name) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onChosen(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onChosen(null) }) { Text(stringResource(R.string.purchase_remove_merchant)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
private fun BudgetDialog(current: Long?, onChosen: (String) -> Boolean, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current?.let { formatAmountInput(it, 2) }.orEmpty()) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.purchase_budget)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; invalid = false },
                label = { Text(stringResource(R.string.purchase_budget_hint)) },
                singleLine = true,
                isError = invalid,
                supportingText = if (invalid) { { Text(stringResource(R.string.catalog_error_price)) } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { TextButton(onClick = { invalid = !onChosen(text) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun FinishDialog(state: PurchaseUiState, viewModel: PurchaseViewModel) {
    val finish = state.finish ?: return
    val automatic = stringResource(R.string.purchase_category_automatic)
    AlertDialog(
        onDismissRequest = viewModel::onDismissFinish,
        title = { Text(stringResource(R.string.purchase_finish_title, state.total.formatted())) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val accountError = when {
                    finish.noAccount -> stringResource(R.string.purchase_payment_choose_account)
                    CompletePurchaseError.ACCOUNT_NOT_ALLOWED in finish.errors -> stringResource(R.string.purchase_payment_account_not_allowed)
                    CompletePurchaseError.ACCOUNT_INACTIVE in finish.errors ||
                        CompletePurchaseError.ACCOUNT_NOT_FOUND in finish.errors -> stringResource(R.string.purchase_error_account)
                    else -> null
                }
                PaymentSelector(
                    method = finish.method,
                    accounts = state.accounts,
                    selectedAccountId = finish.accountId,
                    onMethodChange = viewModel::onMethodChange,
                    onAccountChange = viewModel::onAccountChange,
                    onCreateCashAccount = viewModel::onCreateCashAccount,
                    warning = state.cashShortfall?.let { stringResource(R.string.purchase_payment_cash_low, it.formatted()) },
                    accountError = accountError,
                )
                DateField(stringResource(R.string.purchase_date), finish.date, viewModel::onDateChange)
                DropdownField(
                    label = stringResource(R.string.purchase_category),
                    selectedText = state.expenseCategories.find { it.id == finish.categoryId }?.label ?: automatic,
                    options = listOf<Pair<String?, String>>(null to automatic) + state.expenseCategories.map { it.id to it.label },
                    onSelect = viewModel::onCategoryChange,
                    supportingText = if (CompletePurchaseError.CATEGORY_MISMATCH in finish.errors) {
                        stringResource(R.string.catalog_error_category)
                    } else {
                        stringResource(R.string.purchase_category_help)
                    },
                    isError = CompletePurchaseError.CATEGORY_MISMATCH in finish.errors,
                )
                if (CompletePurchaseError.CANCELLED in finish.errors || CompletePurchaseError.NOT_FOUND in finish.errors) {
                    Text(stringResource(R.string.purchase_not_found), color = MaterialTheme.colorScheme.error)
                }
                state.planned?.takeIf { it.estimateMinor > 0 }?.let { EstimateText(it.estimateMinor, state) }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::onConfirmFinish, enabled = !finish.isSaving) {
                Text(stringResource(if (state.isCompleted) R.string.action_save else R.string.purchase_finish))
            }
        },
        dismissButton = { TextButton(onClick = viewModel::onDismissFinish) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun PurchaseItemError.message(): String = stringResource(
    when (this) {
        PurchaseItemError.PURCHASE_NOT_FOUND, PurchaseItemError.PURCHASE_CANCELLED -> R.string.purchase_not_found
        PurchaseItemError.BLANK_NAME -> R.string.catalog_error_blank_name
        PurchaseItemError.NAME_TOO_LONG -> R.string.catalog_error_name_too_long
        PurchaseItemError.INVALID_QUANTITY -> R.string.purchase_error_quantity
        PurchaseItemError.INVALID_PRICE -> R.string.catalog_error_price
        PurchaseItemError.INVALID_DISCOUNT -> R.string.purchase_error_discount
    },
)
