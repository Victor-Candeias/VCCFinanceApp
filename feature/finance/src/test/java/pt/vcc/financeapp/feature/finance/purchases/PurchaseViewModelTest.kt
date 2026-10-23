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
import pt.vcc.financeapp.core.data.testing.FakeAccountRepository
import pt.vcc.financeapp.core.data.testing.FakeCatalogRepository
import pt.vcc.financeapp.core.data.testing.FakeCategoryRepository
import pt.vcc.financeapp.core.data.testing.FakeMerchantRepository
import pt.vcc.financeapp.core.data.testing.FakePurchaseRepository
import pt.vcc.financeapp.core.data.testing.FakeShoppingListRepository
import pt.vcc.financeapp.core.data.testing.FakeTransactionRepository
import pt.vcc.financeapp.core.data.usecase.CancelPurchaseUseCase
import pt.vcc.financeapp.core.data.usecase.CompletePurchaseUseCase
import pt.vcc.financeapp.core.data.usecase.CreateCashAccountUseCase
import pt.vcc.financeapp.core.data.usecase.DefaultProductCatalog
import pt.vcc.financeapp.core.data.usecase.EditPurchaseHeaderUseCase
import pt.vcc.financeapp.core.data.usecase.ObserveAccountBalancesUseCase
import pt.vcc.financeapp.core.data.usecase.PurchaseItemError
import pt.vcc.financeapp.core.data.usecase.SaveAccountUseCase
import pt.vcc.financeapp.core.data.usecase.SavePurchaseItemUseCase
import pt.vcc.financeapp.core.data.usecase.SystemCategories
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.PaymentMethod
import pt.vcc.financeapp.core.model.PriceSource
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.core.model.PurchaseType
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.security.testing.FakeFieldCipher

@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-21T09:00:00Z"), ZoneId.of("Europe/Lisbon"))
    private val bank = Account("bank", "Banco", AccountType.BANK_ACCOUNT, Money(0, "EUR"), isDefault = true, createdAt = Instant.EPOCH)
    private val accounts = FakeAccountRepository(listOf(bank))
    private val categories = FakeCategoryRepository(SystemCategories.all)
    private val transactions = FakeTransactionRepository()
    private val purchases = FakePurchaseRepository(transactions)
    private val catalog = FakeCatalogRepository(DefaultProductCatalog.categories, DefaultProductCatalog.products(Instant.EPOCH))
    private val merchants = FakeMerchantRepository()
    private val lists = FakeShoppingListRepository()

    private val purchase = Purchase(
        id = "p-1",
        type = PurchaseType.QUICK,
        status = PurchaseStatus.ACTIVE,
        occurredAt = Instant.parse("2026-10-20T18:00:00Z"),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun viewModel() = PurchaseViewModel(
        SavedStateHandle(mapOf(PurchaseViewModel.PURCHASE_ID_ARG to "p-1")),
        purchases,
        merchants,
        catalog,
        lists,
        categories,
        ObserveAccountBalancesUseCase(accounts, transactions),
        SavePurchaseItemUseCase(purchases, catalog),
        EditPurchaseHeaderUseCase(purchases, merchants, clock),
        CancelPurchaseUseCase(purchases, clock),
        CompletePurchaseUseCase(purchases, accounts, categories, transactions, catalog, merchants, lists, clock),
        CreateCashAccountUseCase(SaveAccountUseCase(accounts, transactions, clock, FakeFieldCipher())),
        clock,
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun PurchaseViewModel.addItem(name: String, price: String, quantity: String = "1") {
        onNameChange(name)
        onPriceChange(price)
        updateEditor { it.copy(quantity = quantity) }
        onSaveItem()
    }

    @Test
    fun addingItemsUpdatesTotalAndBudgetRemaining() = runTest {
        purchases.create(purchase, emptyList())
        val vm = viewModel()

        vm.addItem("Leite", "0,95", "2")
        vm.addItem("Pão", "3,00", "0,5")
        assertTrue(vm.onBudgetChosen("5,00"))

        val state = vm.uiState.value
        assertEquals(listOf("Leite", "Pão"), state.items.map { it.name })
        assertEquals(Money(340, "EUR"), state.total)
        assertEquals(Money(160, "EUR"), state.remaining)
        assertEquals("", state.editor.name)
    }

    @Test
    fun invalidItemKeepsEditorWithErrors() = runTest {
        purchases.create(purchase, emptyList())
        val vm = viewModel()

        vm.addItem("", "abc", "0")

        val editor = vm.uiState.value.editor
        assertEquals(setOf(PurchaseItemError.BLANK_NAME, PurchaseItemError.INVALID_PRICE, PurchaseItemError.INVALID_QUANTITY), editor.errors)
        assertTrue(vm.uiState.value.items.isEmpty())
    }

    @Test
    fun selectingProductLinksItAndSuggestsCatalogPrice() = runTest {
        purchases.create(purchase, emptyList())
        val product = DefaultProductCatalog.products(Instant.EPOCH).first().copy(defaultPriceMinor = 129)
        catalog.saveProduct(product)
        val vm = viewModel()

        vm.onNameChange(product.name.take(3))
        assertTrue(product in vm.uiState.value.suggestions)
        vm.onProductSelected(product)

        val editor = vm.uiState.value.editor
        assertEquals(product.id, editor.productId)
        assertEquals(PriceSource.CATALOG, editor.priceSource)
        assertEquals(Money(129, "EUR"), vm.uiState.value.editorTotal)
        assertTrue(vm.uiState.value.suggestions.isEmpty())
    }

    @Test
    fun removingItemCanBeUndone() = runTest {
        purchases.create(purchase, emptyList())
        val vm = viewModel()
        vm.addItem("Leite", "1,00")
        val item = vm.uiState.value.items.single()

        vm.onRemoveItem(item)
        assertTrue(vm.uiState.value.items.isEmpty())
        assertEquals(item, vm.uiState.value.removedItem)

        vm.onUndoRemove()
        assertEquals(listOf(item), vm.uiState.value.items)
        assertNull(vm.uiState.value.removedItem)
        assertEquals(Money(100, "EUR"), vm.uiState.value.total)
    }

    @Test
    fun finishingWithMbCreatesPurchaseExpenseAndCloses() = runTest {
        purchases.create(purchase, emptyList())
        val vm = viewModel()
        vm.addItem("Leite", "1,90")

        vm.onStartFinish()
        assertEquals(PaymentMethod.MB, vm.uiState.value.finish!!.method)
        assertEquals("bank", vm.uiState.value.finish!!.accountId)
        vm.onConfirmFinish()

        val tx = transactions.transactions.value.single()
        assertEquals(TransactionSource.PURCHASE, tx.source)
        assertEquals("bank", tx.accountId)
        assertEquals(Money(190, "EUR"), tx.amount)
        assertEquals(PurchaseStatus.COMPLETED, purchases.getById("p-1")!!.status)
        assertTrue(vm.uiState.value.isClosed)
    }

    @Test
    fun cashWithoutAccountCanCreateOneAndWarnsAboutBalance() = runTest {
        purchases.create(purchase, emptyList())
        val vm = viewModel()
        vm.addItem("Leite", "1,90")
        vm.onStartFinish()

        vm.onMethodChange(PaymentMethod.CASH)
        assertNull(vm.uiState.value.finish!!.accountId)
        vm.onConfirmFinish()
        assertTrue(vm.uiState.value.finish!!.noAccount)

        vm.onCreateCashAccount()
        val finish = vm.uiState.value.finish!!
        assertEquals(PaymentMethod.CASH, finish.method)
        assertEquals(AccountType.CASH, accounts.accounts.value.first { it.id == finish.accountId }.type)
        assertEquals(Money(0, "EUR"), vm.uiState.value.cashShortfall)
    }

    @Test
    fun cancellingMarksPurchaseCancelledWithoutTransaction() = runTest {
        purchases.create(purchase, emptyList())
        val vm = viewModel()
        vm.addItem("Leite", "1,90")

        vm.onConfirmCancel()

        assertEquals(PurchaseStatus.CANCELLED, purchases.getById("p-1")!!.status)
        assertTrue(transactions.transactions.value.isEmpty())
        assertTrue(vm.uiState.value.isClosed)
    }
}
