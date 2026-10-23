package pt.vcc.financeapp.core.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

// Os nomes das constantes são persistidos em texto pela base de dados: não renomear sem migration.

/** Modos de compra herdados do VCCSmartShopcart. */
enum class PurchaseType {
    /** Calculadora rápida: itens introduzidos à medida que se compra. */
    QUICK,

    /** Compra a partir de uma lista planeada. */
    PLANNED,

    /** Despesa rápida a partir do catálogo (café, refeição, transporte…). */
    EXPRESS,
}

/** `ACTIVE` é uma compra em curso (ou rascunho) ainda sem movimento associado. */
enum class PurchaseStatus { ACTIVE, COMPLETED, CANCELLED }

/** MB debita uma conta bancária; Dinheiro debita uma conta [AccountType.CASH]. */
enum class PaymentMethod { MB, CASH }

enum class PriceSource { MANUAL, CATALOG, SUGGESTED, SCANNER, OCR }

data class Purchase(
    val id: String,
    val type: PurchaseType,
    val status: PurchaseStatus,
    val occurredAt: Instant,
    val currency: String = Money.DEFAULT_CURRENCY,
    val totalMinor: Long = 0,
    val merchantId: String? = null,
    val shoppingListId: String? = null,
    val budgetMinor: Long? = null,
    val paymentMethod: PaymentMethod? = null,
    val accountId: String? = null,
    val categoryId: String? = null,
    val transactionId: String? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant? = null,
)

data class PurchaseItem(
    val id: String,
    val purchaseId: String,
    val name: String,
    val quantity: BigDecimal,
    val unitPriceMinor: Long,
    val discountMinor: Long = 0,
    val unit: String? = null,
    val productId: String? = null,
    val shoppingListItemId: String? = null,
    /** Nome da categoria do catálogo no momento da compra, para relatórios estáveis. */
    val categoryName: String? = null,
    val priceSource: PriceSource = PriceSource.MANUAL,
    val position: Int = 0,
) {
    val totalMinor: Long get() = PurchaseMath.itemTotal(quantity, unitPriceMinor, discountMinor)
}

/** Compra com os seus itens, como lida da base de dados. */
data class PurchaseDetail(val purchase: Purchase, val items: List<PurchaseItem>)

/** Categoria do catálogo de produtos; [financeCategoryId] é a categoria de despesa usada no movimento. */
data class ProductCategory(
    val id: String,
    val name: String,
    val icon: String? = null,
    val financeCategoryId: String? = null,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
)

data class Product(
    val id: String,
    val name: String,
    val productCategoryId: String? = null,
    val barcode: String? = null,
    val brand: String? = null,
    val defaultPriceMinor: Long? = null,
    val unit: String? = null,
    val isFavorite: Boolean = false,
    val isActive: Boolean = true,
    /** Produto criado de passagem numa compra; não aparece no catálogo. */
    val isTemporary: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ShoppingList(
    val id: String,
    val name: String,
    val budgetMinor: Long? = null,
    val isArchived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ShoppingListItem(
    val id: String,
    val listId: String,
    val name: String,
    val quantity: BigDecimal = BigDecimal.ONE,
    val unit: String? = null,
    val estimatedPriceMinor: Long? = null,
    val productId: String? = null,
    val position: Int = 0,
)

object PurchaseMath {
    /** `max(quantidade × preço − desconto, 0)`, arredondado ao cêntimo (HALF_UP). */
    fun itemTotal(quantity: BigDecimal, unitPriceMinor: Long, discountMinor: Long): Long {
        val gross = quantity.multiply(BigDecimal.valueOf(unitPriceMinor)).setScale(0, RoundingMode.HALF_UP).longValueExact()
        return (gross - discountMinor).coerceAtLeast(0)
    }

    fun total(items: List<PurchaseItem>): Long = items.fold(0L) { sum, item -> Math.addExact(sum, item.totalMinor) }

    /** Quantidade escrita pelo utilizador ("1,5", "2"); nulo se não for um número positivo. */
    fun parseQuantity(raw: String): BigDecimal? =
        raw.trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.stripTrailingZeros()
}
