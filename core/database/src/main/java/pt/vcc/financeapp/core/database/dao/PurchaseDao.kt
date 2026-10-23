package pt.vcc.financeapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import pt.vcc.financeapp.core.database.entity.ProductCategoryEntity
import pt.vcc.financeapp.core.database.entity.ProductEntity
import pt.vcc.financeapp.core.database.entity.PurchaseEntity
import pt.vcc.financeapp.core.database.entity.PurchaseItemEntity
import pt.vcc.financeapp.core.database.entity.PurchaseSkippedItemEntity
import pt.vcc.financeapp.core.database.entity.ShoppingListEntity
import pt.vcc.financeapp.core.database.entity.ShoppingListItemEntity
import pt.vcc.financeapp.core.database.entity.TransactionEntity
import pt.vcc.financeapp.core.model.PurchaseType

/** Catálogo, listas e compras (S8). As operações que mexem em compra e movimento são atómicas. */
@Dao
abstract class PurchaseDao {
    // --- Catálogo ---

    @Query("SELECT * FROM product_categories ORDER BY sortOrder, name")
    abstract fun observeProductCategories(): Flow<List<ProductCategoryEntity>>

    @Query("SELECT * FROM product_categories WHERE id = :id")
    abstract suspend fun getProductCategory(id: String): ProductCategoryEntity?

    @Upsert
    abstract suspend fun upsertProductCategory(category: ProductCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertProductCategoriesIgnoring(categories: List<ProductCategoryEntity>): List<Long>

    @Query("SELECT * FROM products WHERE isTemporary = 0 ORDER BY isFavorite DESC, sortOrder, name")
    abstract fun observeCatalogProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    abstract suspend fun getProduct(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE barcode = :barcode ORDER BY isTemporary, updatedAt DESC")
    abstract suspend fun getProductsByBarcode(barcode: String): List<ProductEntity>

    @Upsert
    abstract suspend fun upsertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertProductsIgnoring(products: List<ProductEntity>): List<Long>

    // --- Listas planeadas ---

    @Query("SELECT * FROM shopping_lists ORDER BY isArchived, updatedAt DESC")
    abstract fun observeShoppingLists(): Flow<List<ShoppingListEntity>>

    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    abstract fun observeShoppingList(id: String): Flow<ShoppingListEntity?>

    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    abstract suspend fun getShoppingList(id: String): ShoppingListEntity?

    @Upsert
    abstract suspend fun upsertShoppingList(list: ShoppingListEntity)

    @Query("DELETE FROM shopping_lists WHERE id = :id")
    abstract suspend fun deleteShoppingList(id: String)

    @Query("SELECT * FROM shopping_list_items WHERE listId = :listId ORDER BY position, name")
    abstract fun observeShoppingListItems(listId: String): Flow<List<ShoppingListItemEntity>>

    @Query("SELECT * FROM shopping_list_items WHERE listId = :listId ORDER BY position, name")
    abstract suspend fun getShoppingListItems(listId: String): List<ShoppingListItemEntity>

    @Upsert
    abstract suspend fun upsertShoppingListItems(items: List<ShoppingListItemEntity>)

    @Query("DELETE FROM shopping_list_items WHERE id = :id")
    abstract suspend fun deleteShoppingListItem(id: String)

    @Transaction
    open suspend fun insertShoppingListWithItems(list: ShoppingListEntity, items: List<ShoppingListItemEntity>) {
        upsertShoppingList(list)
        upsertShoppingListItems(items)
    }

    // --- Compras ---

    @Query("SELECT * FROM purchases WHERE type = :type AND status = 'ACTIVE' ORDER BY updatedAt DESC LIMIT 1")
    abstract fun observeActive(type: PurchaseType): Flow<PurchaseEntity?>

    @Query("SELECT * FROM purchases WHERE status = 'ACTIVE' ORDER BY updatedAt DESC")
    abstract fun observeAllActive(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE type = :type AND status = 'ACTIVE' ORDER BY updatedAt DESC LIMIT 1")
    abstract suspend fun getActive(type: PurchaseType): PurchaseEntity?

    @Query("SELECT * FROM purchases WHERE id = :id")
    abstract fun observePurchase(id: String): Flow<PurchaseEntity?>

    @Query("SELECT * FROM purchases WHERE id = :id")
    abstract suspend fun getPurchase(id: String): PurchaseEntity?

    @Query("SELECT * FROM purchases WHERE transactionId = :transactionId")
    abstract suspend fun getPurchaseByTransaction(transactionId: String): PurchaseEntity?

    @Query("SELECT * FROM purchases WHERE status = 'COMPLETED' ORDER BY occurredAt DESC, id DESC")
    abstract fun observeCompleted(): Flow<List<PurchaseEntity>>

    @Query(
        """
        SELECT * FROM purchases
        WHERE status = 'COMPLETED' AND occurredAt >= :fromInclusive AND occurredAt < :toExclusive
        ORDER BY occurredAt DESC, id DESC
        """,
    )
    abstract fun observeCompletedInPeriod(fromInclusive: Long, toExclusive: Long): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId ORDER BY position, id")
    abstract fun observeItems(purchaseId: String): Flow<List<PurchaseItemEntity>>

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId ORDER BY position, id")
    abstract suspend fun getItems(purchaseId: String): List<PurchaseItemEntity>

    @Query(
        """
        SELECT i.* FROM purchase_items i JOIN purchases p ON p.id = i.purchaseId
        WHERE p.status = 'COMPLETED' AND p.occurredAt >= :fromInclusive AND p.occurredAt < :toExclusive
        ORDER BY p.occurredAt DESC, i.position
        """,
    )
    abstract fun observeCompletedItemsInPeriod(fromInclusive: Long, toExclusive: Long): Flow<List<PurchaseItemEntity>>

    @Query("SELECT * FROM purchase_items")
    abstract suspend fun getAllItems(): List<PurchaseItemEntity>

    @Query("SELECT * FROM purchases")
    abstract suspend fun getAllPurchases(): List<PurchaseEntity>

    @Upsert
    abstract suspend fun upsertPurchase(purchase: PurchaseEntity)

    @Upsert
    abstract suspend fun upsertItems(items: List<PurchaseItemEntity>)

    @Query("DELETE FROM purchase_items WHERE id = :itemId")
    abstract suspend fun deleteItemById(itemId: String)

    @Query("DELETE FROM purchase_items WHERE purchaseId = :purchaseId")
    abstract suspend fun deleteItemsOf(purchaseId: String)

    @Query("DELETE FROM purchases WHERE id = :purchaseId")
    abstract suspend fun deletePurchaseById(purchaseId: String)

    @Query(
        """
        UPDATE purchases SET
            totalMinor = (SELECT COALESCE(SUM(totalMinor), 0) FROM purchase_items WHERE purchaseId = :purchaseId),
            updatedAt = :now
        WHERE id = :purchaseId
        """,
    )
    abstract suspend fun recalculateTotal(purchaseId: String, now: Long)

    @Query("SELECT shoppingListItemId FROM purchase_skipped_items WHERE purchaseId = :purchaseId")
    abstract fun observeSkipped(purchaseId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSkipped(skipped: PurchaseSkippedItemEntity)

    @Query("DELETE FROM purchase_skipped_items WHERE purchaseId = :purchaseId AND shoppingListItemId = :listItemId")
    abstract suspend fun deleteSkipped(purchaseId: String, listItemId: String)

    @Upsert
    abstract suspend fun upsertTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    abstract suspend fun deleteTransactionById(transactionId: String)

    /** Grava ou substitui um item de uma compra em curso e atualiza o total. */
    @Transaction
    open suspend fun saveItem(item: PurchaseItemEntity, now: Long) {
        upsertItems(listOf(item))
        recalculateTotal(item.purchaseId, now)
    }

    @Transaction
    open suspend fun removeItem(purchaseId: String, itemId: String, now: Long) {
        deleteItemById(itemId)
        recalculateTotal(purchaseId, now)
    }

    @Transaction
    open suspend fun createWithItems(purchase: PurchaseEntity, items: List<PurchaseItemEntity>) {
        upsertPurchase(purchase)
        upsertItems(items)
        recalculateTotal(purchase.id, purchase.updatedAt)
    }

    /**
     * Grava a compra, substitui os itens e sincroniza o movimento associado numa única transação:
     * [transaction] é inserido/atualizado antes da compra (chave estrangeira) e [obsoleteTransactionId]
     * é apagado depois de a compra deixar de o referenciar.
     */
    @Transaction
    open suspend fun persistWithTransaction(
        purchase: PurchaseEntity,
        items: List<PurchaseItemEntity>,
        transaction: TransactionEntity?,
        obsoleteTransactionId: String?,
    ) {
        transaction?.let { upsertTransaction(it) }
        upsertPurchase(purchase)
        deleteItemsOf(purchase.id)
        upsertItems(items)
        obsoleteTransactionId?.takeIf { it != purchase.transactionId }?.let { deleteTransactionById(it) }
    }

    /** Apaga a compra (itens em cascata) e o movimento que gerou. */
    @Transaction
    open suspend fun deleteWithTransaction(purchaseId: String, transactionId: String?) {
        deletePurchaseById(purchaseId)
        transactionId?.let { deleteTransactionById(it) }
    }

    // --- Sugestões de preço (compras concluídas) ---

    @Query(
        """
        SELECT i.unitPriceMinor FROM purchase_items i JOIN purchases p ON p.id = i.purchaseId
        WHERE p.status = 'COMPLETED' AND i.productId = :productId AND p.merchantId = :merchantId
        ORDER BY p.occurredAt DESC LIMIT 1
        """,
    )
    abstract suspend fun lastPriceAtMerchant(productId: String, merchantId: String): Long?

    @Query(
        """
        SELECT i.unitPriceMinor FROM purchase_items i JOIN purchases p ON p.id = i.purchaseId
        WHERE p.status = 'COMPLETED' AND i.productId = :productId
        ORDER BY p.occurredAt DESC LIMIT 1
        """,
    )
    abstract suspend fun lastPrice(productId: String): Long?
}
