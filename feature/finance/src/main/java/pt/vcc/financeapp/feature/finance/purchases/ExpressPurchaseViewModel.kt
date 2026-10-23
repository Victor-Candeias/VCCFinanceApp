package pt.vcc.financeapp.feature.finance.purchases

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.data.repository.CatalogRepository
import pt.vcc.financeapp.core.data.repository.CategoryRepository
import pt.vcc.financeapp.core.data.repository.MerchantRepository
import pt.vcc.financeapp.core.data.repository.PurchaseRepository
import pt.vcc.financeapp.core.data.usecase.CancelPurchaseUseCase
import pt.vcc.financeapp.core.data.usecase.CatalogSaveResult
import pt.vcc.financeapp.core.data.usecase.CatalogValidationError
import pt.vcc.financeapp.core.data.usecase.CompletePurchaseError
import pt.vcc.financeapp.core.data.usecase.CompletePurchaseInput
import pt.vcc.financeapp.core.data.usecase.CompletePurchaseResult
import pt.vcc.financeapp.core.data.usecase.CompletePurchaseUseCase
import pt.vcc.financeapp.core.data.usecase.CreateCashAccountUseCase
import pt.vcc.financeapp.core.data.usecase.EditPurchaseHeaderUseCase
import pt.vcc.financeapp.core.data.usecase.ExpressAddResult
import pt.vcc.financeapp.core.data.usecase.ExpressPurchaseUseCase
import pt.vcc.financeapp.core.data.usecase.LastPaymentChoiceUseCase
import pt.vcc.financeapp.core.data.usecase.ObserveAccountBalancesUseCase
import pt.vcc.financeapp.core.data.usecase.PaymentChoice
import pt.vcc.financeapp.core.data.usecase.PurchaseItemError
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.Merchant
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.PaymentAccounts
import pt.vcc.financeapp.core.model.PaymentMethod
import pt.vcc.financeapp.core.model.Product
import pt.vcc.financeapp.core.model.ProductCategory
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.core.model.PurchaseItem
import pt.vcc.financeapp.core.model.PurchaseType
import pt.vcc.financeapp.core.model.TransactionType
import pt.vcc.financeapp.feature.finance.transactions.CategoryOption
import pt.vcc.financeapp.feature.finance.transactions.toOptions
import pt.vcc.financeapp.feature.finance.ui.formatAmountInput

data class ExpressPayment(val method: PaymentMethod, val accountId: String?, val categoryId: String? = null)

/**
 * Pedido de preço: produto sem preço conhecido ([productId]), produto temporário novo (sem ids)
 * ou alteração do preço de um item do rascunho ([itemId]).
 */
data class PricePrompt(
    val name: String,
    val productId: String? = null,
    val itemId: String? = null,
    val price: String = "",
    val errors: Set<PurchaseItemError> = emptySet(),
) {
    val isTemporary: Boolean get() = productId == null && itemId == null
}

enum class ExpressDialog { MERCHANT, NOTES, PAYMENT, CANCEL }

data class ExpressUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val purchase: Purchase? = null,
    val items: List<PurchaseItem> = emptyList(),
    val merchantName: String? = null,
    val merchants: List<Merchant> = emptyList(),
    /** Produtos do catálogo (sem temporários). */
    val products: List<Product> = emptyList(),
    val categories: List<ProductCategory> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val cashBalances: Map<String, Money> = emptyMap(),
    val expenseCategories: List<CategoryOption> = emptyList(),
    val query: String = "",
    val selectedCategoryId: String? = null,
    val payment: ExpressPayment = ExpressPayment(PaymentMethod.MB, null),
    val date: LocalDate = LocalDate.MIN,
    val time: LocalTime = LocalTime.MIDNIGHT,
    val dialog: ExpressDialog? = null,
    val pricePrompt: PricePrompt? = null,
    val promoteItem: PurchaseItem? = null,
    val promoteError: CatalogValidationError? = null,
    val itemError: PurchaseItemError? = null,
    val notesTooLong: Boolean = false,
    val errors: Set<CompletePurchaseError> = emptySet(),
    val noAccount: Boolean = false,
    val isSaving: Boolean = false,
    val isClosed: Boolean = false,
) {
    val currency: String get() = purchase?.currency ?: Money.DEFAULT_CURRENCY
    val total: Money get() = Money(purchase?.totalMinor ?: 0, currency)
    private val activeProducts: List<Product> get() = products.filter { it.isActive }
    val favorites: List<Product> get() = activeProducts.filter { it.isFavorite }

    /** Categorias com produtos ativos, pela ordem do catálogo. */
    val usedCategories: List<ProductCategory>
        get() = categories.filter { category -> category.isActive && activeProducts.any { it.productCategoryId == category.id } }

    /** Resultado da pesquisa ou, sem pesquisa, os produtos da categoria escolhida (todos sem categoria escolhida). */
    val visibleProducts: List<Product>
        get() {
            val key = Merchant.normalize(query)
            return if (key.isNotEmpty()) {
                activeProducts.filter { key in Merchant.normalize(it.name) }
            } else {
                activeProducts.filter { selectedCategoryId == null || it.productCategoryId == selectedCategoryId }
            }
        }

    val quantities: Map<String, BigDecimal>
        get() = items.mapNotNull { item -> item.productId?.let { it to item.quantity } }.toMap()

    /** Itens cujo produto foi criado de passagem (não está no catálogo). */
    val temporaryProductIds: Set<String>
        get() = items.mapNotNull(PurchaseItem::productId).filterTo(mutableSetOf()) { id -> products.none { it.id == id } }

    val canCreateTemporary: Boolean
        get() = query.isNotBlank() && activeProducts.none { Merchant.normalize(it.name) == Merchant.normalize(query) }

    val paymentAccount: Account? get() = payment.accountId?.let { id -> accounts.find { it.id == id } }

    val cashShortfall: Money?
        get() {
            if (payment.method != PaymentMethod.CASH) return null
            val balance = payment.accountId?.let(cashBalances::get) ?: return null
            return balance.takeIf { it.amountMinor < total.amountMinor }
        }
}

private data class ExpressLocal(
    val query: String = "",
    val selectedCategoryId: String? = null,
    val payment: ExpressPayment? = null,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val dialog: ExpressDialog? = null,
    val pricePrompt: PricePrompt? = null,
    val promoteItem: PurchaseItem? = null,
    val promoteError: CatalogValidationError? = null,
    val itemError: PurchaseItemError? = null,
    val notesTooLong: Boolean = false,
    val errors: Set<CompletePurchaseError> = emptySet(),
    val noAccount: Boolean = false,
    val isSaving: Boolean = false,
    val isClosed: Boolean = false,
)

private data class ExpressReference(
    val merchants: List<Merchant>,
    val products: List<Product>,
    val categories: List<ProductCategory>,
    val accounts: List<Account>,
    val cashBalances: Map<String, Money>,
    val expenseCategories: List<CategoryOption>,
)

/**
 * Despesa rápida: a compra `EXPRESS` em curso é o rascunho (gravado em Room a cada toque).
 * O pagamento vem pré-selecionado com o último usado, por isso uma despesa frequente fica em dois toques.
 */
@HiltViewModel
class ExpressPurchaseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    purchaseRepository: PurchaseRepository,
    merchantRepository: MerchantRepository,
    catalogRepository: CatalogRepository,
    categoryRepository: CategoryRepository,
    observeAccountBalances: ObserveAccountBalancesUseCase,
    lastPaymentChoice: LastPaymentChoiceUseCase,
    private val express: ExpressPurchaseUseCase,
    private val editHeader: EditPurchaseHeaderUseCase,
    private val cancelPurchase: CancelPurchaseUseCase,
    private val completePurchase: CompletePurchaseUseCase,
    private val createCashAccount: CreateCashAccountUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val purchaseId: String = checkNotNull(savedStateHandle[PurchaseViewModel.PURCHASE_ID_ARG]) { "purchaseId em falta" }
    private val local = MutableStateFlow(ExpressLocal())
    private val lastChoice = MutableStateFlow<PaymentChoice?>(null)
    private val _uiState = MutableStateFlow(ExpressUiState())
    val uiState: StateFlow<ExpressUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { lastChoice.value = lastPaymentChoice(PurchaseType.EXPRESS) }
        val reference = combine(
            merchantRepository.observeAll(),
            catalogRepository.observeProducts(),
            catalogRepository.observeCategories(),
            observeAccountBalances(),
            categoryRepository.observeAllCategories(),
        ) { merchants, products, categories, balances, financeCategories ->
            ExpressReference(
                merchants = merchants,
                products = products,
                categories = categories,
                accounts = balances.map { it.account },
                cashBalances = balances.associate { it.account.id to it.balance.booked },
                expenseCategories = financeCategories.filter { it.type == TransactionType.EXPENSE }.toOptions(),
            )
        }
        val localWithChoice = combine(local, lastChoice) { state, choice -> state to choice }
        viewModelScope.launch {
            combine(
                purchaseRepository.observePurchase(purchaseId),
                purchaseRepository.observeItems(purchaseId),
                reference,
                localWithChoice,
            ) { purchase, items, ref, (state, choice) ->
                val accounts = ref.accounts.filter { it.isActive || it.id == purchase?.accountId }
                val occurred = (purchase?.occurredAt ?: clock.instant()).atZone(clock.zone)
                ExpressUiState(
                    isLoading = false,
                    notFound = purchase == null,
                    purchase = purchase,
                    items = items,
                    merchantName = purchase?.merchantId?.let { id -> ref.merchants.find { it.id == id }?.name },
                    merchants = ref.merchants.filter { it.isActive },
                    products = ref.products,
                    categories = ref.categories,
                    accounts = accounts,
                    cashBalances = ref.cashBalances,
                    expenseCategories = ref.expenseCategories,
                    query = state.query,
                    selectedCategoryId = state.selectedCategoryId,
                    payment = state.payment ?: defaultPayment(purchase, choice, accounts),
                    date = state.date ?: occurred.toLocalDate(),
                    time = state.time ?: occurred.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                    dialog = state.dialog,
                    pricePrompt = state.pricePrompt,
                    promoteItem = state.promoteItem,
                    promoteError = state.promoteError,
                    itemError = state.itemError,
                    notesTooLong = state.notesTooLong,
                    errors = state.errors,
                    noAccount = state.noAccount,
                    isSaving = state.isSaving,
                    isClosed = state.isClosed,
                )
            }
                .catch { _uiState.update { it.copy(isLoading = false, notFound = true) } }
                .collect { _uiState.value = it }
        }
    }

    private fun defaultPayment(purchase: Purchase?, choice: PaymentChoice?, accounts: List<Account>): ExpressPayment {
        val method = purchase?.paymentMethod ?: choice?.method ?: PaymentMethod.MB
        val account = PaymentAccounts.defaultFor(method, accounts, purchase?.accountId ?: choice?.accountId)
        return ExpressPayment(method, account?.id, purchase?.categoryId)
    }

    // --- Catálogo ---

    fun onQueryChange(query: String) = local.update { it.copy(query = query) }

    fun onCategorySelected(categoryId: String?) = local.update { it.copy(selectedCategoryId = categoryId) }

    /** Toque num produto: soma uma unidade; sem preço conhecido pede-o. */
    fun onProductTap(product: Product) {
        viewModelScope.launch { handle(express.add(purchaseId, product.id), PricePrompt(product.name, productId = product.id)) }
    }

    fun onIncrement(item: PurchaseItem) {
        val productId = item.productId ?: return
        viewModelScope.launch { handle(express.add(purchaseId, productId), PricePrompt(item.name, productId = productId)) }
    }

    fun onDecrement(item: PurchaseItem) {
        viewModelScope.launch { express.decrement(purchaseId, item.id) }
    }

    private fun handle(result: ExpressAddResult, prompt: PricePrompt) {
        when (result) {
            is ExpressAddResult.Added -> local.update { it.copy(pricePrompt = null) }
            is ExpressAddResult.NeedsPrice -> local.update { it.copy(pricePrompt = prompt) }
            is ExpressAddResult.Invalid -> local.update { state ->
                if (state.pricePrompt != null) {
                    state.copy(pricePrompt = state.pricePrompt.copy(errors = result.errors))
                } else {
                    state.copy(itemError = result.errors.first())
                }
            }
            ExpressAddResult.ProductNotFound -> local.update { it.copy(itemError = PurchaseItemError.PURCHASE_NOT_FOUND, pricePrompt = null) }
        }
    }

    fun onItemErrorShown() = local.update { it.copy(itemError = null) }

    fun onCreateTemporary() {
        val name = local.value.query.trim()
        if (name.isNotEmpty()) local.update { it.copy(pricePrompt = PricePrompt(name)) }
    }

    fun onEditPrice(item: PurchaseItem) = local.update {
        it.copy(pricePrompt = PricePrompt(item.name, itemId = item.id, price = formatAmountInput(item.unitPriceMinor, 2)))
    }

    fun onPriceTextChange(text: String) = local.update { it.copy(pricePrompt = it.pricePrompt?.copy(price = text, errors = emptySet())) }

    fun onDismissPrice() = local.update { it.copy(pricePrompt = null) }

    fun onConfirmPrice() {
        val prompt = local.value.pricePrompt ?: return
        val price = parseOptionalPrice(prompt.price).getOrNull()
        if (price == null) {
            local.update { it.copy(pricePrompt = prompt.copy(errors = setOf(PurchaseItemError.INVALID_PRICE))) }
            return
        }
        viewModelScope.launch {
            val result = when {
                prompt.itemId != null -> express.changePrice(purchaseId, prompt.itemId, price)
                prompt.productId != null -> express.add(purchaseId, prompt.productId, price)
                else -> express.addTemporary(purchaseId, prompt.name, price)
            }
            if (prompt.isTemporary && result is ExpressAddResult.Added) local.update { it.copy(query = "") }
            handle(result, prompt)
        }
    }

    fun onPromote(item: PurchaseItem) = local.update { it.copy(promoteItem = item, promoteError = null) }

    fun onDismissPromote() = local.update { it.copy(promoteItem = null, promoteError = null) }

    fun onConfirmPromote(productCategoryId: String?) {
        val item = local.value.promoteItem ?: return
        viewModelScope.launch {
            when (val result = express.promoteTemporary(purchaseId, item.id, productCategoryId)) {
                is CatalogSaveResult.Saved -> local.update { it.copy(promoteItem = null, promoteError = null) }
                is CatalogSaveResult.Invalid -> local.update { it.copy(promoteError = result.errors.first()) }
            }
        }
    }

    // --- Cabeçalho ---

    fun onShowDialog(dialog: ExpressDialog?) = local.update { it.copy(dialog = dialog, notesTooLong = false) }

    fun onMerchantChosen(name: String?) {
        viewModelScope.launch {
            editHeader.setMerchant(purchaseId, name)
            local.update { it.copy(dialog = null) }
        }
    }

    fun onNotesChosen(notes: String) {
        if (notes.trim().length > EditPurchaseHeaderUseCase.MAX_NOTES_LENGTH) {
            local.update { it.copy(notesTooLong = true) }
            return
        }
        viewModelScope.launch {
            editHeader.setNotes(purchaseId, notes)
            local.update { it.copy(dialog = null) }
        }
    }

    fun onConfirmCancel() {
        viewModelScope.launch {
            cancelPurchase(purchaseId)
            local.update { it.copy(dialog = null, isClosed = true) }
        }
    }

    // --- Pagamento e gravação ---

    private fun updatePayment(transform: (ExpressPayment) -> ExpressPayment) = local.update {
        it.copy(payment = transform(_uiState.value.payment), errors = emptySet(), noAccount = false)
    }

    fun onMethodChange(method: PaymentMethod) = updatePayment { payment ->
        val account = PaymentAccounts.defaultFor(method, _uiState.value.accounts, lastChoice.value?.accountId)
        payment.copy(method = method, accountId = account?.id)
    }

    fun onAccountChange(accountId: String) = updatePayment { it.copy(accountId = accountId) }

    fun onCategoryChange(categoryId: String?) = updatePayment { it.copy(categoryId = categoryId) }

    fun onDateChange(date: LocalDate) = local.update { it.copy(date = date) }

    fun onTimeChange(time: LocalTime) = local.update { it.copy(time = time) }

    fun onCreateCashAccount() {
        viewModelScope.launch {
            val account = createCashAccount(_uiState.value.currency) ?: return@launch
            updatePayment { it.copy(method = PaymentMethod.CASH, accountId = account.id) }
        }
    }

    /** Grava a despesa com o pagamento escolhido; sem conta (ou com erros) abre a escolha do pagamento. */
    fun onSave() {
        val state = _uiState.value
        if (state.items.isEmpty() || state.isSaving) return
        val accountId = state.paymentAccount?.id ?: run {
            local.update { it.copy(dialog = ExpressDialog.PAYMENT, noAccount = true) }
            return
        }
        val changedWhen = local.value.date != null || local.value.time != null
        local.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = completePurchase(
                CompletePurchaseInput(
                    purchaseId = purchaseId,
                    paymentMethod = state.payment.method,
                    accountId = accountId,
                    categoryId = state.payment.categoryId,
                    date = state.date.takeIf { changedWhen },
                    time = state.time.takeIf { changedWhen },
                ),
            )
            when (result) {
                is CompletePurchaseResult.Completed -> local.update { it.copy(isSaving = false, dialog = null, isClosed = true) }
                is CompletePurchaseResult.Invalid -> local.update {
                    it.copy(isSaving = false, errors = result.errors, dialog = ExpressDialog.PAYMENT)
                }
            }
        }
    }
}
