package pt.vcc.financeapp.core.data.repository

import java.time.Instant
import kotlinx.coroutines.flow.Flow
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

interface PurchaseRepository {
    fun observeActive(type: PurchaseType): Flow<Purchase?>
    fun observeAllActive(): Flow<List<Purchase>>
    fun observePurchase(id: String): Flow<Purchase?>
    fun observeItems(purchaseId: String): Flow<List<PurchaseItem>>
    fun observeCompleted(): Flow<List<Purchase>>

    /** Compras concluídas com `occurredAt` em `[fromInclusive, toExclusive)`. */
    fun observeCompletedInPeriod(fromInclusive: Instant, toExclusive: Instant): Flow<List<Purchase>>
    fun observeCompletedItemsInPeriod(fromInclusive: Instant, toExclusive: Instant): Flow<List<PurchaseItem>>

    /** IDs dos itens da lista ignorados numa compra planeada. */
    fun observeSkipped(purchaseId: String): Flow<Set<String>>

    suspend fun getActive(type: PurchaseType): Purchase?
    suspend fun getById(id: String): Purchase?
    suspend fun getDetail(id: String): PurchaseDetail?
    suspend fun getByTransactionId(transactionId: String): Purchase?
    suspend fun getAllDetails(): List<PurchaseDetail>

    suspend fun create(purchase: Purchase, items: List<PurchaseItem> = emptyList())

    /** Atualiza o cabeçalho (estabelecimento, orçamento, notas…); o total é recalculado a partir dos itens. */
    suspend fun update(purchase: Purchase)
    suspend fun saveItem(item: PurchaseItem)
    suspend fun removeItem(purchaseId: String, itemId: String)
    suspend fun setSkipped(purchaseId: String, shoppingListItemId: String, skipped: Boolean)

    /**
     * Grava compra, itens e movimento numa única transação: [transaction] é criado/atualizado e
     * [obsoleteTransactionId] (movimento anterior) apagado.
     */
    suspend fun persistWithTransaction(
        purchase: Purchase,
        items: List<PurchaseItem>,
        transaction: Transaction?,
        obsoleteTransactionId: String?,
    )

    /** Apaga a compra e o movimento associado. */
    suspend fun delete(purchase: Purchase)

    /** Último preço unitário pago pelo produto, no estabelecimento indicado ou em qualquer um. */
    suspend fun lastPrice(productId: String, merchantId: String?): Long?
}

interface CatalogRepository {
    fun observeCategories(): Flow<List<ProductCategory>>

    /** Produtos do catálogo (sem os temporários), favoritos primeiro. */
    fun observeProducts(): Flow<List<Product>>
    suspend fun getCategory(id: String): ProductCategory?
    suspend fun getProduct(id: String): Product?
    suspend fun findByBarcode(barcode: String): Product?
    suspend fun saveCategory(category: ProductCategory)
    suspend fun saveProduct(product: Product)

    /** Insere as categorias/produtos cujo `id` ainda não existe; devolve quantos foram inseridos. */
    suspend fun insertIfAbsent(categories: List<ProductCategory>, products: List<Product>): Int
}

interface ShoppingListRepository {
    fun observeLists(): Flow<List<ShoppingList>>
    fun observeList(id: String): Flow<ShoppingList?>
    fun observeItems(listId: String): Flow<List<ShoppingListItem>>
    suspend fun getList(id: String): ShoppingList?
    suspend fun getItems(listId: String): List<ShoppingListItem>
    suspend fun save(list: ShoppingList, items: List<ShoppingListItem> = emptyList())
    suspend fun saveItems(items: List<ShoppingListItem>)
    suspend fun deleteItem(id: String)
    suspend fun delete(id: String)
}

interface MerchantRepository {
    fun observeActive(): Flow<List<Merchant>>
    suspend fun getById(id: String): Merchant?

    /** Estabelecimento com o mesmo nome normalizado, criado se ainda não existir (e reativado se estava inativo). */
    suspend fun findOrCreate(name: String): Merchant
    suspend fun update(merchant: Merchant)
}
