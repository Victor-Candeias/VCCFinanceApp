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
import pt.vcc.financeapp.core.data.usecase.ExpressPurchaseUseCase
import pt.vcc.financeapp.core.data.usecase.LastPaymentChoiceUseCase
import pt.vcc.financeapp.core.data.usecase.ObserveAccountBalancesUseCase
import pt.vcc.financeapp.core.data.usecase.SaveAccountUseCase
import pt.vcc.financeapp.core.data.usecase.SaveProductUseCase
import pt.vcc.financeapp.core.data.usecase.SavePurchaseItemUseCase
import pt.vcc.financeapp.core.data.usecase.SuggestPriceUseCase
import pt.vcc.financeapp.core.data.usecase.SystemCategories
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.PaymentMethod
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.core.model.PurchaseType
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.security.testing.FakeFieldCipher

@OptIn(ExperimentalCoroutinesApi::class)
class ExpressPurchaseViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-21T09:00:00Z"), ZoneId.of("Europe/Lisbon"))
    private val bank = Account("bank", "Banco", AccountType.BANK_ACCOUNT, Money(0, "EUR"), isDefault = true, createdAt = Instant.EPOCH)
    private val cash = Account("cash", "Dinheiro", AccountType.CASH, Money(0, "EUR"), createdAt = Instant.EPOCH)
    private val accounts = FakeAccountRepository(listOf(bank, cash))
    private val categories = FakeCategoryRepository(SystemCategories.all)
    private val transactions = FakeTransactionRepository()
    private val purchases = FakePurchaseRepository(transactions)
    private val catalog = FakeCatalogRepository(DefaultProductCatalog.categories, DefaultProductCatalog.products(Instant.EPOCH))
    private val merchants = FakeMerchantRepository()
    private val lists = FakeShoppingListRepository()

    private val draft = Purchase(
        id = "draft",
        type = PurchaseType.EXPRESS,
        status = PurchaseStatus.ACTIVE,
        occurredAt = Instant.parse("2026-10-21T08:45:00Z"),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun viewModel() = ExpressPurchaseViewModel(
        SavedStateHandle(mapOf(PurchaseViewModel.PURCHASE_ID_ARG to "draft")),
        purchases,
        merchants,
        catalog,
        categories,
        ObserveAccountBalancesUseCase(accounts, transactions),
        LastPaymentChoiceUseCase(purchases),
        ExpressPurchaseUseCase(
            purchases,
            catalog,
            SuggestPriceUseCase(purchases),
            SavePurchaseItemUseCase(purchases, catalog),
            SaveProductUseCase(catalog, clock),
            clock,
        ),
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

    @Test
    fun frequentExpenseIsSavedInTwoTapsWithLastPayment() = runTest {
        purchases.create(
            draft.copy(id = "previous", status = PurchaseStatus.COMPLETED, paymentMethod = PaymentMethod.CASH, accountId = "cash", completedAt = Instant.EPOCH),
        )
        purchases.create(draft)
        val vm = viewModel()
        val coffee = vm.uiState.value.favorites.first { it.name == "Café" }
        assertEquals(ExpressPayment(PaymentMethod.CASH, "cash"), vm.uiState.value.payment)

        vm.onProductTap(coffee)
        vm.onSave()

        val saved = purchases.getById("draft")!!
        assertEquals(PurchaseStatus.COMPLETED, saved.status)
        assertEquals(PaymentMethod.CASH, saved.paymentMethod)
        assertEquals(draft.occurredAt, saved.occurredAt)
        val transaction = transactions.transactions.value.single()
        assertEquals("cash", transaction.accountId)
        assertEquals(TransactionSource.PURCHASE, transaction.source)
        assertEquals(80L, transaction.amount.amountMinor)
        assertTrue(vm.uiState.value.isClosed)
    }

    @Test
    fun withoutHistoryDefaultsToMbAndDraftIsResumed() = runTest {
        purchases.create(draft)
        val vm = viewModel()
        assertEquals(ExpressPayment(PaymentMethod.MB, "bank"), vm.uiState.value.payment)
        val pastry = vm.uiState.value.favorites.first { it.name == "Pastel de nata" }
        vm.onProductTap(pastry)
        vm.onProductTap(pastry)

        val reopened = viewModel()

        assertEquals(listOf("Pastel de nata"), reopened.uiState.value.items.map { it.name })
        assertEquals(Money(260, "EUR"), reopened.uiState.value.total)
        assertEquals(mapOf(pastry.id to 2), reopened.uiState.value.quantities.mapValues { it.value.toInt() })
    }

    @Test
    fun searchCreatesTemporaryProductAfterAskingPrice() = runTest {
        purchases.create(draft)
        val vm = viewModel()

        vm.onQueryChange("Gelado")
        assertTrue(vm.uiState.value.canCreateTemporary)
        vm.onCreateTemporary()
        vm.onPriceTextChange("abc")
        vm.onConfirmPrice()
        assertTrue(vm.uiState.value.pricePrompt!!.errors.isNotEmpty())
        vm.onPriceTextChange("2,50")
        vm.onConfirmPrice()

        val state = vm.uiState.value
        assertNull(state.pricePrompt)
        assertEquals("", state.query)
        val item = state.items.single()
        assertEquals("Gelado", item.name)
        assertEquals(setOf(item.productId), state.temporaryProductIds)
        assertEquals(Money(250, "EUR"), state.total)
    }
}
