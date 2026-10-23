package pt.vcc.financeapp.feature.finance.purchases

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.financeapp.core.data.usecase.CompletePurchaseError
import pt.vcc.financeapp.core.data.usecase.PurchaseItemError
import pt.vcc.financeapp.core.designsystem.components.EmptyState
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.Product
import pt.vcc.financeapp.core.model.PurchaseItem
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.DateField
import pt.vcc.financeapp.feature.finance.ui.DropdownField
import pt.vcc.financeapp.feature.finance.ui.LoadingState
import pt.vcc.financeapp.feature.finance.ui.TimeField
import pt.vcc.financeapp.feature.finance.ui.formatted

/** Despesa rápida: favoritos e catálogo em grelha (um toque soma uma unidade), itens e pagamento pré-selecionado. */
@Composable
fun ExpressPurchaseScreen(
    onBack: () -> Unit,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpressPurchaseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val itemError = state.itemError?.message()

    LaunchedEffect(state.isClosed) { if (state.isClosed) onClosed() }
    LaunchedEffect(itemError) {
        if (itemError != null) {
            snackbar.showSnackbar(itemError)
            viewModel.onItemErrorShown()
        }
    }

    PurchaseScaffold(
        title = stringResource(R.string.purchase_type_express),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbar,
        actions = {
            if (state.purchase != null) {
                IconButton(onClick = { viewModel.onShowDialog(ExpressDialog.CANCEL) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.express_discard))
                }
            }
        },
        bottomBar = { if (state.purchase != null) ExpressBottomBar(state, viewModel) },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.notFound -> EmptyState(
                title = stringResource(R.string.purchase_type_express),
                message = stringResource(R.string.purchase_not_found),
                modifier = Modifier.padding(padding),
            )
            else -> ExpressContent(state, viewModel, Modifier.padding(padding))
        }
    }

    when (state.dialog) {
        ExpressDialog.MERCHANT -> MerchantPickerDialog(state.merchants, state.merchantName, viewModel::onMerchantChosen) {
            viewModel.onShowDialog(null)
        }
        ExpressDialog.NOTES -> NotesDialog(state.purchase?.notes, state.notesTooLong, viewModel::onNotesChosen) { viewModel.onShowDialog(null) }
        ExpressDialog.PAYMENT -> PaymentDialog(state, viewModel)
        ExpressDialog.CANCEL -> AlertDialog(
            onDismissRequest = { viewModel.onShowDialog(null) },
            title = { Text(stringResource(R.string.express_discard)) },
            text = { Text(stringResource(R.string.express_discard_message)) },
            confirmButton = { TextButton(onClick = viewModel::onConfirmCancel) { Text(stringResource(R.string.express_discard)) } },
            dismissButton = { TextButton(onClick = { viewModel.onShowDialog(null) }) { Text(stringResource(R.string.purchase_cancel_keep)) } },
        )
        null -> Unit
    }
    state.pricePrompt?.let { PriceDialog(it, viewModel) }
    state.promoteItem?.let { PromoteDialog(it, state, viewModel) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpressContent(state: ExpressUiState, viewModel: ExpressPurchaseViewModel, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { viewModel.onShowDialog(ExpressDialog.MERCHANT) },
                    label = { Text(state.merchantName ?: stringResource(R.string.purchase_choose_merchant)) },
                    leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
                )
                AssistChip(
                    onClick = { viewModel.onShowDialog(ExpressDialog.NOTES) },
                    label = {
                        Text(
                            state.purchase?.notes ?: stringResource(R.string.express_notes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                )
            }
        }
        item(key = "search") {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.express_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
            )
        }
        if (state.query.isBlank()) {
            if (state.favorites.isNotEmpty()) {
                item(key = "favorites-title") { ExpressSectionTitle(stringResource(R.string.express_favorites)) }
                item(key = "favorites") { ProductGrid(state.favorites, state, viewModel) }
            }
            item(key = "categories") { CategoryChips(state, viewModel) }
        } else if (state.canCreateTemporary) {
            item(key = "temporary") {
                OutlinedButton(onClick = viewModel::onCreateTemporary, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.express_add_temporary, state.query.trim()), Modifier.padding(start = 8.dp))
                }
            }
        }
        item(key = "products") {
            if (state.visibleProducts.isEmpty()) {
                Text(
                    stringResource(R.string.express_no_products),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ProductGrid(state.visibleProducts, state, viewModel)
            }
        }
        item(key = "items-title") { ExpressSectionTitle(stringResource(R.string.purchase_items_title, state.items.size)) }
        if (state.items.isEmpty()) {
            item(key = "items-empty") {
                Text(
                    stringResource(R.string.express_items_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.items, key = { it.id }) { item ->
            ExpressItemRow(item, temporary = item.productId in state.temporaryProductIds, currency = state.currency, viewModel = viewModel)
        }
    }
}

@Composable
private fun ExpressSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun CategoryChips(state: ExpressUiState, viewModel: ExpressPurchaseViewModel) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.selectedCategoryId == null,
            onClick = { viewModel.onCategorySelected(null) },
            label = { Text(stringResource(R.string.express_all_products)) },
        )
        state.usedCategories.forEach { category ->
            FilterChip(
                selected = state.selectedCategoryId == category.id,
                onClick = { viewModel.onCategorySelected(category.id) },
                label = { Text(category.name) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductGrid(products: List<Product>, state: ExpressUiState, viewModel: ExpressPurchaseViewModel) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3,
    ) {
        products.forEach { product ->
            val quantity = state.quantities[product.id]
            val description = stringResource(R.string.express_add_product, product.name)
            OutlinedCard(
                onClick = { viewModel.onProductTap(product) },
                modifier = Modifier.weight(1f).heightIn(min = 72.dp).semantics { contentDescription = description },
            ) {
                BadgedBox(
                    badge = { if (quantity != null) Badge { Text(quantity.formattedQuantity()) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (product.isFavorite) Icon(Icons.Filled.Star, contentDescription = null, Modifier.padding(end = 4.dp))
                            Text(product.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        product.defaultPriceMinor?.let {
                            Text(
                                Money(it, state.currency).formatted(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressItemRow(item: PurchaseItem, temporary: Boolean, currency: String, viewModel: ExpressPurchaseViewModel) {
    val decrease = stringResource(R.string.express_decrease, item.name)
    val increase = stringResource(R.string.express_increase, item.name)
    ListItem(
        headlineContent = { Text(item.name) },
        supportingContent = {
            Column {
                TextButton(onClick = { viewModel.onEditPrice(item) }, contentPadding = PaddingValues(0.dp)) {
                    Text("${item.quantity.formattedQuantity()} × ${Money(item.unitPriceMinor, currency).formatted()}")
                }
                if (temporary) {
                    TextButton(onClick = { viewModel.onPromote(item) }, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.express_promote))
                    }
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.onDecrement(item) }, modifier = Modifier.semantics { contentDescription = decrease }) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                Text(Money(item.totalMinor, currency).formatted(), style = MaterialTheme.typography.titleSmall)
                if (item.productId != null) {
                    IconButton(onClick = { viewModel.onIncrement(item) }, modifier = Modifier.semantics { contentDescription = increase }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                    }
                }
            }
        },
    )
    HorizontalDivider()
}

@Composable
private fun ExpressBottomBar(state: ExpressUiState, viewModel: ExpressPurchaseViewModel) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { viewModel.onShowDialog(ExpressDialog.PAYMENT) },
                    label = {
                        Text(
                            stringResource(state.payment.method.labelRes) + " · " +
                                (state.paymentAccount?.name ?: stringResource(R.string.purchase_payment_choose_account)),
                        )
                    },
                )
                AssistChip(
                    onClick = { viewModel.onShowDialog(ExpressDialog.PAYMENT) },
                    label = { Text("${state.date.formatted()} ${state.time.formatted()}") },
                    leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.purchase_total), style = MaterialTheme.typography.labelMedium)
                    Text(state.total.formatted(), style = MaterialTheme.typography.headlineSmall)
                }
                Button(
                    onClick = viewModel::onSave,
                    enabled = state.items.isNotEmpty() && !state.isSaving,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}

@Composable
private fun PaymentDialog(state: ExpressUiState, viewModel: ExpressPurchaseViewModel) {
    val automatic = stringResource(R.string.purchase_category_automatic)
    AlertDialog(
        onDismissRequest = { viewModel.onShowDialog(null) },
        title = { Text(stringResource(R.string.purchase_payment)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val accountError = when {
                    state.noAccount -> stringResource(R.string.purchase_payment_choose_account)
                    CompletePurchaseError.ACCOUNT_NOT_ALLOWED in state.errors -> stringResource(R.string.purchase_payment_account_not_allowed)
                    CompletePurchaseError.ACCOUNT_INACTIVE in state.errors ||
                        CompletePurchaseError.ACCOUNT_NOT_FOUND in state.errors -> stringResource(R.string.purchase_error_account)
                    else -> null
                }
                PaymentSelector(
                    method = state.payment.method,
                    accounts = state.accounts,
                    selectedAccountId = state.payment.accountId,
                    onMethodChange = viewModel::onMethodChange,
                    onAccountChange = viewModel::onAccountChange,
                    onCreateCashAccount = viewModel::onCreateCashAccount,
                    warning = state.cashShortfall?.let { stringResource(R.string.purchase_payment_cash_low, it.formatted()) },
                    accountError = accountError,
                )
                DateField(stringResource(R.string.purchase_date), state.date, viewModel::onDateChange)
                TimeField(stringResource(R.string.express_time), state.time, viewModel::onTimeChange)
                DropdownField(
                    label = stringResource(R.string.purchase_category),
                    selectedText = state.expenseCategories.find { it.id == state.payment.categoryId }?.label ?: automatic,
                    options = listOf<Pair<String?, String>>(null to automatic) + state.expenseCategories.map { it.id to it.label },
                    onSelect = viewModel::onCategoryChange,
                    supportingText = if (CompletePurchaseError.CATEGORY_MISMATCH in state.errors) {
                        stringResource(R.string.catalog_error_category)
                    } else {
                        stringResource(R.string.purchase_category_help)
                    },
                    isError = CompletePurchaseError.CATEGORY_MISMATCH in state.errors,
                )
                if (CompletePurchaseError.CANCELLED in state.errors || CompletePurchaseError.NOT_FOUND in state.errors) {
                    Text(stringResource(R.string.purchase_not_found), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::onSave, enabled = state.items.isNotEmpty() && !state.isSaving) {
                Text(stringResource(R.string.express_save_with_total, state.total.formatted()))
            }
        },
        dismissButton = { TextButton(onClick = { viewModel.onShowDialog(null) }) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun PriceDialog(prompt: PricePrompt, viewModel: ExpressPurchaseViewModel) {
    val error = prompt.errors.firstOrNull()
    AlertDialog(
        onDismissRequest = viewModel::onDismissPrice,
        title = {
            Text(
                if (prompt.isTemporary) {
                    stringResource(R.string.express_temporary_title, prompt.name)
                } else {
                    stringResource(R.string.express_price_title, prompt.name)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = prompt.price,
                    onValueChange = viewModel::onPriceTextChange,
                    label = { Text(stringResource(R.string.purchase_item_price)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it.message()) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                )
                Text(
                    stringResource(if (prompt.isTemporary) R.string.express_temporary_help else R.string.express_price_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = viewModel::onConfirmPrice) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = viewModel::onDismissPrice) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun PromoteDialog(item: PurchaseItem, state: ExpressUiState, viewModel: ExpressPurchaseViewModel) {
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    val none = stringResource(R.string.catalog_no_category)
    AlertDialog(
        onDismissRequest = viewModel::onDismissPromote,
        title = { Text(stringResource(R.string.express_promote_title, item.name)) },
        text = {
            DropdownField(
                label = stringResource(R.string.catalog_product_category),
                selectedText = state.categories.find { it.id == categoryId }?.name ?: none,
                options = listOf<Pair<String?, String>>(null to none) + state.categories.filter { it.isActive }.map { it.id to it.name },
                onSelect = { categoryId = it },
                supportingText = state.promoteError?.message(),
                isError = state.promoteError != null,
            )
        },
        confirmButton = { TextButton(onClick = { viewModel.onConfirmPromote(categoryId) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = viewModel::onDismissPromote) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun NotesDialog(current: String?, tooLong: Boolean, onChosen: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.express_notes)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.express_notes_hint)) },
                isError = tooLong,
                supportingText = if (tooLong) { { Text(stringResource(R.string.express_notes_too_long)) } } else null,
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = { TextButton(onClick = { onChosen(text) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
