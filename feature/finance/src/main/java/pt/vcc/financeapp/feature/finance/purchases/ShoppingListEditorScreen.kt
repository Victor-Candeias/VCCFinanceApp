package pt.vcc.financeapp.feature.finance.purchases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.data.repository.CatalogRepository
import pt.vcc.financeapp.core.data.repository.PurchaseRepository
import pt.vcc.financeapp.core.data.repository.ShoppingListRepository
import pt.vcc.financeapp.core.data.usecase.ManageShoppingListUseCase
import pt.vcc.financeapp.core.data.usecase.PlannedPurchaseReducer
import pt.vcc.financeapp.core.data.usecase.SaveShoppingListItemResult
import pt.vcc.financeapp.core.data.usecase.SaveShoppingListItemUseCase
import pt.vcc.financeapp.core.data.usecase.SaveShoppingListResult
import pt.vcc.financeapp.core.data.usecase.SaveShoppingListUseCase
import pt.vcc.financeapp.core.data.usecase.ShoppingListError
import pt.vcc.financeapp.core.data.usecase.ShoppingListItemInput
import pt.vcc.financeapp.core.data.usecase.StartPlannedPurchaseUseCase
import pt.vcc.financeapp.core.designsystem.components.EmptyState
import pt.vcc.financeapp.core.model.Merchant
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.Product
import pt.vcc.financeapp.core.model.PurchaseMath
import pt.vcc.financeapp.core.model.PurchaseType
import pt.vcc.financeapp.core.model.ShoppingList
import pt.vcc.financeapp.core.model.ShoppingListItem
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.LoadingState
import pt.vcc.financeapp.feature.finance.ui.formatAmountInput
import pt.vcc.financeapp.feature.finance.ui.formatted

data class ListItemEditorState(
    val id: String? = null,
    val name: String = "",
    val quantity: String = "1",
    val unit: String = "",
    val price: String = "",
    val productId: String? = null,
    val errors: Set<ShoppingListError> = emptySet(),
)

data class ShoppingListEditorUiState(
    val isLoading: Boolean = true,
    val list: ShoppingList? = null,
    val items: List<ShoppingListItem> = emptyList(),
    val products: List<Product> = emptyList(),
    /** Há uma compra planeada em curso desta lista: a lista não pode ser alterada. */
    val isLocked: Boolean = false,
    val editor: ListItemEditorState = ListItemEditorState(),
    val dialog: ShoppingListDialogState? = null,
    val conflict: PlannedConflict? = null,
) {
    val estimate: Money get() = Money(PlannedPurchaseReducer.reduce(items, emptyList(), emptySet()).estimateMinor, Money.DEFAULT_CURRENCY)

    val suggestions: List<Product>
        get() {
            if (editor.productId != null) return emptyList()
            val key = Merchant.normalize(editor.name)
            if (key.isEmpty()) return emptyList()
            return products.filter { it.isActive && key in Merchant.normalize(it.name) }.take(PurchaseUiState.MAX_SUGGESTIONS)
        }
}

private data class ListEditorLocal(
    val editor: ListItemEditorState = ListItemEditorState(),
    val dialog: ShoppingListDialogState? = null,
)

@HiltViewModel
class ShoppingListEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    listRepository: ShoppingListRepository,
    purchaseRepository: PurchaseRepository,
    catalogRepository: CatalogRepository,
    private val saveList: SaveShoppingListUseCase,
    private val saveItem: SaveShoppingListItemUseCase,
    private val manage: ManageShoppingListUseCase,
    startPlanned: StartPlannedPurchaseUseCase,
) : ViewModel() {
    private val listId: String = checkNotNull(savedStateHandle[LIST_ID_ARG]) { "listId em falta" }
    private val local = MutableStateFlow(ListEditorLocal())
    private val starter = PlannedPurchaseStarter(startPlanned, listRepository)

    val uiState: StateFlow<ShoppingListEditorUiState> = combine(
        combine(listRepository.observeList(listId), listRepository.observeItems(listId), ::Pair),
        catalogRepository.observeProducts(),
        purchaseRepository.observeActive(PurchaseType.PLANNED),
        local,
        starter.conflict,
    ) { (list, items), products, active, state, conflict ->
        ShoppingListEditorUiState(
            isLoading = false,
            list = list,
            items = items,
            products = products,
            isLocked = active?.shoppingListId == listId,
            editor = state.editor,
            dialog = state.dialog,
            conflict = conflict,
        )
    }
        .catch { emit(ShoppingListEditorUiState(isLoading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingListEditorUiState())

    fun updateEditor(transform: (ListItemEditorState) -> ListItemEditorState) =
        local.update { it.copy(editor = transform(it.editor).copy(errors = emptySet())) }

    fun onNameChange(value: String) = updateEditor { it.copy(name = value, productId = null) }

    fun onProductSelected(product: Product) = updateEditor { editor ->
        editor.copy(
            name = product.name,
            productId = product.id,
            unit = editor.unit.ifBlank { product.unit.orEmpty() },
            price = editor.price.ifBlank { product.defaultPriceMinor?.let { formatAmountInput(it, 2) }.orEmpty() },
        )
    }

    fun onEditItem(item: ShoppingListItem) = local.update {
        it.copy(
            editor = ListItemEditorState(
                id = item.id,
                name = item.name,
                quantity = item.quantity.formattedQuantity(),
                unit = item.unit.orEmpty(),
                price = item.estimatedPriceMinor?.let { price -> formatAmountInput(price, 2) }.orEmpty(),
                productId = item.productId,
            ),
        )
    }

    fun onClearEditor() = local.update { it.copy(editor = ListItemEditorState()) }

    fun onSaveItem() {
        val editor = local.value.editor
        val price = parseOptionalPrice(editor.price).getOrElse {
            local.update { it.copy(editor = editor.copy(errors = setOf(ShoppingListError.INVALID_PRICE))) }
            return
        }
        viewModelScope.launch {
            val result = saveItem(
                ShoppingListItemInput(
                    listId = listId,
                    id = editor.id,
                    name = editor.name,
                    quantity = PurchaseMath.parseQuantity(editor.quantity),
                    unit = editor.unit,
                    estimatedPriceMinor = price,
                    productId = editor.productId,
                ),
            )
            when (result) {
                is SaveShoppingListItemResult.Saved -> local.update { it.copy(editor = ListItemEditorState()) }
                is SaveShoppingListItemResult.Invalid -> local.update { it.copy(editor = it.editor.copy(errors = result.errors)) }
            }
        }
    }

    fun onRemoveItem(item: ShoppingListItem) {
        viewModelScope.launch {
            if (manage.removeItem(listId, item.id) && local.value.editor.id == item.id) onClearEditor()
        }
    }

    fun onMove(item: ShoppingListItem, up: Boolean) {
        viewModelScope.launch { manage.moveItem(listId, item.id, up) }
    }

    fun onEditHeader() = local.update { it.copy(dialog = ShoppingListDialogState(uiState.value.list)) }
    fun onDismissDialog() = local.update { it.copy(dialog = null) }
    fun onHeaderNameChange(value: String) = local.update { it.copy(dialog = it.dialog?.copy(name = value, errors = emptySet())) }
    fun onHeaderBudgetChange(value: String) = local.update { it.copy(dialog = it.dialog?.copy(budget = value, errors = emptySet())) }

    fun onSaveHeader() {
        val dialog = local.value.dialog ?: return
        val budget = parseOptionalPrice(dialog.budget).getOrElse {
            local.update { it.copy(dialog = dialog.copy(errors = setOf(ShoppingListError.INVALID_BUDGET))) }
            return
        }
        viewModelScope.launch {
            when (val result = saveList(listId, dialog.name, budget)) {
                is SaveShoppingListResult.Saved -> local.update { it.copy(dialog = null) }
                is SaveShoppingListResult.Invalid -> local.update { it.copy(dialog = dialog.copy(errors = result.errors)) }
            }
        }
    }

    fun onStartPurchase(replace: Boolean, onStarted: (String) -> Unit) {
        viewModelScope.launch { starter.start(listId, replace, onStarted) }
    }

    fun onDismissConflict() = starter.conflict.update { null }

    companion object {
        const val LIST_ID_ARG = "listId"
    }
}

/** Itens planeados de uma lista: editor no topo (o teclado não o tapa), reordenar, remover e iniciar a compra. */
@Composable
fun ShoppingListEditorScreen(
    onBack: () -> Unit,
    onOpenPurchase: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShoppingListEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val list = state.list
    PurchaseScaffold(
        title = list?.name ?: stringResource(R.string.shopping_lists_title),
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (list != null && !state.isLocked) {
                IconButton(onClick = viewModel::onEditHeader) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.shopping_lists_edit))
                }
            }
        },
        bottomBar = {
            if (list != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.shopping_list_estimate_label), style = MaterialTheme.typography.labelMedium)
                            Text(state.estimate.formatted(), style = MaterialTheme.typography.headlineSmall)
                            list.budgetMinor?.let {
                                Text(stringResource(R.string.purchase_budget_value, Money(it, Money.DEFAULT_CURRENCY).formatted()), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Button(
                            onClick = { viewModel.onStartPurchase(replace = false, onStarted = onOpenPurchase) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(if (state.isLocked) R.string.shopping_lists_resume else R.string.shopping_lists_start))
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            list == null -> EmptyState(
                title = stringResource(R.string.shopping_lists_title),
                message = stringResource(R.string.catalog_error_not_found),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().imePadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.isLocked) {
                    item(key = "locked") {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Lock, contentDescription = null)
                                Text(stringResource(R.string.shopping_list_locked))
                            }
                        }
                    }
                } else {
                    item(key = "editor") { ListItemEditor(state, viewModel) }
                }
                item(key = "title") {
                    Text(stringResource(R.string.purchase_items_title, state.items.size), style = MaterialTheme.typography.titleMedium)
                }
                if (state.items.isEmpty()) {
                    item(key = "empty") { Text(stringResource(R.string.shopping_list_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                    ListItem(
                        modifier = Modifier.clickable(enabled = !state.isLocked) { viewModel.onEditItem(item) },
                        headlineContent = { Text(item.name) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    "${item.quantity.formattedQuantity()} ${item.unit.orEmpty()}".trim(),
                                    item.estimatedPriceMinor?.let { Money(it, Money.DEFAULT_CURRENCY).formatted() },
                                ).joinToString(" · "),
                            )
                        },
                        trailingContent = if (state.isLocked) null else {
                            {
                                Row {
                                    IconButton(onClick = { viewModel.onMove(item, up = true) }, enabled = index > 0) {
                                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.shopping_list_move_up, item.name))
                                    }
                                    IconButton(onClick = { viewModel.onMove(item, up = false) }, enabled = index < state.items.lastIndex) {
                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.shopping_list_move_down, item.name))
                                    }
                                    IconButton(onClick = { viewModel.onRemoveItem(item) }) {
                                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.purchase_item_remove, item.name))
                                    }
                                }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    state.dialog?.let { dialog ->
        ShoppingListDialog(
            dialog = dialog,
            onNameChange = viewModel::onHeaderNameChange,
            onBudgetChange = viewModel::onHeaderBudgetChange,
            onSave = viewModel::onSaveHeader,
            onDismiss = viewModel::onDismissDialog,
        )
    }
    state.conflict?.let { conflict ->
        PlannedConflictDialog(
            conflict = conflict,
            onResume = { viewModel.onDismissConflict(); onOpenPurchase(conflict.activePurchaseId) },
            onReplace = { viewModel.onStartPurchase(replace = true, onStarted = onOpenPurchase) },
            onDismiss = viewModel::onDismissConflict,
        )
    }
}

@Composable
private fun ListItemEditor(state: ShoppingListEditorUiState, viewModel: ShoppingListEditorViewModel) {
    val editor = state.editor
    fun error(vararg codes: ShoppingListError) = codes.firstOrNull { it in editor.errors }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (editor.id != null) R.string.purchase_item_edit else R.string.purchase_item_add),
                style = MaterialTheme.typography.titleMedium,
            )
            val nameError = error(ShoppingListError.BLANK_NAME, ShoppingListError.NAME_TOO_LONG, ShoppingListError.IN_USE, ShoppingListError.NOT_FOUND)
            OutlinedTextField(
                value = editor.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.purchase_item_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it.message()) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )
            state.suggestions.forEach { product ->
                SuggestionChip(onClick = { viewModel.onProductSelected(product) }, label = { Text(product.name) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val quantityError = error(ShoppingListError.INVALID_QUANTITY)
                OutlinedTextField(
                    value = editor.quantity,
                    onValueChange = { value -> viewModel.updateEditor { it.copy(quantity = value) } },
                    label = { Text(stringResource(R.string.purchase_item_quantity)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    isError = quantityError != null,
                    supportingText = quantityError?.let { { Text(it.message()) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = editor.unit,
                    onValueChange = { value -> viewModel.updateEditor { it.copy(unit = value) } },
                    label = { Text(stringResource(R.string.purchase_item_unit_short)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
            }
            val priceError = error(ShoppingListError.INVALID_PRICE)
            OutlinedTextField(
                value = editor.price,
                onValueChange = { value -> viewModel.updateEditor { it.copy(price = value) } },
                label = { Text(stringResource(R.string.shopping_list_estimated_price)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = priceError != null,
                supportingText = priceError?.let { { Text(it.message()) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editor.id != null || editor.name.isNotBlank()) {
                    OutlinedButton(onClick = viewModel::onClearEditor, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
                Button(onClick = viewModel::onSaveItem, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(if (editor.id != null) R.string.action_save else R.string.purchase_item_add_button))
                }
            }
        }
    }
}
