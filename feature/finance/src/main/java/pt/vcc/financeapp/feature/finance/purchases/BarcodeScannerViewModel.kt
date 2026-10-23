package pt.vcc.financeapp.feature.finance.purchases

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.data.ocr.OcrLine
import pt.vcc.financeapp.core.data.ocr.PriceCandidate
import pt.vcc.financeapp.core.data.ocr.PriceCandidateParser
import pt.vcc.financeapp.core.data.ocr.ProductNameExtractor
import pt.vcc.financeapp.core.data.repository.CatalogRepository
import pt.vcc.financeapp.core.data.repository.PurchaseRepository
import pt.vcc.financeapp.core.data.usecase.BarcodeConfirmInput
import pt.vcc.financeapp.core.data.usecase.BarcodeConfirmResult
import pt.vcc.financeapp.core.data.usecase.BarcodeLookup
import pt.vcc.financeapp.core.data.usecase.BarcodeScanUseCase
import pt.vcc.financeapp.core.data.usecase.CatalogValidationError
import pt.vcc.financeapp.core.data.usecase.PurchaseItemError
import pt.vcc.financeapp.core.data.usecase.ScanDebouncer
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.PriceSource
import pt.vcc.financeapp.core.model.ProductCategory
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.core.model.PurchaseMath
import pt.vcc.financeapp.core.model.ShoppingListItem
import pt.vcc.financeapp.feature.finance.ui.formatAmountInput

/** Item lido à espera de confirmação (texto tal como escrito). Nada é gravado antes de [BarcodeScannerViewModel.onConfirm]. */
data class ScanDraft(
    val code: String,
    val productId: String? = null,
    val name: String = "",
    val quantity: String = "1",
    val price: String = "",
    val priceSource: PriceSource = PriceSource.MANUAL,
    val saveToCatalog: Boolean = false,
    val productCategoryId: String? = null,
    val listItemId: String? = null,
    val pendingListItems: List<ShoppingListItem> = emptyList(),
    val itemErrors: Set<PurchaseItemError> = emptySet(),
    val catalogErrors: Set<CatalogValidationError> = emptySet(),
    val isSaving: Boolean = false,
) {
    val isNewProduct: Boolean get() = productId == null
}

enum class ScanMessage { INVALID_CODE, ADDED, PURCHASE_NOT_FOUND }

/**
 * Leitura do preço de uma etiqueta para o artigo em confirmação. Com [candidates] a `null` a câmara está pronta a
 * fotografar; depois o utilizador escolhe um candidato (nunca é aplicado sozinho).
 */
data class PriceReading(
    val capturing: Boolean = false,
    val candidates: List<PriceCandidate>? = null,
    val suggested: PriceCandidate? = null,
    val names: List<String> = emptyList(),
    val failed: Boolean = false,
)

data class BarcodeScannerUiState(
    val purchase: Purchase? = null,
    val itemCount: Int = 0,
    val categories: List<ProductCategory> = emptyList(),
    val manualCode: String = "",
    val draft: ScanDraft? = null,
    val priceReading: PriceReading? = null,
    val message: ScanMessage? = null,
    val lastAddedName: String? = null,
) {
    val total: Money get() = Money(purchase?.totalMinor ?: 0, purchase?.currency ?: Money.DEFAULT_CURRENCY)
}

private data class ScannerLocal(
    val manualCode: String = "",
    val draft: ScanDraft? = null,
    val priceReading: PriceReading? = null,
    val message: ScanMessage? = null,
    val lastAddedName: String? = null,
)

/** Scanner de códigos de barras de uma compra rápida ou planeada (catálogo local, sem rede). */
@HiltViewModel
class BarcodeScannerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    purchaseRepository: PurchaseRepository,
    catalogRepository: CatalogRepository,
    private val scan: BarcodeScanUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val purchaseId: String = checkNotNull(savedStateHandle[PurchaseViewModel.PURCHASE_ID_ARG]) { "purchaseId em falta" }
    private val debouncer = ScanDebouncer()
    private val local = MutableStateFlow(ScannerLocal())
    private val _uiState = MutableStateFlow(BarcodeScannerUiState())
    val uiState: StateFlow<BarcodeScannerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                purchaseRepository.observePurchase(purchaseId),
                purchaseRepository.observeItems(purchaseId),
                catalogRepository.observeCategories(),
                local,
            ) { purchase, items, categories, state ->
                BarcodeScannerUiState(
                    purchase = purchase,
                    itemCount = items.size,
                    categories = categories.filter { it.isActive },
                    manualCode = state.manualCode,
                    draft = state.draft,
                    priceReading = state.priceReading,
                    message = state.message,
                    lastAddedName = state.lastAddedName,
                )
            }
                .catch { local.update { it.copy(message = ScanMessage.PURCHASE_NOT_FOUND) } }
                .collect { _uiState.value = it }
        }
    }

    /** Código lido pela câmara: ignorado enquanto há uma confirmação aberta ou se for repetido logo a seguir. */
    fun onCodeDetected(code: String) {
        if (local.value.draft != null || !debouncer.accept(code, clock.millis())) return
        lookup(code)
    }

    fun onManualCodeChange(value: String) =
        local.update { it.copy(manualCode = value.filter(Char::isDigit).take(MAX_CODE_LENGTH), message = null) }

    fun onSubmitManualCode() {
        val code = local.value.manualCode
        if (code.isNotEmpty()) lookup(code)
    }

    private fun lookup(code: String) {
        viewModelScope.launch {
            when (val result = scan.lookup(purchaseId, code)) {
                is BarcodeLookup.Known -> local.update {
                    it.copy(
                        message = null,
                        draft = ScanDraft(
                            code = result.code,
                            productId = result.product.id,
                            name = result.product.name,
                            price = result.suggestion?.let { s -> formatAmountInput(s.priceMinor, 2) }.orEmpty(),
                            priceSource = result.suggestion?.source ?: PriceSource.MANUAL,
                            listItemId = result.listItem?.id,
                            pendingListItems = result.pendingListItems,
                        ),
                    )
                }
                is BarcodeLookup.Unknown -> local.update {
                    it.copy(message = null, draft = ScanDraft(code = result.code, pendingListItems = result.pendingListItems))
                }
                BarcodeLookup.InvalidCode -> local.update { it.copy(message = ScanMessage.INVALID_CODE) }
                BarcodeLookup.PurchaseNotFound -> local.update { it.copy(message = ScanMessage.PURCHASE_NOT_FOUND) }
            }
        }
    }

    fun updateDraft(transform: (ScanDraft) -> ScanDraft) = local.update { state ->
        state.copy(draft = state.draft?.let(transform)?.copy(itemErrors = emptySet(), catalogErrors = emptySet()))
    }

    fun onPriceChange(value: String) = updateDraft { it.copy(price = value, priceSource = PriceSource.MANUAL) }

    /** Planeado: associa o item a uma linha da lista; com um produto novo, o nome da linha pré-preenche o nome. */
    fun onListItemChosen(item: ShoppingListItem?) = updateDraft { draft ->
        draft.copy(
            listItemId = item?.id,
            name = if (draft.isNewProduct && draft.name.isBlank() && item != null) item.name else draft.name,
        )
    }

    fun onDismissDraft() = local.update { it.copy(draft = null, priceReading = null) }

    /** Prepara a câmara para fotografar a etiqueta do preço do artigo em confirmação. */
    fun onReadPrice() {
        if (local.value.draft != null) local.update { it.copy(priceReading = PriceReading()) }
    }

    fun onPriceCaptureStarted() = local.update { state ->
        state.copy(priceReading = state.priceReading?.copy(capturing = true, failed = false))
    }

    fun onPriceTextRead(lines: List<OcrLine>) {
        val result = PriceCandidateParser.parse(lines)
        val names = if (local.value.draft?.isNewProduct == true) ProductNameExtractor.candidates(lines) else emptyList()
        local.update { state ->
            state.copy(
                priceReading = state.priceReading?.let {
                    if (result.candidates.isEmpty() && names.isEmpty()) {
                        it.copy(capturing = false, failed = true)
                    } else {
                        it.copy(capturing = false, candidates = result.candidates, suggested = result.suggested, names = names)
                    }
                },
            )
        }
    }

    fun onPriceCaptureFailed() = local.update { state ->
        state.copy(priceReading = state.priceReading?.copy(capturing = false, failed = true))
    }

    fun onPriceCandidateChosen(candidate: PriceCandidate) = local.update { state ->
        state.copy(
            priceReading = null,
            draft = state.draft?.copy(
                price = formatAmountInput(candidate.cents, 2),
                priceSource = PriceSource.OCR,
                itemErrors = emptySet(),
            ),
        )
    }

    fun onNameCandidateChosen(name: String) = local.update { state ->
        state.copy(
            draft = state.draft?.copy(name = name, itemErrors = emptySet()),
            priceReading = state.priceReading?.copy(names = emptyList()),
        )
    }

    /** Volta a fotografar sem perder o artigo em confirmação. */
    fun onRetryPriceReading() = local.update { state -> state.copy(priceReading = state.priceReading?.let { PriceReading() }) }

    fun onCancelPriceReading() = local.update { it.copy(priceReading = null) }

    fun onConfirm() {
        val draft = local.value.draft ?: return
        local.update { it.copy(draft = draft.copy(isSaving = true)) }
        viewModelScope.launch {
            val result = scan.confirm(
                BarcodeConfirmInput(
                    purchaseId = purchaseId,
                    code = draft.code,
                    productId = draft.productId,
                    name = draft.name,
                    quantity = PurchaseMath.parseQuantity(draft.quantity),
                    unitPriceMinor = if (draft.price.isBlank()) null else parseOptionalPrice(draft.price).getOrNull(),
                    priceSource = draft.priceSource,
                    saveToCatalog = draft.saveToCatalog,
                    productCategoryId = draft.productCategoryId,
                    shoppingListItemId = draft.listItemId,
                ),
            )
            when (result) {
                is BarcodeConfirmResult.Added -> local.update {
                    it.copy(draft = null, manualCode = "", message = ScanMessage.ADDED, lastAddedName = result.item.name)
                }
                is BarcodeConfirmResult.Invalid -> local.update {
                    it.copy(draft = draft.copy(itemErrors = result.itemErrors, catalogErrors = result.catalogErrors, isSaving = false))
                }
            }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null) }

    private companion object {
        const val MAX_CODE_LENGTH = 13
    }
}
