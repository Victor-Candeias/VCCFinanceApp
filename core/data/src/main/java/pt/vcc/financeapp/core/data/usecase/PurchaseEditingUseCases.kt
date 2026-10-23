package pt.vcc.financeapp.core.data.usecase

import java.math.BigDecimal
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import pt.vcc.financeapp.core.data.repository.CatalogRepository
import pt.vcc.financeapp.core.data.repository.MerchantRepository
import pt.vcc.financeapp.core.data.repository.PurchaseRepository
import pt.vcc.financeapp.core.model.PriceSource
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.core.model.PurchaseItem
import pt.vcc.financeapp.core.model.PurchaseStatus

data class PurchaseItemInput(
    val purchaseId: String,
    val id: String? = null,
    val name: String,
    /** `null` quando o texto escrito não é uma quantidade positiva. */
    val quantity: BigDecimal?,
    /** `null` quando o texto escrito não é um preço válido. */
    val unitPriceMinor: Long?,
    val discountMinor: Long? = 0,
    val unit: String? = null,
    val productId: String? = null,
    val shoppingListItemId: String? = null,
    val priceSource: PriceSource = PriceSource.MANUAL,
)

enum class PurchaseItemError { PURCHASE_NOT_FOUND, PURCHASE_CANCELLED, BLANK_NAME, NAME_TOO_LONG, INVALID_QUANTITY, INVALID_PRICE, INVALID_DISCOUNT }

sealed interface SavePurchaseItemResult {
    data class Saved(val item: PurchaseItem) : SavePurchaseItemResult
    data class Invalid(val errors: Set<PurchaseItemError>) : SavePurchaseItemResult
}

/** Adiciona ou altera um item; o total da compra é recalculado pelo repositório. */
class SavePurchaseItemUseCase @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val catalogRepository: CatalogRepository,
) {
    suspend operator fun invoke(input: PurchaseItemInput): SavePurchaseItemResult {
        val errors = mutableSetOf<PurchaseItemError>()
        val detail = purchaseRepository.getDetail(input.purchaseId)
        when {
            detail == null -> errors += PurchaseItemError.PURCHASE_NOT_FOUND
            detail.purchase.status == PurchaseStatus.CANCELLED -> errors += PurchaseItemError.PURCHASE_CANCELLED
        }
        val name = input.name.trim().replace(WHITESPACE, " ")
        if (name.isEmpty()) errors += PurchaseItemError.BLANK_NAME
        if (name.length > MAX_NAME_LENGTH) errors += PurchaseItemError.NAME_TOO_LONG
        if (input.quantity == null || input.quantity.signum() <= 0 || input.quantity > MAX_QUANTITY) errors += PurchaseItemError.INVALID_QUANTITY
        if (input.unitPriceMinor == null || input.unitPriceMinor < 0) errors += PurchaseItemError.INVALID_PRICE
        if (input.discountMinor == null || input.discountMinor < 0) errors += PurchaseItemError.INVALID_DISCOUNT
        if (errors.isNotEmpty() || detail == null) return SavePurchaseItemResult.Invalid(errors)

        val existing = input.id?.let { id -> detail.items.find { it.id == id } }
        val product = input.productId?.let { catalogRepository.getProduct(it) }
        val categoryName = product?.productCategoryId?.let { catalogRepository.getCategory(it) }?.name
        val item = PurchaseItem(
            id = existing?.id ?: input.id ?: UUID.randomUUID().toString(),
            purchaseId = input.purchaseId,
            name = name,
            quantity = input.quantity!!.stripTrailingZeros(),
            unitPriceMinor = input.unitPriceMinor!!,
            discountMinor = input.discountMinor!!,
            unit = input.unit?.trim()?.takeIf(String::isNotEmpty),
            productId = product?.id,
            shoppingListItemId = input.shoppingListItemId ?: existing?.shoppingListItemId,
            categoryName = categoryName,
            priceSource = input.priceSource,
            position = existing?.position ?: ((detail.items.maxOfOrNull(PurchaseItem::position) ?: -1) + 1),
        )
        purchaseRepository.saveItem(item)
        return SavePurchaseItemResult.Saved(item)
    }

    companion object {
        const val MAX_NAME_LENGTH = 120
        val MAX_QUANTITY: BigDecimal = BigDecimal(99_999)
        private val WHITESPACE = Regex("\\s+")
    }
}

/** Abandona uma compra em curso: fica `CANCELLED` (para o histórico) e não gera movimento. */
class CancelPurchaseUseCase @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(purchaseId: String): Boolean {
        val purchase = purchaseRepository.getById(purchaseId)?.takeIf { it.status == PurchaseStatus.ACTIVE } ?: return false
        purchaseRepository.update(purchase.copy(status = PurchaseStatus.CANCELLED, updatedAt = clock.instant()))
        return true
    }
}

/** Alterações ao cabeçalho de uma compra: estabelecimento e orçamento. */
class EditPurchaseHeaderUseCase @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val merchantRepository: MerchantRepository,
    private val clock: Clock,
) {
    /** Nome vazio retira o estabelecimento; um nome novo cria-o. */
    suspend fun setMerchant(purchaseId: String, merchantName: String?): Purchase? {
        val purchase = purchaseRepository.getById(purchaseId) ?: return null
        val merchantId = merchantName?.trim()?.takeIf(String::isNotEmpty)?.let { merchantRepository.findOrCreate(it).id }
        return purchase.copy(merchantId = merchantId, updatedAt = clock.instant()).also { purchaseRepository.update(it) }
    }

    /** `null` ou 0 retira o orçamento; valores negativos são rejeitados. */
    suspend fun setBudget(purchaseId: String, budgetMinor: Long?): Purchase? {
        if (budgetMinor != null && budgetMinor < 0) return null
        val purchase = purchaseRepository.getById(purchaseId) ?: return null
        return purchase.copy(budgetMinor = budgetMinor?.takeIf { it > 0 }, updatedAt = clock.instant())
            .also { purchaseRepository.update(it) }
    }
}
