package pt.vcc.financeapp.core.data.repository

import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.vcc.financeapp.core.data.mapper.asDomain
import pt.vcc.financeapp.core.data.mapper.asEntity
import pt.vcc.financeapp.core.database.dao.MerchantDao
import pt.vcc.financeapp.core.database.dao.PurchaseDao
import pt.vcc.financeapp.core.database.entity.MerchantEntity
import pt.vcc.financeapp.core.database.entity.ProductCategoryEntity
import pt.vcc.financeapp.core.database.entity.ProductEntity
import pt.vcc.financeapp.core.database.entity.PurchaseEntity
import pt.vcc.financeapp.core.database.entity.PurchaseItemEntity
import pt.vcc.financeapp.core.database.entity.PurchaseSkippedItemEntity
import pt.vcc.financeapp.core.database.entity.ShoppingListEntity
import pt.vcc.financeapp.core.database.entity.ShoppingListItemEntity
import pt.vcc.financeapp.core.model.Merchant
import pt.vcc.financeapp.core.model.Product
import pt.vcc.financeapp.core.model.ProductCategory
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.core.model.PurchaseDetail
import pt.vcc.financeapp.core.model.PurchaseItem
import pt.vcc.financeapp.core.model.PurchaseType
import pt.vcc.financeapp.core.model.ShoppingList
import pt.vcc.financeapp.core.model.ShoppingListItem
import pt.vcc.financeapp.core.model.Transaction

internal class LocalPurchaseRepository @Inject constructor(
    private val dao: PurchaseDao,
    private val clock: Clock,
) : PurchaseRepository {
    override fun observeActive(type: PurchaseType): Flow<Purchase?> = dao.observeActive(type).map { it?.asDomain() }

    override fun observeAllActive(): Flow<List<Purchase>> = dao.observeAllActive().map { it.map(PurchaseEntity::asDomain) }

    override fun observePurchase(id: String): Flow<Purchase?> = dao.observePurchase(id).map { it?.asDomain() }

    override fun observeItems(purchaseId: String): Flow<List<PurchaseItem>> =
        dao.observeItems(purchaseId).map { it.map(PurchaseItemEntity::asDomain) }

    override fun observeCompleted(): Flow<List<Purchase>> = dao.observeCompleted().map { it.map(PurchaseEntity::asDomain) }

    override fun observeCompletedInPeriod(fromInclusive: Instant, toExclusive: Instant): Flow<List<Purchase>> =
        dao.observeCompletedInPeriod(fromInclusive.toEpochMilli(), toExclusive.toEpochMilli())
            .map { it.map(PurchaseEntity::asDomain) }

    override fun observeCompletedItemsInPeriod(fromInclusive: Instant, toExclusive: Instant): Flow<List<PurchaseItem>> =
        dao.observeCompletedItemsInPeriod(fromInclusive.toEpochMilli(), toExclusive.toEpochMilli())
            .map { it.map(PurchaseItemEntity::asDomain) }

    override fun observeSkipped(purchaseId: String): Flow<Set<String>> = dao.observeSkipped(purchaseId).map { it.toSet() }

    override suspend fun getActive(type: PurchaseType): Purchase? = dao.getActive(type)?.asDomain()

    override suspend fun getById(id: String): Purchase? = dao.getPurchase(id)?.asDomain()

    override suspend fun getDetail(id: String): PurchaseDetail? {
        val purchase = dao.getPurchase(id) ?: return null
        return PurchaseDetail(purchase.asDomain(), dao.getItems(id).map(PurchaseItemEntity::asDomain))
    }

    override suspend fun getByTransactionId(transactionId: String): Purchase? =
        dao.getPurchaseByTransaction(transactionId)?.asDomain()

    override suspend fun getAllDetails(): List<PurchaseDetail> {
        val items = dao.getAllItems().map(PurchaseItemEntity::asDomain).groupBy { it.purchaseId }
        return dao.getAllPurchases().map { PurchaseDetail(it.asDomain(), items[it.id].orEmpty().sortedBy(PurchaseItem::position)) }
    }

    override suspend fun create(purchase: Purchase, items: List<PurchaseItem>) =
        dao.createWithItems(purchase.asEntity(), items.map(PurchaseItem::asEntity))

    override suspend fun update(purchase: Purchase) {
        dao.upsertPurchase(purchase.asEntity())
        dao.recalculateTotal(purchase.id, clock.millis())
    }

    override suspend fun saveItem(item: PurchaseItem) = dao.saveItem(item.asEntity(), clock.millis())

    override suspend fun removeItem(purchaseId: String, itemId: String) = dao.removeItem(purchaseId, itemId, clock.millis())

    override suspend fun setSkipped(purchaseId: String, shoppingListItemId: String, skipped: Boolean) {
        if (skipped) {
            dao.insertSkipped(PurchaseSkippedItemEntity(purchaseId, shoppingListItemId))
        } else {
            dao.deleteSkipped(purchaseId, shoppingListItemId)
        }
    }

    override suspend fun persistWithTransaction(
        purchase: Purchase,
        items: List<PurchaseItem>,
        transaction: Transaction?,
        obsoleteTransactionId: String?,
    ) = dao.persistWithTransaction(
        purchase.asEntity(),
        items.map(PurchaseItem::asEntity),
        transaction?.asEntity(),
        obsoleteTransactionId,
    )

    override suspend fun delete(purchase: Purchase) = dao.deleteWithTransaction(purchase.id, purchase.transactionId)

    override suspend fun lastPrice(productId: String, merchantId: String?): Long? =
        merchantId?.let { dao.lastPriceAtMerchant(productId, it) } ?: dao.lastPrice(productId)
}

internal class LocalCatalogRepository @Inject constructor(
    private val dao: PurchaseDao,
) : CatalogRepository {
    override fun observeCategories(): Flow<List<ProductCategory>> =
        dao.observeProductCategories().map { it.map(ProductCategoryEntity::asDomain) }

    override fun observeProducts(): Flow<List<Product>> = dao.observeCatalogProducts().map { it.map(ProductEntity::asDomain) }

    override suspend fun getCategory(id: String): ProductCategory? = dao.getProductCategory(id)?.asDomain()

    override suspend fun getProduct(id: String): Product? = dao.getProduct(id)?.asDomain()

    override suspend fun findByBarcode(barcode: String): Product? = dao.getProductsByBarcode(barcode).firstOrNull()?.asDomain()

    override suspend fun saveCategory(category: ProductCategory) = dao.upsertProductCategory(category.asEntity())

    override suspend fun saveProduct(product: Product) = dao.upsertProduct(product.asEntity())

    override suspend fun insertIfAbsent(categories: List<ProductCategory>, products: List<Product>): Int {
        val insertedCategories = dao.insertProductCategoriesIgnoring(categories.map(ProductCategory::asEntity)).count { it != -1L }
        val insertedProducts = dao.insertProductsIgnoring(products.map(Product::asEntity)).count { it != -1L }
        return insertedCategories + insertedProducts
    }
}

internal class LocalShoppingListRepository @Inject constructor(
    private val dao: PurchaseDao,
) : ShoppingListRepository {
    override fun observeLists(): Flow<List<ShoppingList>> = dao.observeShoppingLists().map { it.map(ShoppingListEntity::asDomain) }

    override fun observeList(id: String): Flow<ShoppingList?> = dao.observeShoppingList(id).map { it?.asDomain() }

    override fun observeItems(listId: String): Flow<List<ShoppingListItem>> =
        dao.observeShoppingListItems(listId).map { it.map(ShoppingListItemEntity::asDomain) }

    override suspend fun getList(id: String): ShoppingList? = dao.getShoppingList(id)?.asDomain()

    override suspend fun getItems(listId: String): List<ShoppingListItem> =
        dao.getShoppingListItems(listId).map(ShoppingListItemEntity::asDomain)

    override suspend fun save(list: ShoppingList, items: List<ShoppingListItem>) =
        dao.insertShoppingListWithItems(list.asEntity(), items.map(ShoppingListItem::asEntity))

    override suspend fun saveItems(items: List<ShoppingListItem>) = dao.upsertShoppingListItems(items.map(ShoppingListItem::asEntity))

    override suspend fun deleteItem(id: String) = dao.deleteShoppingListItem(id)

    override suspend fun delete(id: String) = dao.deleteShoppingList(id)
}

internal class LocalMerchantRepository @Inject constructor(
    private val dao: MerchantDao,
    private val clock: Clock,
) : MerchantRepository {
    override fun observeActive(): Flow<List<Merchant>> = dao.observeActive().map { it.map(MerchantEntity::asDomain) }

    override fun observeAll(): Flow<List<Merchant>> = dao.observeAll().map { it.map(MerchantEntity::asDomain) }

    override suspend fun getById(id: String): Merchant? = dao.getById(id)?.asDomain()

    override suspend fun findByName(name: String): Merchant? = dao.getByNormalizedName(Merchant.normalize(name))?.asDomain()

    override suspend fun findOrCreate(name: String): Merchant {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Nome do estabelecimento vazio" }
        val normalized = Merchant.normalize(trimmed)
        dao.getByNormalizedName(normalized)?.let { existing ->
            if (!existing.isActive) dao.update(existing.copy(isActive = true))
            return existing.copy(isActive = true).asDomain()
        }
        val merchant = Merchant(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            normalizedName = normalized,
            createdAt = clock.instant(),
        )
        dao.insert(merchant.asEntity())
        return merchant
    }

    override suspend fun update(merchant: Merchant) =
        dao.update(merchant.copy(normalizedName = Merchant.normalize(merchant.name)).asEntity())
}
