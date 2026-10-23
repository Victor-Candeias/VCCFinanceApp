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
import pt.vcc.financeapp.core.data.testing.FakePurchaseRepository
import pt.vcc.financeapp.core.data.testing.FakeShoppingListRepository
import pt.vcc.financeapp.core.data.testing.FakeTransactionRepository
import pt.vcc.financeapp.core.model.PriceSource
import pt.vcc.financeapp.core.model.Product
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.core.model.PurchaseItem
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.core.model.PurchaseType
import pt.vcc.financeapp.core.model.ShoppingList
import pt.vcc.financeapp.core.model.ShoppingListItem

class BarcodeScanUseCasesTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-21T09:30:00Z"), ZoneId.of("Europe/Lisbon"))
    private val purchases = FakePurchaseRepository(FakeTransactionRepository())
    private val catalog = FakeCatalogRepository(DefaultProductCatalog.categories, emptyList())
    private val lists = FakeShoppingListRepository()
    private val scan = BarcodeScanUseCase(
        purchases, catalog, lists, SuggestPriceUseCase(purchases), SavePurchaseItemUseCase(purchases, catalog), SaveProductUseCase(catalog, clock), clock,
    )

    private val ean = "4006381333931"
    private val milk = Product("milk", "Leite", barcode = ean, defaultPriceMinor = 95, unit = "l", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
    private val active = Purchase(
        id = "p-1",
        type = PurchaseType.QUICK,
        status = PurchaseStatus.ACTIVE,
        occurredAt = Instant.EPOCH,
        merchantId = "m-1",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun confirm(productId: String?, name: String = "Leite", price: Long? = 95, quantity: String = "1", saveToCatalog: Boolean = false, listItemId: String? = null) =
        BarcodeConfirmInput("p-1", ean, productId, name, BigDecimal(quantity), price, saveToCatalog = saveToCatalog, shoppingListItemId = listItemId)

    @Test
    fun gtinAcceptsEan8Ean13AndUpcAWithValidCheckDigit() {
        assertEquals(ean, Gtin.normalize(" $ean "))
        assertEquals("96385074", Gtin.normalize("96385074"))
        assertEquals("0036000291452", Gtin.normalize("036000291452"))
        assertNull(Gtin.normalize("4006381333932"))
        assertNull(Gtin.normalize("40063813339"))
        assertNull(Gtin.normalize("4006381A33931"))
        assertNull(Gtin.normalize(""))
    }

    @Test
    fun debouncerIgnoresSameCodeInsideWindow() {
        val debouncer = ScanDebouncer(windowMs = 3_000)
        assertTrue(debouncer.accept(ean, 0))
        assertFalse(debouncer.accept(ean, 2_999))
        assertTrue(debouncer.accept("96385074", 3_000))
        assertTrue(debouncer.accept(ean, 3_001))
    }

    @Test
    fun knownProductSuggestsLastPricePaidAtMerchant() = runTest {
        catalog.saveProduct(milk)
        purchases.create(active.copy(id = "old", status = PurchaseStatus.COMPLETED), listOf(PurchaseItem("i", "old", "Leite", BigDecimal.ONE, 89, productId = "milk")))
        purchases.create(active)

        val lookup = scan.lookup("p-1", ean) as BarcodeLookup.Known

        assertEquals(milk, lookup.product)
        assertEquals(PriceSuggestion(89, PriceSource.SUGGESTED), lookup.suggestion)
        assertTrue(purchases.getDetail("p-1")!!.items.isEmpty())
    }

    @Test
    fun upcAStoredWithoutLeadingZeroIsFound() = runTest {
        catalog.saveProduct(milk.copy(barcode = "036000291452"))
        purchases.create(active)

        assertEquals(milk.id, (scan.lookup("p-1", "036000291452") as BarcodeLookup.Known).product.id)
    }

    @Test
    fun invalidAndUnknownCodes() = runTest {
        purchases.create(active)

        assertEquals(BarcodeLookup.InvalidCode, scan.lookup("p-1", "123"))
        assertEquals(BarcodeLookup.Unknown(ean, emptyList()), scan.lookup("p-1", ean))
        assertEquals(BarcodeLookup.PurchaseNotFound, scan.lookup("missing", ean))
    }

    @Test
    fun confirmingKnownProductAddsItemAndRescanSumsQuantity() = runTest {
        catalog.saveProduct(milk)
        purchases.create(active)

        val first = (scan.confirm(confirm("milk")) as BarcodeConfirmResult.Added).item
        scan.confirm(confirm("milk", quantity = "2"))

        val item = purchases.getDetail("p-1")!!.items.single()
        assertEquals(first.id, item.id)
        assertEquals(0, BigDecimal(3).compareTo(item.quantity))
        assertEquals(PriceSource.SCANNER, item.priceSource)
        assertEquals("l", item.unit)
        assertEquals(285, purchases.getById("p-1")!!.totalMinor)
    }

    @Test
    fun unknownCodeCreatesTemporaryOrCatalogProductWithBarcode() = runTest {
        purchases.create(active)

        scan.confirm(confirm(null, name = "Iogurte", price = 45))
        val temporary = catalog.products.value.single()
        assertTrue(temporary.isTemporary)
        assertEquals(ean, temporary.barcode)

        catalog.products.value = emptyList()
        scan.confirm(confirm(null, name = "Iogurte grego", price = 60, saveToCatalog = true))
        val saved = catalog.products.value.single()
        assertFalse(saved.isTemporary)
        assertEquals(ean, saved.barcode)
        assertEquals(60L, saved.defaultPriceMinor)
        assertEquals(2, purchases.getDetail("p-1")!!.items.size)
    }

    @Test
    fun nothingIsWrittenWhenConfirmationIsInvalid() = runTest {
        purchases.create(active)

        val result = scan.confirm(confirm(null, name = " ", price = null)) as BarcodeConfirmResult.Invalid

        assertEquals(setOf(PurchaseItemError.BLANK_NAME, PurchaseItemError.INVALID_PRICE), result.itemErrors)
        assertTrue(catalog.products.value.isEmpty())
        assertTrue(purchases.getDetail("p-1")!!.items.isEmpty())
    }

    @Test
    fun plannedPurchaseLinksScanToPendingListItem() = runTest {
        catalog.saveProduct(milk)
        lists.save(
            ShoppingList("l-1", "Semana", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
            listOf(ShoppingListItem("li-pao", "l-1", "Pão"), ShoppingListItem("li-leite", "l-1", "Leite", productId = "milk", position = 1)),
        )
        purchases.create(active.copy(type = PurchaseType.PLANNED, shoppingListId = "l-1"))

        val lookup = scan.lookup("p-1", ean) as BarcodeLookup.Known
        assertEquals("li-leite", lookup.listItem?.id)
        assertEquals(listOf("li-pao", "li-leite"), lookup.pendingListItems.map { it.id })

        scan.confirm(confirm("milk", listItemId = lookup.listItem!!.id))
        assertEquals("li-leite", purchases.getDetail("p-1")!!.items.single().shoppingListItemId)
        assertEquals(listOf("li-pao"), (scan.lookup("p-1", ean) as BarcodeLookup.Known).pendingListItems.map { it.id })
    }
}
