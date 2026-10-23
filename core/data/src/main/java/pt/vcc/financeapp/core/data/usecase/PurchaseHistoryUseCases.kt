package pt.vcc.financeapp.core.data.usecase

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import pt.vcc.financeapp.core.data.repository.CatalogRepository
import pt.vcc.financeapp.core.data.repository.PurchaseRepository
import pt.vcc.financeapp.core.model.PaymentMethod
import pt.vcc.financeapp.core.model.PriceSource
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.core.model.PurchaseItem
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.core.model.PurchaseType

enum class HistoryPeriod { ALL, THIS_MONTH, LAST_MONTH, LAST_90_DAYS, THIS_YEAR }

/** Filtros do histórico de compras concluídas; `null` = sem filtro. */
data class PurchaseHistoryFilter(
    val type: PurchaseType? = null,
    val method: PaymentMethod? = null,
    val accountId: String? = null,
    val merchantId: String? = null,
    val period: HistoryPeriod = HistoryPeriod.ALL,
) {
    val isActive: Boolean get() = this != PurchaseHistoryFilter()

    fun matches(purchase: Purchase, today: LocalDate, zone: ZoneId): Boolean {
        if (purchase.status != PurchaseStatus.COMPLETED) return false
        if (type != null && purchase.type != type) return false
        if (method != null && purchase.paymentMethod != method) return false
        if (accountId != null && purchase.accountId != accountId) return false
        if (merchantId != null && purchase.merchantId != merchantId) return false
        val date = purchase.occurredAt.atZone(zone).toLocalDate()
        return when (period) {
            HistoryPeriod.ALL -> true
            HistoryPeriod.THIS_MONTH -> date.year == today.year && date.month == today.month
            HistoryPeriod.LAST_MONTH -> today.minusMonths(1).let { date.year == it.year && date.month == it.month }
            HistoryPeriod.LAST_90_DAYS -> !date.isBefore(today.minusDays(89)) && !date.isAfter(today)
            HistoryPeriod.THIS_YEAR -> date.year == today.year
        }
    }
}

sealed interface RepeatPurchaseResult {
    data class Started(val purchase: Purchase) : RepeatPurchaseResult

    /** Já há uma compra em curso (com artigos) do mesmo tipo; só pode haver uma. */
    data class Conflict(val activePurchase: Purchase) : RepeatPurchaseResult
    data object NotFound : RepeatPurchaseResult
}

/**
 * Repete uma compra concluída: nova compra em curso com os mesmos artigos, quantidades e estabelecimento, e os
 * preços sugeridos atuais (último pago → preço habitual; o preço antigo só quando o produto já não existe).
 * Uma compra planeada é repetida como compra rápida (a lista continua disponível para iniciar outra compra).
 */
class RepeatPurchaseUseCase @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val catalogRepository: CatalogRepository,
    private val suggestPrice: SuggestPriceUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(purchaseId: String): RepeatPurchaseResult {
        val source = purchaseRepository.getDetail(purchaseId)
            ?.takeIf { it.purchase.status == PurchaseStatus.COMPLETED }
            ?: return RepeatPurchaseResult.NotFound
        val type = if (source.purchase.type == PurchaseType.PLANNED) PurchaseType.QUICK else source.purchase.type
        val active = purchaseRepository.getActive(type)
        val activeItems = active?.let { purchaseRepository.getDetail(it.id)?.items }.orEmpty()
        if (active != null && activeItems.isNotEmpty()) return RepeatPurchaseResult.Conflict(active)

        val now = clock.instant()
        val merchantId = source.purchase.merchantId
        val purchase = active?.copy(merchantId = merchantId, updatedAt = now) ?: Purchase(
            id = UUID.randomUUID().toString(),
            type = type,
            status = PurchaseStatus.ACTIVE,
            occurredAt = now,
            merchantId = merchantId,
            createdAt = now,
            updatedAt = now,
        )
        val items = source.items.sortedBy(PurchaseItem::position).mapIndexed { index, item ->
            val product = item.productId?.let { catalogRepository.getProduct(it) }
            val suggestion = product?.let { suggestPrice(it, merchantId) }
            val categoryName = product?.productCategoryId?.let { catalogRepository.getCategory(it) }?.name ?: item.categoryName
            item.copy(
                id = UUID.randomUUID().toString(),
                purchaseId = purchase.id,
                unitPriceMinor = suggestion?.priceMinor ?: item.unitPriceMinor,
                priceSource = suggestion?.source ?: PriceSource.MANUAL,
                discountMinor = 0,
                productId = product?.id,
                shoppingListItemId = null,
                categoryName = categoryName,
                position = index,
            )
        }
        if (active != null) {
            purchaseRepository.update(purchase)
            items.forEach { purchaseRepository.saveItem(it) }
        } else {
            purchaseRepository.create(purchase, items)
        }
        return RepeatPurchaseResult.Started(purchaseRepository.getById(purchase.id) ?: purchase)
    }
}
