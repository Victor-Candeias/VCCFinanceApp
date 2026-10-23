package pt.vcc.financeapp.core.data.testing

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import pt.vcc.financeapp.core.data.repository.CatalogRepository
import pt.vcc.financeapp.core.data.repository.MerchantRepository
import pt.vcc.financeapp.core.data.repository.PurchaseRepository
import pt.vcc.financeapp.core.data.repository.ShoppingListRepository
import pt.vcc.financeapp.core.model.Merchant
import pt.vcc.financeapp.core.model.Product
import pt.vcc.financeapp.core.model.ProductCategory
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.core.model.PurchaseDetail
import pt.vcc.financeapp.core.model.PurchaseItem
import pt.vcc.financeapp.core.model.PurchaseMath
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.core.model.PurchaseType
import pt.vcc.financeapp.core.model.ShoppingList
import pt.vcc.financeapp.core.model.ShoppingListItem
import pt.vcc.financeapp.core.model.Transaction

/** Imita o `PurchaseDao`: o total é recalculado a partir dos itens e os movimentos vão para [transactions]. */
class FakePurchaseRepository(
    private val transactions: FakeTransactionRepository = FakeTransactionRepository(),
    private val now: () -> Instant = { Instant.EPOCH },
) : PurchaseRepository {
    val purchases = MutableStateFlow<List<Purchase>>(emptyList())
    val items = MutableStateFlow<List<PurchaseItem>>(emptyList())
    val skipped = MutableStateFlow<Set<Pair<String, String>>>(emptySet())

    private fun recalculate(purchaseId: String) = purchases.update { list ->
        list.map { purchase ->
            if (purchase.id == purchaseId) {
                purchase.copy(totalMinor = PurchaseMath.total(items.value.filter { it.purchaseId == purchaseId }), updatedAt = now())
            } else {
                purchase
            }
        }
    }

    private fun removeTransaction(id: String) = transactions.transactions.update { list -> list.filterNot { it.id == id } }

    private fun upsert(purchase: Purchase) = purchases.update { list -> list.filterNot { it.id == purchase.id } + purchase }

    private fun completedIn(from: Instant, to: Instant) =
        purchases.value.filter { it.status == PurchaseStatus.COMPLETED && it.occurredAt >= from && it.occurredAt < to }

    override fun observeActive(type: PurchaseType): Flow<Purchase?> =
        purchases.map { list -> list.filter { it.type == type && it.status == PurchaseStatus.ACTIVE }.maxByOrNull { it.updatedAt } }

    override fun observeAllActive(): Flow<List<Purchase>> =
        purchases.map { list -> list.filter { it.status == PurchaseStatus.ACTIVE }.sortedByDescending { it.updatedAt } }

    override fun observePurchase(id: String): Flow<Purchase?> = purchases.map { list -> list.find { it.id == id } }

    override fun observeItems(purchaseId: String): Flow<List<PurchaseItem>> =
        items.map { list -> list.filter { it.purchaseId == purchaseId }.sortedBy { it.position } }

    override fun observeCompleted(): Flow<List<Purchase>> =
        purchases.map { list -> list.filter { it.status == PurchaseStatus.COMPLETED }.sortedByDescending { it.occurredAt } }

    override fun observeCompletedInPeriod(fromInclusive: Instant, toExclusive: Instant): Flow<List<Purchase>> =
        purchases.map { completedIn(fromInclusive, toExclusive) }

    override fun observeCompletedItemsInPeriod(fromInclusive: Instant, toExclusive: Instant): Flow<List<PurchaseItem>> =
        items.map { list ->
            val ids = completedIn(fromInclusive, toExclusive).map { it.id }.toSet()
            list.filter { it.purchaseId in ids }
        }

    override fun observeSkipped(purchaseId: String): Flow<Set<String>> =
        skipped.map { set -> set.filter { it.first == purchaseId }.map { it.second }.toSet() }

    override suspend fun getActive(type: PurchaseType): Purchase? =
        purchases.value.filter { it.type == type && it.status == PurchaseStatus.ACTIVE }.maxByOrNull { it.updatedAt }

    override suspend fun getById(id: String): Purchase? = purchases.value.find { it.id == id }

    override suspend fun getDetail(id: String): PurchaseDetail? =
        getById(id)?.let { PurchaseDetail(it, items.value.filter { item -> item.purchaseId == id }.sortedBy(PurchaseItem::position)) }

    override suspend fun getByTransactionId(transactionId: String): Purchase? =
        purchases.value.find { it.transactionId == transactionId }

    override suspend fun getAllDetails(): List<PurchaseDetail> = purchases.value.mapNotNull { getDetail(it.id) }

    override suspend fun create(purchase: Purchase, items: List<PurchaseItem>) {
        upsert(purchase)
        this.items.update { list -> list.filterNot { existing -> items.any { it.id == existing.id } } + items }
        recalculate(purchase.id)
    }

    override suspend fun update(purchase: Purchase) {
        upsert(purchase)
        recalculate(purchase.id)
    }

    override suspend fun saveItem(item: PurchaseItem) {
        items.update { list -> list.filterNot { it.id == item.id } + item }
        recalculate(item.purchaseId)
    }

    override suspend fun removeItem(purchaseId: String, itemId: String) {
        items.update { list -> list.filterNot { it.id == itemId } }
        recalculate(purchaseId)
    }

    override suspend fun setSkipped(purchaseId: String, shoppingListItemId: String, skipped: Boolean) =
        this.skipped.update { if (skipped) it + (purchaseId to shoppingListItemId) else it - (purchaseId to shoppingListItemId) }

    override suspend fun persistWithTransaction(
        purchase: Purchase,
        items: List<PurchaseItem>,
        transaction: Transaction?,
        obsoleteTransactionId: String?,
    ) {
        transaction?.let { tx -> transactions.transactions.update { list -> list.filterNot { it.id == tx.id } + tx } }
        upsert(purchase)
        this.items.update { list -> list.filterNot { it.purchaseId == purchase.id } + items }
        obsoleteTransactionId?.takeIf { it != purchase.transactionId }?.let(::removeTransaction)
    }

    override suspend fun delete(purchase: Purchase) {
        purchases.update { list -> list.filterNot { it.id == purchase.id } }
        items.update { list -> list.filterNot { it.purchaseId == purchase.id } }
        purchase.transactionId?.let(::removeTransaction)
    }

    override suspend fun lastPrice(productId: String, merchantId: String?): Long? {
        val completed = purchases.value.filter { it.status == PurchaseStatus.COMPLETED }.associateBy { it.id }
        val candidates = items.value
            .filter { it.productId == productId && it.purchaseId in completed }
            .sortedByDescending { completed.getValue(it.purchaseId).occurredAt }
        return merchantId?.let { id -> candidates.firstOrNull { completed.getValue(it.purchaseId).merchantId == id } }?.unitPriceMinor
            ?: candidates.firstOrNull()?.unitPriceMinor
    }
}

class FakeCatalogRepository(
    categories: List<ProductCategory> = emptyList(),
    products: List<Product> = emptyList(),
) : CatalogRepository {
    val categories = MutableStateFlow(categories)
    val products = MutableStateFlow(products)

    override fun observeCategories(): Flow<List<ProductCategory>> = categories.map { list -> list.sortedBy { it.sortOrder } }
    override fun observeProducts(): Flow<List<Product>> =
        products.map { list -> list.filterNot { it.isTemporary }.sortedWith(compareByDescending<Product> { it.isFavorite }.thenBy { it.sortOrder }) }
    override suspend fun getCategory(id: String): ProductCategory? = categories.value.find { it.id == id }
    override suspend fun getProduct(id: String): Product? = products.value.find { it.id == id }
    override suspend fun findByBarcode(barcode: String): Product? =
        products.value.filter { it.barcode == barcode }.sortedBy { it.isTemporary }.firstOrNull()
    override suspend fun saveCategory(category: ProductCategory) =
        categories.update { list -> list.filterNot { it.id == category.id } + category }
    override suspend fun saveProduct(product: Product) = products.update { list -> list.filterNot { it.id == product.id } + product }
    override suspend fun insertIfAbsent(categories: List<ProductCategory>, products: List<Product>): Int {
        val missingCategories = categories.filter { candidate -> this.categories.value.none { it.id == candidate.id } }
        val missingProducts = products.filter { candidate -> this.products.value.none { it.id == candidate.id } }
        this.categories.update { it + missingCategories }
        this.products.update { it + missingProducts }
        return missingCategories.size + missingProducts.size
    }
}

class FakeShoppingListRepository : ShoppingListRepository {
    val lists = MutableStateFlow<List<ShoppingList>>(emptyList())
    val items = MutableStateFlow<List<ShoppingListItem>>(emptyList())

    override fun observeLists(): Flow<List<ShoppingList>> = lists
    override fun observeList(id: String): Flow<ShoppingList?> = lists.map { list -> list.find { it.id == id } }
    override fun observeItems(listId: String): Flow<List<ShoppingListItem>> =
        items.map { list -> list.filter { it.listId == listId }.sortedBy { it.position } }
    override suspend fun getList(id: String): ShoppingList? = lists.value.find { it.id == id }
    override suspend fun getItems(listId: String): List<ShoppingListItem> =
        items.value.filter { it.listId == listId }.sortedBy { it.position }
    override suspend fun save(list: ShoppingList, items: List<ShoppingListItem>) {
        lists.update { all -> all.filterNot { it.id == list.id } + list }
        saveItems(items)
    }
    override suspend fun saveItems(items: List<ShoppingListItem>) =
        this.items.update { list -> list.filterNot { existing -> items.any { it.id == existing.id } } + items }
    override suspend fun deleteItem(id: String) = items.update { list -> list.filterNot { it.id == id } }
    override suspend fun delete(id: String) {
        lists.update { list -> list.filterNot { it.id == id } }
        items.update { list -> list.filterNot { it.listId == id } }
    }
}

class FakeMerchantRepository(initial: List<Merchant> = emptyList()) : MerchantRepository {
    val merchants = MutableStateFlow(initial)

    override fun observeActive(): Flow<List<Merchant>> = merchants.map { list -> list.filter { it.isActive }.sortedBy { it.name } }
    override fun observeAll(): Flow<List<Merchant>> =
        merchants.map { list -> list.sortedWith(compareByDescending<Merchant> { it.isActive }.thenBy { it.name }) }
    override suspend fun getById(id: String): Merchant? = merchants.value.find { it.id == id }
    override suspend fun findByName(name: String): Merchant? =
        Merchant.normalize(name).let { normalized -> merchants.value.find { it.normalizedName == normalized } }
    override suspend fun findOrCreate(name: String): Merchant {
        val normalized = Merchant.normalize(name)
        merchants.value.find { it.normalizedName == normalized }?.let { existing ->
            val active = existing.copy(isActive = true)
            update(active)
            return active
        }
        val merchant = Merchant(UUID.randomUUID().toString(), name.trim(), normalized, createdAt = Instant.EPOCH)
        merchants.update { it + merchant }
        return merchant
    }
    override suspend fun update(merchant: Merchant) =
        merchants.update { list -> list.map { if (it.id == merchant.id) merchant.copy(normalizedName = Merchant.normalize(merchant.name)) else it } }
}
