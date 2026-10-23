package pt.vcc.financeapp.core.data.usecase

import java.math.BigDecimal
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import pt.vcc.financeapp.core.data.repository.CatalogRepository
import pt.vcc.financeapp.core.data.repository.PurchaseRepository
import pt.vcc.financeapp.core.data.repository.ShoppingListRepository
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.core.model.PurchaseItem
import pt.vcc.financeapp.core.model.PurchaseMath
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.core.model.PurchaseType
import pt.vcc.financeapp.core.model.ShoppingList
import pt.vcc.financeapp.core.model.ShoppingListItem

enum class ShoppingListError { NOT_FOUND, IN_USE, BLANK_NAME, NAME_TOO_LONG, INVALID_BUDGET, INVALID_QUANTITY, INVALID_PRICE }

sealed interface SaveShoppingListResult {
    data class Saved(val list: ShoppingList) : SaveShoppingListResult
    data class Invalid(val errors: Set<ShoppingListError>) : SaveShoppingListResult
}

/**
 * A compra planeada em curso lê os itens da lista; enquanto existir, a lista fica bloqueada para
 * que alterações posteriores não mudem a compra.
 */
private suspend fun PurchaseRepository.isListInUse(listId: String): Boolean =
    getActive(PurchaseType.PLANNED)?.shoppingListId == listId

private val WHITESPACE = Regex("\\s+")
private fun String.cleanName() = trim().replace(WHITESPACE, " ")

/** Cria ou altera nome/orçamento de uma lista (orçamento vazio ou 0 = sem orçamento). */
class SaveShoppingListUseCase @Inject constructor(
    private val listRepository: ShoppingListRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(id: String?, name: String, budgetMinor: Long?): SaveShoppingListResult {
        val errors = mutableSetOf<ShoppingListError>()
        val existing = id?.let { listRepository.getList(it) }
        if (id != null && existing == null) errors += ShoppingListError.NOT_FOUND
        val clean = name.cleanName()
        if (clean.isEmpty()) errors += ShoppingListError.BLANK_NAME
        if (clean.length > MAX_NAME_LENGTH) errors += ShoppingListError.NAME_TOO_LONG
        if (budgetMinor != null && budgetMinor < 0) errors += ShoppingListError.INVALID_BUDGET
        if (errors.isNotEmpty()) return SaveShoppingListResult.Invalid(errors)

        val now = clock.instant()
        val list = existing?.copy(name = clean, budgetMinor = budgetMinor?.takeIf { it > 0 }, updatedAt = now)
            ?: ShoppingList(UUID.randomUUID().toString(), clean, budgetMinor?.takeIf { it > 0 }, createdAt = now, updatedAt = now)
        listRepository.save(list)
        return SaveShoppingListResult.Saved(list)
    }

    companion object {
        const val MAX_NAME_LENGTH = 80
    }
}

data class ShoppingListItemInput(
    val listId: String,
    val id: String? = null,
    val name: String,
    /** `null` quando o texto escrito não é uma quantidade positiva. */
    val quantity: BigDecimal?,
    val unit: String? = null,
    /** Preço unitário estimado; `null` = sem estimativa. */
    val estimatedPriceMinor: Long? = null,
    val productId: String? = null,
)

sealed interface SaveShoppingListItemResult {
    data class Saved(val item: ShoppingListItem) : SaveShoppingListItemResult
    data class Invalid(val errors: Set<ShoppingListError>) : SaveShoppingListItemResult
}

class SaveShoppingListItemUseCase @Inject constructor(
    private val listRepository: ShoppingListRepository,
    private val purchaseRepository: PurchaseRepository,
    private val catalogRepository: CatalogRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(input: ShoppingListItemInput): SaveShoppingListItemResult {
        val errors = mutableSetOf<ShoppingListError>()
        val list = listRepository.getList(input.listId)
        when {
            list == null -> errors += ShoppingListError.NOT_FOUND
            purchaseRepository.isListInUse(list.id) -> errors += ShoppingListError.IN_USE
        }
        val name = input.name.cleanName()
        if (name.isEmpty()) errors += ShoppingListError.BLANK_NAME
        if (name.length > SavePurchaseItemUseCase.MAX_NAME_LENGTH) errors += ShoppingListError.NAME_TOO_LONG
        if (input.quantity == null || input.quantity.signum() <= 0 || input.quantity > SavePurchaseItemUseCase.MAX_QUANTITY) {
            errors += ShoppingListError.INVALID_QUANTITY
        }
        if (input.estimatedPriceMinor != null && input.estimatedPriceMinor < 0) errors += ShoppingListError.INVALID_PRICE
        if (errors.isNotEmpty() || list == null) return SaveShoppingListItemResult.Invalid(errors)

        val items = listRepository.getItems(list.id)
        val existing = input.id?.let { id -> items.find { it.id == id } }
        val item = ShoppingListItem(
            id = existing?.id ?: UUID.randomUUID().toString(),
            listId = list.id,
            name = name,
            quantity = input.quantity!!.stripTrailingZeros(),
            unit = input.unit?.trim()?.takeIf(String::isNotEmpty),
            estimatedPriceMinor = input.estimatedPriceMinor,
            productId = input.productId?.let { catalogRepository.getProduct(it) }?.id,
            position = existing?.position ?: ((items.maxOfOrNull(ShoppingListItem::position) ?: -1) + 1),
        )
        listRepository.save(list.copy(updatedAt = clock.instant()), listOf(item))
        return SaveShoppingListItemResult.Saved(item)
    }
}

/** Remoção, reordenação, duplicação, arquivo e eliminação de listas e dos seus itens. */
class ManageShoppingListUseCase @Inject constructor(
    private val listRepository: ShoppingListRepository,
    private val purchaseRepository: PurchaseRepository,
    private val clock: Clock,
) {
    suspend fun isInUse(listId: String): Boolean = purchaseRepository.isListInUse(listId)

    suspend fun removeItem(listId: String, itemId: String): Boolean {
        if (isInUse(listId)) return false
        listRepository.deleteItem(itemId)
        return true
    }

    /** Troca o item com o vizinho de cima ([up]) ou de baixo e renumera as posições. */
    suspend fun moveItem(listId: String, itemId: String, up: Boolean): Boolean {
        if (isInUse(listId)) return false
        val items = listRepository.getItems(listId).toMutableList()
        val index = items.indexOfFirst { it.id == itemId }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in items.indices) return false
        items[index] = items[target].also { items[target] = items[index] }
        listRepository.saveItems(items.mapIndexed { position, item -> item.copy(position = position) })
        return true
    }

    /** Nova lista "Nome (cópia)" com os mesmos itens (ids novos), não arquivada. */
    suspend fun duplicate(listId: String, copySuffix: String = " (cópia)"): ShoppingList? {
        val list = listRepository.getList(listId) ?: return null
        val now = clock.instant()
        val copy = list.copy(
            id = UUID.randomUUID().toString(),
            name = (list.name + copySuffix).take(SaveShoppingListUseCase.MAX_NAME_LENGTH),
            isArchived = false,
            createdAt = now,
            updatedAt = now,
        )
        val items = listRepository.getItems(listId).map { it.copy(id = UUID.randomUUID().toString(), listId = copy.id) }
        listRepository.save(copy, items)
        return copy
    }

    suspend fun setArchived(listId: String, archived: Boolean): Boolean {
        val list = listRepository.getList(listId) ?: return false
        if (archived && isInUse(listId)) return false
        listRepository.save(list.copy(isArchived = archived, updatedAt = clock.instant()))
        return true
    }

    /** As compras já feitas a partir da lista mantêm-se (ficam sem lista associada). */
    suspend fun delete(listId: String): Boolean {
        if (isInUse(listId)) return false
        listRepository.delete(listId)
        return true
    }
}

sealed interface StartPlannedPurchaseResult {
    data class Started(val purchase: Purchase) : StartPlannedPurchaseResult

    /** Já existe uma compra planeada em curso a partir de outra lista. */
    data class Conflict(val active: Purchase) : StartPlannedPurchaseResult
    data object ListNotFound : StartPlannedPurchaseResult
}

/**
 * Inicia (ou retoma) a compra `PLANNED` de uma lista, copiando o orçamento. Só pode haver uma
 * compra planeada ativa: com [replaceActive] a de outra lista é cancelada; sem ele devolve [StartPlannedPurchaseResult.Conflict].
 */
class StartPlannedPurchaseUseCase @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val listRepository: ShoppingListRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(listId: String, replaceActive: Boolean = false): StartPlannedPurchaseResult {
        val list = listRepository.getList(listId) ?: return StartPlannedPurchaseResult.ListNotFound
        val active = purchaseRepository.getActive(PurchaseType.PLANNED)
        val now = clock.instant()
        if (active != null) {
            if (active.shoppingListId == listId) return StartPlannedPurchaseResult.Started(active)
            if (!replaceActive) return StartPlannedPurchaseResult.Conflict(active)
            purchaseRepository.update(active.copy(status = PurchaseStatus.CANCELLED, updatedAt = now))
        }
        val purchase = Purchase(
            id = UUID.randomUUID().toString(),
            type = PurchaseType.PLANNED,
            status = PurchaseStatus.ACTIVE,
            occurredAt = now,
            shoppingListId = list.id,
            budgetMinor = list.budgetMinor,
            createdAt = now,
            updatedAt = now,
        )
        purchaseRepository.create(purchase)
        return StartPlannedPurchaseResult.Started(purchase)
    }
}

/** Situação de uma compra planeada face à lista: por comprar, adicionados, ignorados e extras. */
data class PlannedProgress(
    val toBuy: List<ShoppingListItem>,
    val added: List<PurchaseItem>,
    val skipped: List<ShoppingListItem>,
    val extras: List<PurchaseItem>,
    /** Soma de quantidade × preço estimado dos itens da lista que têm estimativa. */
    val estimateMinor: Long,
    val plannedCount: Int,
) {
    /** Itens da lista já tratados (comprados ou ignorados). */
    val handledCount: Int get() = plannedCount - toBuy.size
}

object PlannedPurchaseReducer {
    /**
     * Só o estado "ignorado" é persistido; os restantes derivam dos itens da compra. Um item da lista
     * comprado conta como adicionado mesmo que também esteja marcado como ignorado; itens da compra
     * sem item da lista correspondente são extras.
     */
    fun reduce(listItems: List<ShoppingListItem>, purchaseItems: List<PurchaseItem>, skippedIds: Set<String>): PlannedProgress {
        val listIds = listItems.mapTo(mutableSetOf()) { it.id }
        val (added, extras) = purchaseItems.partition { it.shoppingListItemId != null && it.shoppingListItemId in listIds }
        val boughtIds = added.mapTo(mutableSetOf()) { it.shoppingListItemId }
        val remaining = listItems.filter { it.id !in boughtIds }
        return PlannedProgress(
            toBuy = remaining.filter { it.id !in skippedIds },
            added = added,
            skipped = remaining.filter { it.id in skippedIds },
            extras = extras,
            estimateMinor = listItems.sumOf { item ->
                item.estimatedPriceMinor?.let { PurchaseMath.itemTotal(item.quantity, it, 0) } ?: 0L
            },
            plannedCount = listItems.size,
        )
    }
}
