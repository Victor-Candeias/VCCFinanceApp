package pt.vcc.financeapp.core.data.usecase

import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.financeapp.core.data.testing.FakeCatalogRepository
import pt.vcc.financeapp.core.data.testing.FakeMerchantRepository
import pt.vcc.financeapp.core.data.testing.FakePurchaseRepository
import pt.vcc.financeapp.core.data.testing.FakeTransactionRepository
import pt.vcc.financeapp.core.model.PriceSource
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.core.model.PurchaseType

class PurchaseEditingUseCasesTest {
    private val now = Instant.parse("2026-10-21T09:30:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Europe/Lisbon"))
    private val purchases = FakePurchaseRepository(FakeTransactionRepository())
    private val catalog = FakeCatalogRepository(DefaultProductCatalog.categories, DefaultProductCatalog.products(Instant.EPOCH))
    private val merchants = FakeMerchantRepository(emptyList())
    private val saveItem = SavePurchaseItemUseCase(purchases, catalog)
    private val cancel = CancelPurchaseUseCase(purchases, clock)
    private val header = EditPurchaseHeaderUseCase(purchases, merchants, clock)

    private val purchase = Purchase(
        id = "p-1",
        type = PurchaseType.QUICK,
        status = PurchaseStatus.ACTIVE,
        occurredAt = Instant.EPOCH,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun input(name: String = "Leite", quantity: String? = "2", price: Long? = 95, id: String? = null, discount: Long? = 0) =
        PurchaseItemInput("p-1", id, name, quantity?.let(::BigDecimal), price, discount)

    @Test
    fun savingItemsAppendsPositionsAndRecalculatesTotal() = runTest {
        purchases.create(purchase, emptyList())

        val first = (saveItem(input("  Leite   meio gordo ")) as SavePurchaseItemResult.Saved).item
        val second = (saveItem(input("Pão", "0.500", 300, discount = 20)) as SavePurchaseItemResult.Saved).item

        assertEquals("Leite meio gordo", first.name)
        assertEquals(0, first.position)
        assertEquals(1, second.position)
        assertEquals(BigDecimal("0.5"), second.quantity)
        assertEquals(190L + 130L, purchases.getById("p-1")!!.totalMinor)
    }

    @Test
    fun editingKeepsIdAndPosition() = runTest {
        purchases.create(purchase, emptyList())
        val first = (saveItem(input()) as SavePurchaseItemResult.Saved).item
        saveItem(input("Pão"))

        val edited = (saveItem(input("Leite", "3", 100, id = first.id)) as SavePurchaseItemResult.Saved).item

        assertEquals(first.id, edited.id)
        assertEquals(0, edited.position)
        assertEquals(2, purchases.getDetail("p-1")!!.items.size)
        assertEquals(300L + 190L, purchases.getById("p-1")!!.totalMinor)
    }

    @Test
    fun catalogProductProvidesCategoryName() = runTest {
        purchases.create(purchase, emptyList())
        val product = DefaultProductCatalog.products(Instant.EPOCH).first()

        val item = (saveItem(input(product.name).copy(productId = product.id, priceSource = PriceSource.CATALOG)) as SavePurchaseItemResult.Saved).item

        assertEquals(product.id, item.productId)
        assertEquals(DefaultProductCatalog.categories.first { it.id == product.productCategoryId }.name, item.categoryName)
    }

    @Test
    fun invalidInputIsRejected() = runTest {
        purchases.create(purchase, emptyList())

        val result = saveItem(input(" ", "0", -1, discount = null)) as SavePurchaseItemResult.Invalid

        assertEquals(
            setOf(PurchaseItemError.BLANK_NAME, PurchaseItemError.INVALID_QUANTITY, PurchaseItemError.INVALID_PRICE, PurchaseItemError.INVALID_DISCOUNT),
            result.errors,
        )
        assertTrue((saveItem(input("x".repeat(121))) as SavePurchaseItemResult.Invalid).errors.contains(PurchaseItemError.NAME_TOO_LONG))
        assertTrue((saveItem(input(quantity = "100000")) as SavePurchaseItemResult.Invalid).errors.contains(PurchaseItemError.INVALID_QUANTITY))
        assertTrue(purchases.getDetail("p-1")!!.items.isEmpty())
    }

    @Test
    fun cancelledPurchaseRejectsItemsAndCannotBeCancelledTwice() = runTest {
        purchases.create(purchase, emptyList())

        assertTrue(cancel("p-1"))
        assertEquals(PurchaseStatus.CANCELLED, purchases.getById("p-1")!!.status)
        assertFalse(cancel("p-1"))
        assertEquals(setOf(PurchaseItemError.PURCHASE_CANCELLED), (saveItem(input()) as SavePurchaseItemResult.Invalid).errors)
        assertEquals(setOf(PurchaseItemError.PURCHASE_NOT_FOUND), (saveItem(input().copy(purchaseId = "x")) as SavePurchaseItemResult.Invalid).errors)
    }

    @Test
    fun headerSetsAndClearsMerchantAndBudget() = runTest {
        purchases.create(purchase, emptyList())

        val withMerchant = header.setMerchant("p-1", " Continente ")!!
        assertEquals(merchants.findByName("Continente")!!.id, withMerchant.merchantId)
        assertNull(header.setMerchant("p-1", "  ")!!.merchantId)

        assertEquals(5000L, header.setBudget("p-1", 5000)!!.budgetMinor)
        assertNull(header.setBudget("p-1", -1))
        assertEquals(5000L, purchases.getById("p-1")!!.budgetMinor)
        assertNull(header.setBudget("p-1", 0)!!.budgetMinor)
    }
}
