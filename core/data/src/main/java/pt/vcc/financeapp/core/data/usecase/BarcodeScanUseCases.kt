package pt.vcc.financeapp.core.data.usecase

import java.math.BigDecimal
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import pt.vcc.financeapp.core.data.repository.CatalogRepository
import pt.vcc.financeapp.core.data.repository.PurchaseRepository
import pt.vcc.financeapp.core.data.repository.ShoppingListRepository
import pt.vcc.financeapp.core.model.PriceSource
import pt.vcc.financeapp.core.model.Product
import pt.vcc.financeapp.core.model.PurchaseItem
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.core.model.PurchaseType
import pt.vcc.financeapp.core.model.ShoppingListItem

/** Códigos de produto lidos pelo scanner: EAN-8, EAN-13 e UPC-A (guardado como EAN-13 com `0` à esquerda). */
object Gtin {
    /** Só dígitos e dígito de controlo válido; devolve `null` quando o código não é aceitável. */
    fun normalize(raw: String): String? {
        val digits = raw.trim()
        if (digits.isEmpty() || !digits.all(Char::isDigit)) return null
        val code = when (digits.length) {
            8, 13 -> digits
            12 -> "0$digits"
            else -> return null
        }
        return code.takeIf(::hasValidCheckDigit)
    }

    private fun hasValidCheckDigit(code: String): Boolean {
        val body = code.dropLast(1)
        // Da direita para a esquerda, os dígitos alternam peso 3 e 1 (começando por 3).
        val sum = body.reversed().mapIndexed { index, c -> (c - '0') * if (index % 2 == 0) 3 else 1 }.sum()
        return (10 - sum % 10) % 10 == code.last() - '0'
    }
}

/** Ignora o mesmo código lido de novo dentro da janela (a câmara lê o mesmo código várias vezes por segundo). */
class ScanDebouncer(private val windowMs: Long = 3_000) {
    private var lastCode: String? = null
    private var lastAt = Long.MIN_VALUE

    fun accept(code: String, nowMs: Long): Boolean {
        if (code == lastCode && nowMs - lastAt < windowMs) return false
        lastCode = code
        lastAt = nowMs
        return true
    }
}

sealed interface BarcodeLookup {
    /** Produto do catálogo; [listItem] é a linha da lista (compra planeada) que o código satisfaz. */
    data class Known(
        val code: String,
        val product: Product,
        val suggestion: PriceSuggestion?,
        val listItem: ShoppingListItem?,
        val pendingListItems: List<ShoppingListItem>,
    ) : BarcodeLookup

    /** Código válido mas sem produto: pode criar-se um (no catálogo ou temporário). */
    data class Unknown(val code: String, val pendingListItems: List<ShoppingListItem>) : BarcodeLookup
    data object InvalidCode : BarcodeLookup
    data object PurchaseNotFound : BarcodeLookup
}

/** Confirmação do utilizador depois do scan; nada é gravado antes disto. */
data class BarcodeConfirmInput(
    val purchaseId: String,
    val code: String,
    /** Produto conhecido; `null` cria um produto novo com [name] e o código lido. */
    val productId: String?,
    val name: String,
    val quantity: BigDecimal?,
    val unitPriceMinor: Long?,
    val priceSource: PriceSource = PriceSource.SCANNER,
    /** Produto novo: `true` fica no catálogo; `false` é temporário (só nesta compra). */
    val saveToCatalog: Boolean = false,
    val productCategoryId: String? = null,
    val shoppingListItemId: String? = null,
)

sealed interface BarcodeConfirmResult {
    data class Added(val item: PurchaseItem) : BarcodeConfirmResult
    data class Invalid(
        val itemErrors: Set<PurchaseItemError> = emptySet(),
        val catalogErrors: Set<CatalogValidationError> = emptySet(),
    ) : BarcodeConfirmResult
}

/**
 * Scanner nas compras rápidas e planeadas: procura o código no catálogo local (sem rede) e, depois da
 * confirmação, adiciona o item. Ler de novo um produto que já está na compra ao mesmo preço soma a quantidade.
 */
class BarcodeScanUseCase @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val catalogRepository: CatalogRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val suggestPrice: SuggestPriceUseCase,
    private val saveItem: SavePurchaseItemUseCase,
    private val saveProduct: SaveProductUseCase,
    private val clock: Clock,
) {
    suspend fun lookup(purchaseId: String, raw: String): BarcodeLookup {
        val code = Gtin.normalize(raw) ?: return BarcodeLookup.InvalidCode
        val detail = purchaseRepository.getDetail(purchaseId)
            ?.takeIf { it.purchase.status == PurchaseStatus.ACTIVE }
            ?: return BarcodeLookup.PurchaseNotFound
        val pending = pendingListItems(detail.purchase.type, detail.purchase.shoppingListId, detail.items)
        val product = findProduct(raw.trim(), code) ?: return BarcodeLookup.Unknown(code, pending)
        return BarcodeLookup.Known(
            code = code,
            product = product,
            suggestion = suggestPrice(product, detail.purchase.merchantId),
            listItem = pending.firstOrNull { it.productId == product.id },
            pendingListItems = pending,
        )
    }

    suspend fun confirm(input: BarcodeConfirmInput): BarcodeConfirmResult {
        val code = Gtin.normalize(input.code)
        val itemErrors = mutableSetOf<PurchaseItemError>()
        if (input.name.isBlank()) itemErrors += PurchaseItemError.BLANK_NAME
        if (input.productId == null && input.name.trim().length > MAX_PRODUCT_NAME_LENGTH) itemErrors += PurchaseItemError.NAME_TOO_LONG
        if (input.quantity == null || input.quantity <= BigDecimal.ZERO || input.quantity > SavePurchaseItemUseCase.MAX_QUANTITY) {
            itemErrors += PurchaseItemError.INVALID_QUANTITY
        }
        if (input.unitPriceMinor == null || input.unitPriceMinor < 0) itemErrors += PurchaseItemError.INVALID_PRICE
        val detail = purchaseRepository.getDetail(input.purchaseId)
        when {
            detail == null -> itemErrors += PurchaseItemError.PURCHASE_NOT_FOUND
            detail.purchase.status == PurchaseStatus.CANCELLED -> itemErrors += PurchaseItemError.PURCHASE_CANCELLED
            // Uma compra concluída é editada no ecrã da compra, que mantém o movimento sincronizado.
            detail.purchase.status != PurchaseStatus.ACTIVE -> itemErrors += PurchaseItemError.PURCHASE_NOT_FOUND
        }
        if (itemErrors.isNotEmpty()) return BarcodeConfirmResult.Invalid(itemErrors)
        val purchase = detail!!.purchase

        val product = when (input.productId) {
            null -> when (val created = createProduct(input, code)) {
                is CatalogSaveResult.Saved -> created.value
                is CatalogSaveResult.Invalid -> return BarcodeConfirmResult.Invalid(catalogErrors = created.errors)
            }
            else -> catalogRepository.getProduct(input.productId)
                ?: return BarcodeConfirmResult.Invalid(catalogErrors = setOf(CatalogValidationError.NOT_FOUND))
        }
        val listItemId = input.shoppingListItemId?.takeIf { purchase.type == PurchaseType.PLANNED }
        val existing = detail.items.firstOrNull {
            it.productId == product.id && it.unitPriceMinor == input.unitPriceMinor && it.discountMinor == 0L &&
                (listItemId == null || it.shoppingListItemId == listItemId)
        }
        val result = saveItem(
            PurchaseItemInput(
                purchaseId = input.purchaseId,
                id = existing?.id,
                name = existing?.name ?: input.name,
                quantity = existing?.quantity?.add(input.quantity) ?: input.quantity,
                unitPriceMinor = input.unitPriceMinor,
                unit = existing?.unit ?: product.unit,
                productId = product.id,
                shoppingListItemId = existing?.shoppingListItemId ?: listItemId,
                priceSource = input.priceSource,
            ),
        )
        return when (result) {
            is SavePurchaseItemResult.Saved -> BarcodeConfirmResult.Added(result.item)
            is SavePurchaseItemResult.Invalid -> BarcodeConfirmResult.Invalid(result.errors)
        }
    }

    private suspend fun findProduct(raw: String, code: String): Product? =
        catalogRepository.findByBarcode(code)
            ?: raw.takeIf { it != code }?.let { catalogRepository.findByBarcode(it) }
            // UPC-A guardado sem o zero à esquerda.
            ?: code.takeIf { it.length == 13 && it.startsWith('0') }?.let { catalogRepository.findByBarcode(it.drop(1)) }

    private suspend fun createProduct(input: BarcodeConfirmInput, code: String?): CatalogSaveResult<Product> {
        if (input.saveToCatalog) {
            return saveProduct(
                ProductInput(
                    name = input.name,
                    productCategoryId = input.productCategoryId,
                    defaultPriceMinor = input.unitPriceMinor,
                    barcode = code,
                ),
            )
        }
        val now = clock.instant()
        val product = Product(
            id = UUID.randomUUID().toString(),
            name = input.name.trim().replace(WHITESPACE, " "),
            barcode = code,
            defaultPriceMinor = input.unitPriceMinor,
            isTemporary = true,
            createdAt = now,
            updatedAt = now,
        )
        catalogRepository.saveProduct(product)
        return CatalogSaveResult.Saved(product)
    }

    /** Linhas da lista ainda por comprar (só em compras planeadas). */
    private suspend fun pendingListItems(type: PurchaseType, listId: String?, items: List<PurchaseItem>): List<ShoppingListItem> {
        if (type != PurchaseType.PLANNED || listId == null) return emptyList()
        val bought = items.mapNotNull { it.shoppingListItemId }.toSet()
        return shoppingListRepository.getItems(listId).filter { it.id !in bought }.sortedBy { it.position }
    }

    private companion object {
        /** Igual ao limite dos nomes do catálogo. */
        const val MAX_PRODUCT_NAME_LENGTH = 80
        val WHITESPACE = Regex("\\s+")
    }
}
