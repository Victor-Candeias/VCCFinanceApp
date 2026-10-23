package pt.vcc.financeapp.core.data.usecase

import java.math.BigDecimal
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import pt.vcc.financeapp.core.data.repository.CatalogRepository
import pt.vcc.financeapp.core.data.repository.PurchaseRepository
import pt.vcc.financeapp.core.model.PaymentMethod
import pt.vcc.financeapp.core.model.PriceSource
import pt.vcc.financeapp.core.model.Product
import pt.vcc.financeapp.core.model.PurchaseItem
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.core.model.PurchaseType

data class PriceSuggestion(val priceMinor: Long, val source: PriceSource)

/**
 * Preço sugerido para um produto: último pago no estabelecimento → último pago em qualquer lado → preço habitual.
 * Só conta o histórico de compras concluídas; alterar o preço de um item não muda o produto nem o histórico.
 */
class SuggestPriceUseCase @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
) {
    suspend operator fun invoke(product: Product, merchantId: String?): PriceSuggestion? {
        purchaseRepository.lastPrice(product.id, merchantId)?.let { return PriceSuggestion(it, PriceSource.SUGGESTED) }
        return product.defaultPriceMinor?.let { PriceSuggestion(it, PriceSource.CATALOG) }
    }
}

sealed interface ExpressAddResult {
    data class Added(val item: PurchaseItem) : ExpressAddResult

    /** Não há preço conhecido: o utilizador tem de o indicar. */
    data class NeedsPrice(val product: Product) : ExpressAddResult
    data class Invalid(val errors: Set<PurchaseItemError>) : ExpressAddResult
    data object ProductNotFound : ExpressAddResult
}

/**
 * Despesa rápida (`EXPRESS`): cada toque num produto soma uma unidade. A compra em curso é o rascunho,
 * gravado em Room a cada alteração e retomado a partir do hub.
 */
class ExpressPurchaseUseCase @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val catalogRepository: CatalogRepository,
    private val suggestPrice: SuggestPriceUseCase,
    private val saveItem: SavePurchaseItemUseCase,
    private val saveProduct: SaveProductUseCase,
    private val clock: Clock,
) {
    /** Soma uma unidade ao item do produto ou cria-o com o preço sugerido ([priceMinor] força um preço). */
    suspend fun add(purchaseId: String, productId: String, priceMinor: Long? = null): ExpressAddResult {
        val detail = purchaseRepository.getDetail(purchaseId)
            ?: return ExpressAddResult.Invalid(setOf(PurchaseItemError.PURCHASE_NOT_FOUND))
        if (detail.purchase.status == PurchaseStatus.CANCELLED) return ExpressAddResult.Invalid(setOf(PurchaseItemError.PURCHASE_CANCELLED))
        if (priceMinor != null && priceMinor < 0) return ExpressAddResult.Invalid(setOf(PurchaseItemError.INVALID_PRICE))
        val existing = detail.items.firstOrNull { it.productId == productId }
        if (existing != null && priceMinor == null) {
            val quantity = existing.quantity + BigDecimal.ONE
            if (quantity > SavePurchaseItemUseCase.MAX_QUANTITY) return ExpressAddResult.Invalid(setOf(PurchaseItemError.INVALID_QUANTITY))
            val updated = existing.copy(quantity = quantity)
            purchaseRepository.saveItem(updated)
            return ExpressAddResult.Added(updated)
        }
        val product = catalogRepository.getProduct(productId)
            ?: return ExpressAddResult.ProductNotFound
        val price = priceMinor?.let { PriceSuggestion(it, PriceSource.MANUAL) }
            ?: suggestPrice(product, detail.purchase.merchantId)
            ?: return ExpressAddResult.NeedsPrice(product)
        val result = saveItem(
            PurchaseItemInput(
                purchaseId = purchaseId,
                id = existing?.id,
                name = product.name,
                quantity = existing?.quantity ?: BigDecimal.ONE,
                unitPriceMinor = price.priceMinor,
                discountMinor = existing?.discountMinor ?: 0,
                unit = product.unit,
                productId = product.id,
                priceSource = price.source,
            ),
        )
        return result.toAddResult()
    }

    /** Tira uma unidade; com uma unidade (ou menos) o item é removido. */
    suspend fun decrement(purchaseId: String, itemId: String): Boolean {
        val item = purchaseRepository.getDetail(purchaseId)?.items?.find { it.id == itemId } ?: return false
        if (item.quantity <= BigDecimal.ONE) {
            purchaseRepository.removeItem(purchaseId, itemId)
        } else {
            purchaseRepository.saveItem(item.copy(quantity = item.quantity - BigDecimal.ONE))
        }
        return true
    }

    /** Muda o preço unitário só deste item (o produto e as compras anteriores ficam iguais). */
    suspend fun changePrice(purchaseId: String, itemId: String, priceMinor: Long?): ExpressAddResult {
        if (priceMinor == null || priceMinor < 0) return ExpressAddResult.Invalid(setOf(PurchaseItemError.INVALID_PRICE))
        val item = purchaseRepository.getDetail(purchaseId)?.items?.find { it.id == itemId }
            ?: return ExpressAddResult.Invalid(setOf(PurchaseItemError.PURCHASE_NOT_FOUND))
        val updated = item.copy(unitPriceMinor = priceMinor, priceSource = PriceSource.MANUAL)
        purchaseRepository.saveItem(updated)
        return ExpressAddResult.Added(updated)
    }

    /** Produto temporário (fora do catálogo) criado a partir da pesquisa e adicionado com uma unidade. */
    suspend fun addTemporary(purchaseId: String, name: String, priceMinor: Long?): ExpressAddResult {
        val errors = mutableSetOf<PurchaseItemError>()
        val trimmed = name.trim().replace(WHITESPACE, " ")
        if (trimmed.isEmpty()) errors += PurchaseItemError.BLANK_NAME
        if (trimmed.length > MAX_PRODUCT_NAME_LENGTH) errors += PurchaseItemError.NAME_TOO_LONG
        if (priceMinor == null || priceMinor < 0) errors += PurchaseItemError.INVALID_PRICE
        val purchase = purchaseRepository.getById(purchaseId)
        when {
            purchase == null -> errors += PurchaseItemError.PURCHASE_NOT_FOUND
            purchase.status == PurchaseStatus.CANCELLED -> errors += PurchaseItemError.PURCHASE_CANCELLED
        }
        if (errors.isNotEmpty()) return ExpressAddResult.Invalid(errors)

        val now = clock.instant()
        val product = Product(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            defaultPriceMinor = priceMinor,
            isTemporary = true,
            createdAt = now,
            updatedAt = now,
        )
        catalogRepository.saveProduct(product)
        return saveItem(
            PurchaseItemInput(
                purchaseId = purchaseId,
                name = trimmed,
                quantity = BigDecimal.ONE,
                unitPriceMinor = priceMinor,
                productId = product.id,
                priceSource = PriceSource.MANUAL,
            ),
        ).toAddResult()
    }

    /**
     * Passa o produto temporário de um item para o catálogo, com o preço pago como preço habitual,
     * e atualiza a categoria guardada no item.
     */
    suspend fun promoteTemporary(purchaseId: String, itemId: String, productCategoryId: String?): CatalogSaveResult<Product> {
        val item = purchaseRepository.getDetail(purchaseId)?.items?.find { it.id == itemId }
        val product = item?.productId?.let { catalogRepository.getProduct(it) }?.takeIf { it.isTemporary }
        if (item == null || product == null) return CatalogSaveResult.Invalid(setOf(CatalogValidationError.NOT_FOUND))
        val result = saveProduct(
            ProductInput(
                id = product.id,
                name = product.name,
                productCategoryId = productCategoryId,
                defaultPriceMinor = item.unitPriceMinor,
                unit = item.unit,
            ),
        )
        if (result is CatalogSaveResult.Saved) {
            val categoryName = productCategoryId?.let { catalogRepository.getCategory(it) }?.name
            purchaseRepository.saveItem(item.copy(categoryName = categoryName))
        }
        return result
    }

    private fun SavePurchaseItemResult.toAddResult(): ExpressAddResult = when (this) {
        is SavePurchaseItemResult.Saved -> ExpressAddResult.Added(item)
        is SavePurchaseItemResult.Invalid -> ExpressAddResult.Invalid(errors)
    }

    private companion object {
        const val MAX_PRODUCT_NAME_LENGTH = 80
        val WHITESPACE = Regex("\\s+")
    }
}

data class PaymentChoice(val method: PaymentMethod, val accountId: String?)

/** Último pagamento usado, preferindo uma compra do mesmo [type]; serve para pré-selecionar MB/Dinheiro e a conta. */
class LastPaymentChoiceUseCase @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
) {
    suspend operator fun invoke(type: PurchaseType? = null): PaymentChoice? {
        val paid = purchaseRepository.observeCompleted().first()
            .filter { it.paymentMethod != null }
            .sortedByDescending { it.completedAt ?: it.updatedAt }
        val last = paid.firstOrNull { type == null || it.type == type } ?: paid.firstOrNull() ?: return null
        return PaymentChoice(last.paymentMethod!!, last.accountId)
    }
}
