package pt.vcc.financeapp.feature.finance.purchases

import androidx.lifecycle.SavedStateHandle
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.vcc.financeapp.core.data.testing.FakeCatalogRepository
import pt.vcc.financeapp.core.data.testing.FakePurchaseRepository
import pt.vcc.financeapp.core.data.testing.FakeShoppingListRepository
import pt.vcc.financeapp.core.data.testing.FakeTransactionRepository
import pt.vcc.financeapp.core.data.usecase.BarcodeScanUseCase
import pt.vcc.financeapp.core.data.usecase.DefaultProductCatalog
import pt.vcc.financeapp.core.data.usecase.PurchaseItemError
import pt.vcc.financeapp.core.data.usecase.SaveProductUseCase
import pt.vcc.financeapp.core.data.usecase.SavePurchaseItemUseCase
import pt.vcc.financeapp.core.data.usecase.SuggestPriceUseCase
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.PriceSource
import pt.vcc.financeapp.core.model.Product
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.core.model.PurchaseType

@OptIn(ExperimentalCoroutinesApi::class)
class BarcodeScannerViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-21T09:00:00Z"), ZoneId.of("Europe/Lisbon"))
    private val purchases = FakePurchaseRepository(FakeTransactionRepository())
    private val catalog = FakeCatalogRepository(DefaultProductCatalog.categories, emptyList())
    private val ean = "4006381333931"
    private val milk = Product("milk", "Leite", barcode = ean, defaultPriceMinor = 95, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
    private val purchase = Purchase(
        id = "p-1",
        type = PurchaseType.QUICK,
        status = PurchaseStatus.ACTIVE,
        occurredAt = Instant.EPOCH,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun viewModel() = BarcodeScannerViewModel(
        SavedStateHandle(mapOf(PurchaseViewModel.PURCHASE_ID_ARG to "p-1")),
        purchases,
        catalog,
        BarcodeScanUseCase(
            purchases, catalog, FakeShoppingListRepository(), SuggestPriceUseCase(purchases),
            SavePurchaseItemUseCase(purchases, catalog), SaveProductUseCase(catalog, clock), clock,
        ),
        clock,
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun scannedKnownProductNeedsConfirmationBeforeBeingAdded() = runTest {
        catalog.saveProduct(milk)
        purchases.create(purchase)
        val vm = viewModel()

        vm.onCodeDetected(ean)
        val draft = vm.uiState.value.draft!!
        assertEquals("milk", draft.productId)
        assertEquals("0,95", draft.price)
        assertEquals(PriceSource.CATALOG, draft.priceSource)
        assertTrue(purchases.getDetail("p-1")!!.items.isEmpty())

        vm.updateDraft { it.copy(quantity = "2") }
        vm.onConfirm()

        assertNull(vm.uiState.value.draft)
        assertEquals(ScanMessage.ADDED, vm.uiState.value.message)
        assertEquals(1, vm.uiState.value.itemCount)
        assertEquals(Money(190, "EUR"), vm.uiState.value.total)
    }

    @Test
    fun sameCodeIsIgnoredRightAfterAndWhileConfirming() = runTest {
        catalog.saveProduct(milk)
        purchases.create(purchase)
        val vm = viewModel()

        vm.onCodeDetected(ean)
        vm.onDismissDraft()
        vm.onCodeDetected(ean)

        assertNull(vm.uiState.value.draft)
    }

    @Test
    fun manualUnknownCodeOpensNewProductAndValidatesBeforeSaving() = runTest {
        purchases.create(purchase)
        val vm = viewModel()

        vm.onManualCodeChange("12a3")
        vm.onSubmitManualCode()
        assertEquals(ScanMessage.INVALID_CODE, vm.uiState.value.message)

        vm.onManualCodeChange(ean)
        vm.onSubmitManualCode()
        assertTrue(vm.uiState.value.draft!!.isNewProduct)
        vm.onConfirm()
        assertEquals(setOf(PurchaseItemError.BLANK_NAME, PurchaseItemError.INVALID_PRICE), vm.uiState.value.draft!!.itemErrors)

        vm.updateDraft { it.copy(name = "Iogurte") }
        vm.onPriceChange("0,45")
        vm.onConfirm()
        assertEquals("Iogurte", purchases.getDetail("p-1")!!.items.single().name)
        assertEquals(ean, catalog.products.value.single().barcode)
        assertEquals("", vm.uiState.value.manualCode)
    }
}
