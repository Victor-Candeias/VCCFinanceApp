package pt.vcc.financeapp.feature.finance.purchases

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.data.repository.CatalogRepository
import pt.vcc.financeapp.core.data.repository.CategoryRepository
import pt.vcc.financeapp.core.data.repository.MerchantRepository
import pt.vcc.financeapp.core.data.repository.PurchaseRepository
import pt.vcc.financeapp.core.data.repository.ShoppingListRepository
import pt.vcc.financeapp.core.data.usecase.CancelPurchaseUseCase
import pt.vcc.financeapp.core.data.usecase.CompletePurchaseError
import pt.vcc.financeapp.core.data.usecase.CompletePurchaseInput
import pt.vcc.financeapp.core.data.usecase.CompletePurchaseResult
import pt.vcc.financeapp.core.data.usecase.CompletePurchaseUseCase
import pt.vcc.financeapp.core.data.usecase.CreateCashAccountUseCase
import pt.vcc.financeapp.core.data.usecase.EditPurchaseHeaderUseCase
import pt.vcc.financeapp.core.data.usecase.ObserveAccountBalancesUseCase
import pt.vcc.financeapp.core.data.usecase.PlannedProgress
import pt.vcc.financeapp.core.data.usecase.PlannedPurchaseReducer
import pt.vcc.financeapp.core.data.usecase.PurchaseItemError
import pt.vcc.financeapp.core.data.usecase.PurchaseItemInput
import pt.vcc.financeapp.core.data.usecase.SavePurchaseItemResult
import pt.vcc.financeapp.core.data.usecase.SavePurchaseItemUseCase
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.Merchant
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.PaymentAccounts
import pt.vcc.financeapp.core.model.PaymentMethod
import pt.vcc.financeapp.core.model.PriceSource
import pt.vcc.financeapp.core.model.Product
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.core.model.PurchaseItem
import pt.vcc.financeapp.core.model.PurchaseMath
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.core.model.PurchaseType
import pt.vcc.financeapp.core.model.ShoppingListItem
import pt.vcc.financeapp.core.model.TransactionType
import pt.vcc.financeapp.feature.finance.transactions.CategoryOption
import pt.vcc.financeapp.feature.finance.transactions.toOptions
import pt.vcc.financeapp.feature.finance.ui.formatAmountInput

/** Formulário de item (texto tal como escrito); `id == null` adiciona um item novo. */
data class ItemEditorState(
    val id: String? = null,
    val name: String = "",
    val quantity: String = "1",
    val unit: String = "",
    val price: String = "",
    val discount: String = "",
    val productId: String? = null,
    val shoppingListItemId: String? = null,
    val priceSource: PriceSource = PriceSource.MANUAL,
    val errors: Set<PurchaseItemError> = emptySet(),
) {
    val isEditing: Boolean get() = id != null
}

/** Folha de conclusão: forma de pagamento, conta, data e categoria (nula = automática). */
data class FinishState(
    val method: PaymentMethod,
    val accountId: String?,
    val date: LocalDate,
    val categoryId: String? = null,
    val errors: Set<CompletePurchaseError> = emptySet(),
    val noAccount: Boolean = false,
    val isSaving: Boolean = false,
)

enum class PurchaseDialog { MERCHANT, BUDGET, CANCEL }

data class PurchaseUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val purchase: Purchase? = null,
    val items: List<PurchaseItem> = emptyList(),
    val merchantName: String? = null,
    val listName: String? = null,
    /** Só nas compras planeadas ativas: estado dos itens da lista. */
    val planned: PlannedProgress? = null,
    val merchants: List<Merchant> = emptyList(),
    val products: List<Product> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val cashBalances: Map<String, Money> = emptyMap(),
    val expenseCategories: List<CategoryOption> = emptyList(),
    val editor: ItemEditorState = ItemEditorState(),
    val finish: FinishState? = null,
    val dialog: PurchaseDialog? = null,
    val removedItem: PurchaseItem? = null,
    val isClosed: Boolean = false,
) {
    val currency: String get() = purchase?.currency ?: Money.DEFAULT_CURRENCY
    val total: Money get() = Money(purchase?.totalMinor ?: 0, currency)
    val budget: Money? get() = purchase?.budgetMinor?.let { Money(it, currency) }

    /** Restante do orçamento (negativo = excedido). */
    val remaining: Money? get() = purchase?.budgetMinor?.let { Money(it - total.amountMinor, currency) }

    /** Total previsto do item em edição, quando os campos são válidos. */
    val editorTotal: Money?
        get() {
            val quantity = PurchaseMath.parseQuantity(editor.quantity) ?: return null
            val price = parseOptionalPrice(editor.price).getOrNull() ?: return null
            val discount = parseOptionalPrice(editor.discount).getOrNull() ?: 0
            return Money(PurchaseMath.itemTotal(quantity, price, discount), currency)
        }

    /** Produtos do catálogo que correspondem ao nome escrito (enquanto não estiver ligado a um produto). */
    val suggestions: List<Product>
        get() {
            if (editor.productId != null) return emptyList()
            val key = Merchant.normalize(editor.name)
            if (key.isEmpty()) return emptyList()
            return products.filter { it.isActive && key in Merchant.normalize(it.name) }.take(MAX_SUGGESTIONS)
        }

    /** Dinheiro: aviso (não bloqueante) quando o saldo da conta não chega para o total. */
    val cashShortfall: Money?
        get() {
            val finish = finish ?: return null
            if (finish.method != PaymentMethod.CASH) return null
            val balance = finish.accountId?.let(cashBalances::get) ?: return null
            return balance.takeIf { it.amountMinor < total.amountMinor }
        }

    val isCompleted: Boolean get() = purchase?.status == PurchaseStatus.COMPLETED

    companion object {
        const val MAX_SUGGESTIONS = 5
    }
}

private data class LocalState(
    val editor: ItemEditorState = ItemEditorState(),
    val finish: FinishState? = null,
    val dialog: PurchaseDialog? = null,
    val removedItem: PurchaseItem? = null,
    val isClosed: Boolean = false,
)

private data class Reference(
    val merchants: List<Merchant>,
    val allMerchants: List<Merchant>,
    val products: List<Product>,
    val accounts: List<Account>,
    val cashBalances: Map<String, Money>,
    val categories: List<CategoryOption>,
)

/**
 * Compra em curso (rápida ou planeada) ou concluída em edição. Itens, estabelecimento e orçamento
 * são gravados de imediato em Room, por isso a compra sobrevive a rotação e à morte do processo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PurchaseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val purchaseRepository: PurchaseRepository,
    merchantRepository: MerchantRepository,
    private val catalogRepository: CatalogRepository,
    shoppingListRepository: ShoppingListRepository,
    categoryRepository: CategoryRepository,
    observeAccountBalances: ObserveAccountBalancesUseCase,
    private val saveItem: SavePurchaseItemUseCase,
    private val editHeader: EditPurchaseHeaderUseCase,
    private val cancelPurchase: CancelPurchaseUseCase,
    private val completePurchase: CompletePurchaseUseCase,
    private val createCashAccount: CreateCashAccountUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val purchaseId: String = checkNotNull(savedStateHandle[PURCHASE_ID_ARG]) { "purchaseId em falta" }
    private val local = MutableStateFlow(LocalState())
    private val _uiState = MutableStateFlow(PurchaseUiState())
    val uiState: StateFlow<PurchaseUiState> = _uiState.asStateFlow()

    init {
        val purchaseFlow = purchaseRepository.observePurchase(purchaseId)
        val listFlow = purchaseFlow.flatMapLatest { purchase ->
            val listId = purchase?.shoppingListId ?: return@flatMapLatest flowOf(null)
            combine(
                shoppingListRepository.observeList(listId),
                shoppingListRepository.observeItems(listId),
                purchaseRepository.observeSkipped(purchaseId),
            ) { list, items, skipped -> Triple(list?.name, items, skipped) }
        }
        val reference = combine(
            merchantRepository.observeAll(),
            catalogRepository.observeProducts(),
            observeAccountBalances(),
            categoryRepository.observeAllCategories(),
        ) { merchants, products, balances, categories ->
            Reference(
                merchants = merchants.filter { it.isActive },
                allMerchants = merchants,
                products = products,
                accounts = balances.map { it.account },
                cashBalances = balances.associate { it.account.id to it.balance.booked },
                categories = categories.filter { it.type == TransactionType.EXPENSE }.toOptions(),
            )
        }
        viewModelScope.launch {
            combine(purchaseFlow, purchaseRepository.observeItems(purchaseId), listFlow, reference, local) { purchase, items, list, ref, state ->
                PurchaseUiState(
                    isLoading = false,
                    notFound = purchase == null,
                    purchase = purchase,
                    items = items,
                    merchantName = purchase?.merchantId?.let { id -> ref.allMerchants.find { it.id == id }?.name },
                    listName = list?.first,
                    planned = list?.takeIf { purchase?.status == PurchaseStatus.ACTIVE }
                        ?.let { (_, listItems, skipped) -> PlannedPurchaseReducer.reduce(listItems, items, skipped) },
                    merchants = ref.merchants,
                    products = ref.products,
                    accounts = ref.accounts.filter { it.isActive || it.id == purchase?.accountId },
                    cashBalances = ref.cashBalances,
                    expenseCategories = ref.categories,
                    editor = state.editor,
                    finish = state.finish,
                    dialog = state.dialog,
                    removedItem = state.removedItem,
                    isClosed = state.isClosed,
                )
            }
                .catch { _uiState.update { it.copy(isLoading = false, notFound = true) } }
                .collect { _uiState.value = it }
        }
    }

    // --- Editor de itens ---

    fun updateEditor(transform: (ItemEditorState) -> ItemEditorState) =
        local.update { it.copy(editor = transform(it.editor).copy(errors = emptySet())) }

    fun onNameChange(value: String) = updateEditor { it.copy(name = value, productId = null) }

    fun onPriceChange(value: String) = updateEditor { it.copy(price = value, priceSource = PriceSource.MANUAL) }

    /** Liga o item a um produto do catálogo e sugere o último preço pago (ou o preço habitual). */
    fun onProductSelected(product: Product) {
        viewModelScope.launch {
            val last = purchaseRepository.lastPrice(product.id, _uiState.value.purchase?.merchantId)
            val price = last ?: product.defaultPriceMinor
            updateEditor { editor ->
                editor.copy(
                    name = product.name,
                    productId = product.id,
                    unit = editor.unit.ifBlank { product.unit.orEmpty() },
                    price = if (editor.price.isBlank() && price != null) formatAmountInput(price, 2) else editor.price,
                    priceSource = when {
                        editor.price.isNotBlank() -> editor.priceSource
                        last != null -> PriceSource.SUGGESTED
                        price != null -> PriceSource.CATALOG
                        else -> PriceSource.MANUAL
                    },
                )
            }
        }
    }

    /** Planeado: começa um item a partir de uma linha da lista (nome, quantidade e preço estimado). */
    fun onListItemSelected(item: ShoppingListItem) = local.update {
        it.copy(
            editor = ItemEditorState(
                name = item.name,
                quantity = item.quantity.formattedQuantity(),
                unit = item.unit.orEmpty(),
                price = item.estimatedPriceMinor?.let { price -> formatAmountInput(price, 2) }.orEmpty(),
                productId = item.productId,
                shoppingListItemId = item.id,
                priceSource = if (item.estimatedPriceMinor != null) PriceSource.SUGGESTED else PriceSource.MANUAL,
            ),
        )
    }

    fun onSkipListItem(item: ShoppingListItem, skipped: Boolean) {
        viewModelScope.launch { purchaseRepository.setSkipped(purchaseId, item.id, skipped) }
    }

    fun onEditItem(item: PurchaseItem) = local.update {
        it.copy(
            editor = ItemEditorState(
                id = item.id,
                name = item.name,
                quantity = item.quantity.formattedQuantity(),
                unit = item.unit.orEmpty(),
                price = formatAmountInput(item.unitPriceMinor, 2),
                discount = if (item.discountMinor > 0) formatAmountInput(item.discountMinor, 2) else "",
                productId = item.productId,
                shoppingListItemId = item.shoppingListItemId,
                priceSource = item.priceSource,
            ),
        )
    }

    fun onClearEditor() = local.update { it.copy(editor = ItemEditorState()) }

    fun onSaveItem() {
        val editor = local.value.editor
        val discount = if (editor.discount.isBlank()) 0L else parseOptionalPrice(editor.discount).getOrNull()
        viewModelScope.launch {
            val result = saveItem(
                PurchaseItemInput(
                    purchaseId = purchaseId,
                    id = editor.id,
                    name = editor.name,
                    quantity = PurchaseMath.parseQuantity(editor.quantity),
                    unitPriceMinor = if (editor.price.isBlank()) null else parseOptionalPrice(editor.price).getOrNull(),
                    discountMinor = discount,
                    unit = editor.unit,
                    productId = editor.productId,
                    shoppingListItemId = editor.shoppingListItemId,
                    priceSource = editor.priceSource,
                ),
            )
            when (result) {
                is SavePurchaseItemResult.Saved -> local.update { it.copy(editor = ItemEditorState()) }
                is SavePurchaseItemResult.Invalid -> local.update { it.copy(editor = it.editor.copy(errors = result.errors)) }
            }
        }
    }

    fun onRemoveItem(item: PurchaseItem) {
        viewModelScope.launch {
            purchaseRepository.removeItem(purchaseId, item.id)
            local.update { state ->
                state.copy(
                    removedItem = item,
                    editor = if (state.editor.id == item.id) ItemEditorState() else state.editor,
                )
            }
        }
    }

    fun onUndoRemove() {
        val item = local.value.removedItem ?: return
        viewModelScope.launch {
            purchaseRepository.saveItem(item)
            local.update { it.copy(removedItem = null) }
        }
    }

    fun onRemovedShown() = local.update { it.copy(removedItem = null) }

    // --- Cabeçalho ---

    fun onShowDialog(dialog: PurchaseDialog?) = local.update { it.copy(dialog = dialog) }

    fun onMerchantChosen(name: String?) {
        viewModelScope.launch {
            editHeader.setMerchant(purchaseId, name)
            local.update { it.copy(dialog = null) }
        }
    }

    /** Devolve `false` se o texto não for um valor válido. */
    fun onBudgetChosen(text: String): Boolean {
        val budget = parseOptionalPrice(text).getOrElse { return false }
        viewModelScope.launch {
            editHeader.setBudget(purchaseId, budget)
            local.update { it.copy(dialog = null) }
        }
        return true
    }

    fun onConfirmCancel() {
        viewModelScope.launch {
            cancelPurchase(purchaseId)
            local.update { it.copy(dialog = null, isClosed = true) }
        }
    }

    // --- Conclusão ---

    fun onStartFinish() {
        val state = _uiState.value
        val purchase = state.purchase ?: return
        val method = purchase.paymentMethod ?: PaymentMethod.MB
        val account = PaymentAccounts.defaultFor(method, state.accounts, purchase.accountId)
        local.update {
            it.copy(
                finish = FinishState(
                    method = method,
                    accountId = account?.id,
                    date = purchase.occurredAt.atZone(clock.zone).toLocalDate(),
                    categoryId = purchase.categoryId,
                ),
            )
        }
    }

    fun onDismissFinish() = local.update { it.copy(finish = null) }

    fun onMethodChange(method: PaymentMethod) = local.update { state ->
        val finish = state.finish ?: return@update state
        val account = PaymentAccounts.defaultFor(method, _uiState.value.accounts, _uiState.value.purchase?.accountId)
        state.copy(finish = finish.copy(method = method, accountId = account?.id, errors = emptySet(), noAccount = false))
    }

    fun onAccountChange(accountId: String) =
        local.update { it.copy(finish = it.finish?.copy(accountId = accountId, errors = emptySet(), noAccount = false)) }

    fun onDateChange(date: LocalDate) = local.update { it.copy(finish = it.finish?.copy(date = date)) }

    fun onCategoryChange(categoryId: String?) = local.update { it.copy(finish = it.finish?.copy(categoryId = categoryId, errors = emptySet())) }

    fun onCreateCashAccount() {
        viewModelScope.launch {
            val account = createCashAccount(_uiState.value.currency) ?: return@launch
            local.update { it.copy(finish = it.finish?.copy(method = PaymentMethod.CASH, accountId = account.id, noAccount = false)) }
        }
    }

    fun onConfirmFinish() {
        val finish = local.value.finish ?: return
        val accountId = finish.accountId ?: run {
            local.update { it.copy(finish = finish.copy(noAccount = true)) }
            return
        }
        local.update { it.copy(finish = finish.copy(isSaving = true)) }
        viewModelScope.launch {
            val result = completePurchase(
                CompletePurchaseInput(purchaseId, finish.method, accountId, finish.categoryId, finish.date),
            )
            when (result) {
                is CompletePurchaseResult.Completed -> local.update { it.copy(finish = null, isClosed = true) }
                is CompletePurchaseResult.Invalid -> local.update { it.copy(finish = finish.copy(errors = result.errors, isSaving = false)) }
            }
        }
    }

    companion object {
        const val PURCHASE_ID_ARG = "purchaseId"
    }
}
