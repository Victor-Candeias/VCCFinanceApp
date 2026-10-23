package pt.vcc.financeapp.core.data.usecase

import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.financeapp.core.data.testing.FakeCatalogRepository
import pt.vcc.financeapp.core.data.testing.FakePurchaseRepository
import pt.vcc.financeapp.core.data.testing.FakeTransactionRepository
import pt.vcc.financeapp.core.model.PaymentMethod
import pt.vcc.financeapp.core.model.PriceSource
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.core.model.PurchaseItem
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.core.model.PurchaseType

class PurchaseHistoryUseCasesTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    private val now = Instant.parse("2026-10-21T09:30:00Z")
    private val clock = Clock.fixed(now, zone)
    private val today = LocalDate.of(2026, 10, 21)
    private val purchases = FakePurchaseRepository(FakeTransactionRepository())
    private val catalog = FakeCatalogRepository(DefaultProductCatalog.categories, DefaultProductCatalog.products(Instant.EPOCH))
    private val repeat = RepeatPurchaseUseCase(purchases, catalog, SuggestPriceUseCase(purchases), clock)

    private fun completed(
        id: String,
        occurredAt: String = "2026-10-10T12:00:00Z",
        type: PurchaseType = PurchaseType.QUICK,
        method: PaymentMethod = PaymentMethod.MB,
        accountId: String = "bank",
        merchantId: String? = "m-1",
    ) = Purchase(
        id = id,
        type = type,
        status = PurchaseStatus.COMPLETED,
        occurredAt = Instant.parse(occurredAt),
        merchantId = merchantId,
        paymentMethod = method,
        accountId = accountId,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        completedAt = Instant.parse(occurredAt),
    )

    private fun item(purchaseId: String, id: String, price: Long, productId: String? = null, quantity: String = "1") =
        PurchaseItem(id, purchaseId, id, BigDecimal(quantity), price, discountMinor = 10, productId = productId, shoppingListItemId = "li", position = 1)

    // --- Filtros ---

    @Test
    fun filterCombinesTypeMethodAccountAndMerchant() {
        val p = completed("p", type = PurchaseType.EXPRESS, method = PaymentMethod.CASH, accountId = "cash", merchantId = "m-2")
        val match = PurchaseHistoryFilter(PurchaseType.EXPRESS, PaymentMethod.CASH, "cash", "m-2")

        assertTrue(match.matches(p, today, zone))
        assertFalse(match.copy(type = PurchaseType.QUICK).matches(p, today, zone))
        assertFalse(match.copy(method = PaymentMethod.MB).matches(p, today, zone))
        assertFalse(match.copy(accountId = "bank").matches(p, today, zone))
        assertFalse(match.copy(merchantId = "m-1").matches(p, today, zone))
        assertFalse(PurchaseHistoryFilter().matches(p.copy(status = PurchaseStatus.CANCELLED), today, zone))
        assertFalse(PurchaseHistoryFilter().isActive)
        assertTrue(match.isActive)
    }

    @Test
    fun periodsUseLocalDateOfThePurchase() {
        fun matches(period: HistoryPeriod, at: String) = PurchaseHistoryFilter(period = period).matches(completed("p", at), today, zone)

        // 30/09 23:30 UTC = 01/10 00:30 em Lisboa → este mês.
        assertTrue(matches(HistoryPeriod.THIS_MONTH, "2026-09-30T23:30:00Z"))
        assertFalse(matches(HistoryPeriod.THIS_MONTH, "2026-09-30T22:30:00Z"))
        assertTrue(matches(HistoryPeriod.LAST_MONTH, "2026-09-15T12:00:00Z"))
        assertFalse(matches(HistoryPeriod.LAST_MONTH, "2025-09-15T12:00:00Z"))
        assertTrue(matches(HistoryPeriod.LAST_90_DAYS, "2026-07-24T12:00:00Z"))
        assertFalse(matches(HistoryPeriod.LAST_90_DAYS, "2026-07-22T12:00:00Z"))
        assertTrue(matches(HistoryPeriod.THIS_YEAR, "2026-01-01T12:00:00Z"))
        assertFalse(matches(HistoryPeriod.THIS_YEAR, "2025-12-31T12:00:00Z"))
        assertTrue(matches(HistoryPeriod.ALL, "2001-01-01T12:00:00Z"))
    }

    // --- Repetir ---

    @Test
    fun repeatCopiesItemsWithCurrentSuggestedPrices() = runTest {
        purchases.create(completed("old", "2026-09-01T12:00:00Z"), listOf(item("old", "cafe", 70, "catalog-product-espresso", "2"), item("old", "livre", 199)))
        purchases.create(completed("newer", "2026-10-01T12:00:00Z"), listOf(item("newer", "cafe2", 90, "catalog-product-espresso")))

        val started = (repeat("old") as RepeatPurchaseResult.Started).purchase

        assertEquals(PurchaseStatus.ACTIVE, started.status)
        assertEquals(PurchaseType.QUICK, started.type)
        assertEquals("m-1", started.merchantId)
        assertEquals(now, started.occurredAt)
        assertNull(started.paymentMethod)
        assertNull(started.transactionId)
        val items = purchases.getDetail(started.id)!!.items.sortedBy { it.position }
        assertEquals(listOf("cafe", "livre"), items.map { it.name })
        assertEquals(listOf(90L, 199L), items.map { it.unitPriceMinor })
        assertEquals(listOf(PriceSource.SUGGESTED, PriceSource.MANUAL), items.map { it.priceSource })
        assertEquals(BigDecimal("2"), items[0].quantity)
        assertTrue(items.all { it.purchaseId == started.id && it.discountMinor == 0L && it.shoppingListItemId == null })
        assertTrue(items.none { it.id == "cafe" || it.id == "livre" })
        assertEquals(379, started.totalMinor)
        // A compra original fica intacta.
        assertEquals(2, purchases.getDetail("old")!!.items.size)
    }

    @Test
    fun repeatingPlannedStartsQuickPurchaseAndReusesEmptyActive() = runTest {
        purchases.create(completed("planned", type = PurchaseType.PLANNED), listOf(item("planned", "a", 100)))
        val emptyActive = completed("draft").copy(status = PurchaseStatus.ACTIVE, merchantId = null, paymentMethod = null, accountId = null)
        purchases.create(emptyActive)

        val started = (repeat("planned") as RepeatPurchaseResult.Started).purchase

        assertEquals("draft", started.id)
        assertEquals(PurchaseType.QUICK, started.type)
        assertEquals("m-1", started.merchantId)
        assertNull(started.shoppingListId)
        assertEquals(1, purchases.getDetail("draft")!!.items.size)
    }

    @Test
    fun repeatConflictsWithActivePurchaseThatHasItems() = runTest {
        purchases.create(completed("done", type = PurchaseType.EXPRESS), listOf(item("done", "a", 100)))
        val active = completed("busy", type = PurchaseType.EXPRESS).copy(status = PurchaseStatus.ACTIVE)
        purchases.create(active, listOf(item("busy", "x", 50)))

        val result = repeat("done") as RepeatPurchaseResult.Conflict

        assertEquals("busy", result.activePurchase.id)
        assertEquals(listOf("x"), purchases.getDetail("busy")!!.items.map { it.id })
    }

    @Test
    fun onlyCompletedPurchasesCanBeRepeated() = runTest {
        purchases.create(completed("cancelled").copy(status = PurchaseStatus.CANCELLED), listOf(item("cancelled", "a", 100)))

        assertEquals(RepeatPurchaseResult.NotFound, repeat("cancelled"))
        assertEquals(RepeatPurchaseResult.NotFound, repeat("missing"))
    }
}
