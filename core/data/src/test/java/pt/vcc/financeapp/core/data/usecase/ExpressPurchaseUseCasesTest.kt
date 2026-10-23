package pt.vcc.financeapp.core.data.usecase

import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.financeapp.core.data.testing.FakeAccountRepository
import pt.vcc.financeapp.core.data.testing.FakeCatalogRepository
import pt.vcc.financeapp.core.data.testing.FakeCategoryRepository
import pt.vcc.financeapp.core.data.testing.FakeMerchantRepository
import pt.vcc.financeapp.core.data.testing.FakePurchaseRepository
import pt.vcc.financeapp.core.data.testing.FakeShoppingListRepository
import pt.vcc.financeapp.core.data.testing.FakeTransactionRepository
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.PaymentMethod
import pt.vcc.financeapp.core.model.PriceSource
import pt.vcc.financeapp.core.model.Product
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.core.model.PurchaseItem
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.core.model.PurchaseType

class ExpressPurchaseUseCasesTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    private val clock = Clock.fixed(Instant.parse("2026-10-21T09:30:00Z"), zone)
    private val transactions = FakeTransactionRepository()
    private val purchases = FakePurchaseRepository(transactions)
    private val catalog = FakeCatalogRepository(DefaultProductCatalog.categories, DefaultProductCatalog.products(Instant.EPOCH))
    private val bank = Account("bank", "Banco", AccountType.BANK_ACCOUNT, Money(0, "EUR"), createdAt = Instant.EPOCH)
    private val cash = Account("cash", "Dinheiro", AccountType.CASH, Money(0, "EUR"), createdAt = Instant.EPOCH)
    private val accounts = FakeAccountRepository(listOf(bank, cash))
    private val merchants = FakeMerchantRepository()
    private val suggest = SuggestPriceUseCase(purchases)
    private val express = ExpressPurchaseUseCase(
        purchases,
        catalog,
        suggest,
        SavePurchaseItemUseCase(purchases, catalog),
        SaveProductUseCase(catalog, clock),
        clock,
    )
    private val complete = CompletePurchaseUseCase(
        purchases, accounts, FakeCategoryRepository(SystemCategories.all), transactions, catalog, merchants, FakeShoppingListRepository(), clock,
    )
    private val coffee = DefaultProductCatalog.products(Instant.EPOCH).first { it.id == "catalog-product-espresso" }

    private fun purchase(id: String, status: PurchaseStatus = PurchaseStatus.ACTIVE, merchantId: String? = null, at: String = "2026-10-01T10:00:00Z") =
        Purchase(
            id = id,
            type = PurchaseType.EXPRESS,
            status = status,
            occurredAt = Instant.parse(at),
            merchantId = merchantId,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

    private suspend fun paid(id: String, merchantId: String?, price: Long, at: String) {
        purchases.create(
            purchase(id, PurchaseStatus.COMPLETED, merchantId, at),
            listOf(PurchaseItem("$id-i", id, "Café", BigDecimal.ONE, price, productId = coffee.id)),
        )
    }

    @Test
    fun priceSuggestionPrefersMerchantThenAnyMerchantThenCatalog() = runTest {
        assertEquals(PriceSuggestion(80, PriceSource.CATALOG), suggest(coffee, "m-a"))

        paid("old-a", "m-a", 90, "2026-10-01T08:00:00Z")
        paid("new-b", "m-b", 100, "2026-10-05T08:00:00Z")

        assertEquals(PriceSuggestion(90, PriceSource.SUGGESTED), suggest(coffee, "m-a"))
        assertEquals(PriceSuggestion(100, PriceSource.SUGGESTED), suggest(coffee, "m-c"))
        assertEquals(PriceSuggestion(100, PriceSource.SUGGESTED), suggest(coffee, null))
        assertNull(suggest(coffee.copy(id = "sem-preco", defaultPriceMinor = null), null))
    }

    @Test
    fun tappingSumsWholeUnitsAndDecrementRemovesAtOne() = runTest {
        purchases.create(purchase("draft"))

        val first = (express.add("draft", coffee.id) as ExpressAddResult.Added).item
        express.add("draft", coffee.id)

        val item = purchases.getDetail("draft")!!.items.single()
        assertEquals(first.id, item.id)
        assertEquals(BigDecimal(2), item.quantity)
        assertEquals("Café", item.name)
        assertEquals("Café", item.categoryName)
        assertEquals(PriceSource.CATALOG, item.priceSource)
        assertEquals(160L, purchases.getById("draft")!!.totalMinor)

        assertTrue(express.decrement("draft", item.id))
        assertEquals(BigDecimal.ONE, purchases.getDetail("draft")!!.items.single().quantity)
        assertTrue(express.decrement("draft", item.id))
        assertTrue(purchases.getDetail("draft")!!.items.isEmpty())
    }

    @Test
    fun productWithoutPriceAsksForIt() = runTest {
        purchases.create(purchase("draft"))
        val noPrice = coffee.copy(id = "sem-preco", name = "Bica cheia", defaultPriceMinor = null)
        catalog.saveProduct(noPrice)

        assertEquals(ExpressAddResult.NeedsPrice(noPrice), express.add("draft", noPrice.id))
        val item = (express.add("draft", noPrice.id, priceMinor = 95) as ExpressAddResult.Added).item

        assertEquals(95L, item.unitPriceMinor)
        assertEquals(PriceSource.MANUAL, item.priceSource)
        assertEquals(ExpressAddResult.ProductNotFound, express.add("draft", "nao-existe"))
    }

    @Test
    fun changingPriceKeepsProductAndHistory() = runTest {
        paid("history", null, 85, "2026-10-01T08:00:00Z")
        purchases.create(purchase("draft"))
        val item = (express.add("draft", coffee.id) as ExpressAddResult.Added).item
        assertEquals(85L, item.unitPriceMinor)

        express.changePrice("draft", item.id, 120)

        assertEquals(120L, purchases.getDetail("draft")!!.items.single().unitPriceMinor)
        assertEquals(80L, catalog.getProduct(coffee.id)!!.defaultPriceMinor)
        assertEquals(85L, purchases.getDetail("history")!!.items.single().unitPriceMinor)
        assertTrue(express.changePrice("draft", item.id, -1) is ExpressAddResult.Invalid)
    }

    @Test
    fun temporaryProductStaysOutOfCatalogUntilPromoted() = runTest {
        purchases.create(purchase("draft"))

        val invalid = express.addTemporary("draft", "  ", null) as ExpressAddResult.Invalid
        assertEquals(setOf(PurchaseItemError.BLANK_NAME, PurchaseItemError.INVALID_PRICE), invalid.errors)

        val item = (express.addTemporary("draft", " Água   das Pedras ", 120) as ExpressAddResult.Added).item
        val product = catalog.getProduct(item.productId!!)!!
        assertEquals("Água das Pedras", product.name)
        assertTrue(product.isTemporary)
        assertFalse(catalog.products.value.filter { !it.isTemporary }.any { it.id == product.id })

        val promoted = express.promoteTemporary("draft", item.id, "catalog-other") as CatalogSaveResult.Saved
        assertFalse(promoted.value.isTemporary)
        assertEquals(120L, promoted.value.defaultPriceMinor)
        assertEquals("Outros", purchases.getDetail("draft")!!.items.single().categoryName)
        assertTrue(express.promoteTemporary("draft", item.id, null) is CatalogSaveResult.Invalid)
    }

    @Test
    fun draftSurvivesAndIsResumed() = runTest {
        val getOrCreate = GetOrCreateActivePurchaseUseCase(purchases, clock)
        val draft = getOrCreate(PurchaseType.EXPRESS)
        express.add(draft.id, coffee.id)

        val resumed = getOrCreate(PurchaseType.EXPRESS)

        assertEquals(draft.id, resumed.id)
        assertEquals(1, purchases.getDetail(resumed.id)!!.items.size)
    }

    @Test
    fun lastPaymentChoicePrefersSameType() = runTest {
        val last = LastPaymentChoiceUseCase(purchases)
        assertNull(last(PurchaseType.EXPRESS))

        purchases.create(
            purchase("express", PurchaseStatus.COMPLETED).copy(paymentMethod = PaymentMethod.CASH, accountId = "cash", completedAt = Instant.parse("2026-10-01T00:00:00Z")),
        )
        purchases.create(
            purchase("quick", PurchaseStatus.COMPLETED).copy(
                type = PurchaseType.QUICK, paymentMethod = PaymentMethod.MB, accountId = "bank", completedAt = Instant.parse("2026-10-02T00:00:00Z"),
            ),
        )

        assertEquals(PaymentChoice(PaymentMethod.CASH, "cash"), last(PurchaseType.EXPRESS))
        assertEquals(PaymentChoice(PaymentMethod.MB, "bank"), last(PurchaseType.PLANNED))
    }

    @Test
    fun completingWithTimeSetsDateAndTimeAndNotes() = runTest {
        purchases.create(purchase("draft"))
        express.add("draft", coffee.id)
        EditPurchaseHeaderUseCase(purchases, merchants, clock).setNotes("draft", "  com o Rui ")

        val result = complete(
            CompletePurchaseInput("draft", PaymentMethod.CASH, "cash", date = LocalDate.of(2026, 10, 20), time = LocalTime.of(8, 15)),
        ) as CompletePurchaseResult.Completed

        assertEquals(LocalDate.of(2026, 10, 20).atTime(8, 15).atZone(zone).toInstant(), result.purchase.occurredAt)
        assertEquals("com o Rui", result.transaction!!.notes)
        assertEquals("system-food-coffee", result.transaction!!.categoryId)
        assertNull(EditPurchaseHeaderUseCase(purchases, merchants, clock).setNotes("draft", "x".repeat(501)))
    }
}
