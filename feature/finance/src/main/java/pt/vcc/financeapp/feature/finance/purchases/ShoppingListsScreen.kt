package pt.vcc.financeapp.feature.finance.purchases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import pt.vcc.financeapp.core.data.repository.PurchaseRepository
import pt.vcc.financeapp.core.data.repository.ShoppingListRepository
import pt.vcc.financeapp.core.data.usecase.ManageShoppingListUseCase
import pt.vcc.financeapp.core.data.usecase.SaveShoppingListResult
import pt.vcc.financeapp.core.data.usecase.SaveShoppingListUseCase
import pt.vcc.financeapp.core.data.usecase.ShoppingListError
import pt.vcc.financeapp.core.data.usecase.StartPlannedPurchaseResult
import pt.vcc.financeapp.core.data.usecase.StartPlannedPurchaseUseCase
import pt.vcc.financeapp.core.designsystem.components.EmptyState
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.PurchaseType
import pt.vcc.financeapp.core.model.ShoppingList
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.LoadingState
import pt.vcc.financeapp.feature.finance.ui.formatAmountInput
import pt.vcc.financeapp.feature.finance.ui.formatted

/** Nome/orçamento de uma lista; `list == null` cria uma nova. */
data class ShoppingListDialogState(
    val list: ShoppingList? = null,
    val name: String = list?.name.orEmpty(),
    val budget: String = list?.budgetMinor?.let { formatAmountInput(it, 2) }.orEmpty(),
    val errors: Set<ShoppingListError> = emptySet(),
)

/** Já existe uma compra planeada em curso de outra lista. */
data class PlannedConflict(val listId: String, val activePurchaseId: String, val activeListName: String?)

data class ShoppingListsUiState(
    val isLoading: Boolean = true,
    val lists: List<ShoppingList> = emptyList(),
    val showArchived: Boolean = false,
    val activeListId: String? = null,
    val dialog: ShoppingListDialogState? = null,
    val confirmDelete: ShoppingList? = null,
    val conflict: PlannedConflict? = null,
    val inUseMessage: Boolean = false,
)

/** Arranque de uma compra planeada com a regra "uma compra planeada ativa", partilhado pelos ecrãs das listas. */
internal class PlannedPurchaseStarter(
    private val startPlanned: StartPlannedPurchaseUseCase,
    private val listRepository: ShoppingListRepository,
) {
    val conflict = MutableStateFlow<PlannedConflict?>(null)

    suspend fun start(listId: String, replace: Boolean, onStarted: (String) -> Unit) {
        when (val result = startPlanned(listId, replace)) {
            is StartPlannedPurchaseResult.Started -> {
                conflict.value = null
                onStarted(result.purchase.id)
            }
            is StartPlannedPurchaseResult.Conflict -> conflict.value = PlannedConflict(
                listId = listId,
                activePurchaseId = result.active.id,
                activeListName = result.active.shoppingListId?.let { listRepository.getList(it)?.name },
            )
            StartPlannedPurchaseResult.ListNotFound -> conflict.value = null
        }
    }
}

@HiltViewModel
class ShoppingListsViewModel @Inject constructor(
    private val listRepository: ShoppingListRepository,
    purchaseRepository: PurchaseRepository,
    private val saveList: SaveShoppingListUseCase,
    private val manage: ManageShoppingListUseCase,
    startPlanned: StartPlannedPurchaseUseCase,
) : ViewModel() {
    private val local = MutableStateFlow(ShoppingListsUiState())
    private val starter = PlannedPurchaseStarter(startPlanned, listRepository)

    val uiState: StateFlow<ShoppingListsUiState> = combine(
        listRepository.observeLists(),
        purchaseRepository.observeActive(PurchaseType.PLANNED),
        local,
        starter.conflict,
    ) { lists, active, state, conflict ->
        state.copy(
            isLoading = false,
            lists = lists.filter { it.isArchived == state.showArchived },
            activeListId = active?.shoppingListId,
            conflict = conflict,
        )
    }
        .catch { emit(ShoppingListsUiState(isLoading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingListsUiState())

    fun onShowArchived(value: Boolean) = local.update { it.copy(showArchived = value) }
    fun onAdd() = local.update { it.copy(dialog = ShoppingListDialogState()) }
    fun onDismissDialog() = local.update { it.copy(dialog = null) }
    fun onNameChange(value: String) = local.update { it.copy(dialog = it.dialog?.copy(name = value, errors = emptySet())) }
    fun onBudgetChange(value: String) = local.update { it.copy(dialog = it.dialog?.copy(budget = value, errors = emptySet())) }

    fun onSave(onCreated: (String) -> Unit) {
        val dialog = local.value.dialog ?: return
        val budget = parseOptionalPrice(dialog.budget).getOrElse {
            local.update { it.copy(dialog = dialog.copy(errors = setOf(ShoppingListError.INVALID_BUDGET))) }
            return
        }
        viewModelScope.launch {
            when (val result = saveList(dialog.list?.id, dialog.name, budget)) {
                is SaveShoppingListResult.Saved -> {
                    local.update { it.copy(dialog = null) }
                    if (dialog.list == null) onCreated(result.list.id)
                }
                is SaveShoppingListResult.Invalid -> local.update { it.copy(dialog = dialog.copy(errors = result.errors)) }
            }
        }
    }

    fun onDuplicate(list: ShoppingList, suffix: String) {
        viewModelScope.launch { manage.duplicate(list.id, suffix) }
    }

    fun onArchive(list: ShoppingList, archived: Boolean) {
        viewModelScope.launch {
            if (!manage.setArchived(list.id, archived)) local.update { it.copy(inUseMessage = true) }
        }
    }

    fun onRequestDelete(list: ShoppingList?) = local.update { it.copy(confirmDelete = list) }

    fun onConfirmDelete() {
        val list = local.value.confirmDelete ?: return
        viewModelScope.launch {
            val deleted = manage.delete(list.id)
            local.update { it.copy(confirmDelete = null, inUseMessage = !deleted) }
        }
    }

    fun onInUseShown() = local.update { it.copy(inUseMessage = false) }

    fun onStartPurchase(listId: String, replace: Boolean, onStarted: (String) -> Unit) {
        viewModelScope.launch { starter.start(listId, replace, onStarted) }
    }

    fun onDismissConflict() = starter.conflict.update { null }
}

@Composable
fun ShoppingListsScreen(
    onBack: () -> Unit,
    onOpenList: (String) -> Unit,
    onOpenPurchase: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShoppingListsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val inUse = stringResource(R.string.shopping_lists_in_use)
    val copySuffix = stringResource(R.string.shopping_lists_copy_suffix)
    LaunchedEffect(state.inUseMessage) {
        if (state.inUseMessage) {
            snackbar.showSnackbar(inUse)
            viewModel.onInUseShown()
        }
    }

    PurchaseScaffold(
        title = stringResource(R.string.shopping_lists_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbar,
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.shopping_lists_add))
            }
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@PurchaseScaffold
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            FilterChip(
                selected = state.showArchived,
                onClick = { viewModel.onShowArchived(!state.showArchived) },
                label = { Text(stringResource(R.string.shopping_lists_show_archived)) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (state.lists.isEmpty()) {
                EmptyState(stringResource(R.string.shopping_lists_title), stringResource(R.string.shopping_lists_empty))
            } else {
                LazyColumn {
                    items(state.lists, key = { it.id }) { list ->
                        ShoppingListRow(
                            list = list,
                            isActive = list.id == state.activeListId,
                            onOpen = { onOpenList(list.id) },
                            onStart = { viewModel.onStartPurchase(list.id, replace = false, onStarted = onOpenPurchase) },
                            onDuplicate = { viewModel.onDuplicate(list, copySuffix) },
                            onArchive = { viewModel.onArchive(list, !list.isArchived) },
                            onDelete = { viewModel.onRequestDelete(list) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    state.dialog?.let { dialog ->
        ShoppingListDialog(
            dialog = dialog,
            onNameChange = viewModel::onNameChange,
            onBudgetChange = viewModel::onBudgetChange,
            onSave = { viewModel.onSave(onOpenList) },
            onDismiss = viewModel::onDismissDialog,
        )
    }
    state.confirmDelete?.let { list ->
        AlertDialog(
            onDismissRequest = { viewModel.onRequestDelete(null) },
            title = { Text(stringResource(R.string.shopping_lists_delete_title, list.name)) },
            text = { Text(stringResource(R.string.shopping_lists_delete_message)) },
            confirmButton = { TextButton(onClick = viewModel::onConfirmDelete) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { viewModel.onRequestDelete(null) }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    state.conflict?.let { conflict ->
        PlannedConflictDialog(
            conflict = conflict,
            onResume = { viewModel.onDismissConflict(); onOpenPurchase(conflict.activePurchaseId) },
            onReplace = { viewModel.onStartPurchase(conflict.listId, replace = true, onStarted = onOpenPurchase) },
            onDismiss = viewModel::onDismissConflict,
        )
    }
}

@Composable
private fun ShoppingListRow(
    list: ShoppingList,
    isActive: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onDuplicate: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        headlineContent = { Text(list.name) },
        supportingContent = {
            val parts = listOfNotNull(
                list.budgetMinor?.let { stringResource(R.string.purchase_budget_value, Money(it, Money.DEFAULT_CURRENCY).formatted()) },
                stringResource(R.string.shopping_lists_active_purchase).takeIf { isActive },
            )
            if (parts.isNotEmpty()) {
                Text(parts.joinToString(" · "), color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.shopping_lists_actions, list.name))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (isActive) R.string.shopping_lists_resume else R.string.shopping_lists_start)) },
                        leadingIcon = { Icon(Icons.Filled.ShoppingCart, contentDescription = null) },
                        onClick = { menu = false; onStart() },
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.shopping_lists_duplicate)) }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(
                        text = { Text(stringResource(if (list.isArchived) R.string.shopping_lists_unarchive else R.string.shopping_lists_archive)) },
                        onClick = { menu = false; onArchive() },
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; onDelete() })
                }
            }
        },
    )
}

@Composable
internal fun ShoppingListDialog(
    dialog: ShoppingListDialogState,
    onNameChange: (String) -> Unit,
    onBudgetChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (dialog.list == null) R.string.shopping_lists_add else R.string.shopping_lists_edit)) },
        text = {
            Column {
                val nameError = dialog.errors.firstOrNull { it != ShoppingListError.INVALID_BUDGET }
                OutlinedTextField(
                    value = dialog.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.label_name)) },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { error -> { Text(error.message()) } },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                val budgetError = ShoppingListError.INVALID_BUDGET in dialog.errors
                OutlinedTextField(
                    value = dialog.budget,
                    onValueChange = onBudgetChange,
                    label = { Text(stringResource(R.string.purchase_budget_hint)) },
                    singleLine = true,
                    isError = budgetError,
                    supportingText = if (budgetError) { { Text(stringResource(R.string.catalog_error_price)) } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun PlannedConflictDialog(conflict: PlannedConflict, onResume: () -> Unit, onReplace: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_lists_conflict_title)) },
        text = {
            Text(
                stringResource(
                    R.string.shopping_lists_conflict_message,
                    conflict.activeListName ?: stringResource(R.string.purchase_type_planned),
                ),
            )
        },
        confirmButton = { TextButton(onClick = onResume) { Text(stringResource(R.string.shopping_lists_conflict_resume)) } },
        dismissButton = {
            Column {
                TextButton(onClick = onReplace) { Text(stringResource(R.string.shopping_lists_conflict_replace)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
internal fun ShoppingListError.message(): String = stringResource(
    when (this) {
        ShoppingListError.NOT_FOUND -> R.string.catalog_error_not_found
        ShoppingListError.IN_USE -> R.string.shopping_lists_in_use
        ShoppingListError.BLANK_NAME -> R.string.catalog_error_blank_name
        ShoppingListError.NAME_TOO_LONG -> R.string.catalog_error_name_too_long
        ShoppingListError.INVALID_BUDGET, ShoppingListError.INVALID_PRICE -> R.string.catalog_error_price
        ShoppingListError.INVALID_QUANTITY -> R.string.purchase_error_quantity
    },
)
