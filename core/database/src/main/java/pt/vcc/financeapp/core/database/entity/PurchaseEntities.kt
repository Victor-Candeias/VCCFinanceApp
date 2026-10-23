package pt.vcc.financeapp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import pt.vcc.financeapp.core.model.PaymentMethod
import pt.vcc.financeapp.core.model.PriceSource
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.core.model.PurchaseType

/** Compras (S8-01): os estabelecimentos reutilizam `merchants`; o pagamento gera um movimento em `transactions`. */
@Entity(
    tableName = "product_categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["financeCategoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("financeCategoryId")],
)
data class ProductCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String?,
    val financeCategoryId: String?,
    val sortOrder: Int,
    val isActive: Boolean,
)

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = ProductCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["productCategoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("productCategoryId"), Index("barcode")],
)
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val productCategoryId: String?,
    val barcode: String?,
    val brand: String?,
    val defaultPriceMinor: Long?,
    val unit: String?,
    val isFavorite: Boolean,
    val isActive: Boolean,
    val isTemporary: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val budgetMinor: Long?,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "shopping_list_items",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("listId"), Index("productId")],
)
data class ShoppingListItemEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val name: String,
    val quantity: BigDecimal,
    val unit: String?,
    val estimatedPriceMinor: Long?,
    val productId: String?,
    val position: Int,
)

@Entity(
    tableName = "purchases",
    foreignKeys = [
        ForeignKey(
            entity = MerchantEntity::class,
            parentColumns = ["id"],
            childColumns = ["merchantId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ShoppingListEntity::class,
            parentColumns = ["id"],
            childColumns = ["shoppingListId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["type", "status"]),
        Index("occurredAt"),
        Index("merchantId"),
        Index("shoppingListId"),
        Index("accountId"),
        Index("categoryId"),
        Index(value = ["transactionId"], unique = true),
    ],
)
data class PurchaseEntity(
    @PrimaryKey val id: String,
    val type: PurchaseType,
    val status: PurchaseStatus,
    val occurredAt: Long,
    val currency: String,
    val totalMinor: Long,
    val merchantId: String?,
    val shoppingListId: String?,
    val budgetMinor: Long?,
    val paymentMethod: PaymentMethod?,
    val accountId: String?,
    val categoryId: String?,
    val transactionId: String?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
)

@Entity(
    tableName = "purchase_items",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ShoppingListItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["shoppingListItemId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("purchaseId"), Index("productId"), Index("shoppingListItemId")],
)
data class PurchaseItemEntity(
    @PrimaryKey val id: String,
    val purchaseId: String,
    val name: String,
    val quantity: BigDecimal,
    val unitPriceMinor: Long,
    val discountMinor: Long,
    val totalMinor: Long,
    val unit: String?,
    val productId: String?,
    val shoppingListItemId: String?,
    val categoryName: String?,
    val priceSource: PriceSource,
    val position: Int,
)

/** Itens da lista ignorados numa compra planeada; os restantes estados derivam de `purchase_items`. */
@Entity(
    tableName = "purchase_skipped_items",
    primaryKeys = ["purchaseId", "shoppingListItemId"],
    foreignKeys = [
        ForeignKey(
            entity = PurchaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ShoppingListItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["shoppingListItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("shoppingListItemId")],
)
data class PurchaseSkippedItemEntity(
    val purchaseId: String,
    val shoppingListItemId: String,
)
