package pt.vcc.financeapp.core.data.usecase

import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
import pt.vcc.financeapp.core.model.Merchant
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.PaymentAccounts
import pt.vcc.financeapp.core.model.PaymentMethod
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.core.model.PurchaseItem
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.core.model.PurchaseType
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.model.TransactionType

class PurchaseUseCasesTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    private val now = Instant.parse("2026-10-21T09:30:00Z")
    private val clock = Clock.fixed(now, zone)

    private fun account(id: String, type: AccountType, isDefault: Boolean = false, isActive: Boolean = true) =
        Account(id, id, type, Money(0, "EUR"), isActive = isActive, isDefault = isDefault, createdAt = Instant.EPOCH)

    private val bank = account("bank", AccountType.BANK_ACCOUNT)
    private val cash = account("cash", AccountType.CASH)
    private val accounts = FakeAccountRepository(listOf(bank, cash, account("closed", AccountType.BANK_ACCOUNT, isActive = false)))
    private val transactions = FakeTransactionRepository()
    private val purchases = FakePurchaseRepository(transactions)
    private val catalog = FakeCatalogRepository(DefaultProductCatalog.categories, DefaultProductCatalog.products(Instant.EPOCH))
    private val merchants = FakeMerchantRepository(listOf(Merchant("m-1", "Pingo Doce", "pingo doce", createdAt = Instant.EPOCH)))
    private val complete = CompletePurchaseUseCase(
        purchases, accounts, FakeCategoryRepository(SystemCategories.all), transactions, catalog, merchants, FakeShoppingListRepository(), clock,
    )
    private val delete = DeletePurchaseUseCase(purchases)

    private val purchase = Purchase(
        id = "p-1",
        type = PurchaseType.QUICK,
        status = PurchaseStatus.ACTIVE,
        occurredAt = Instant.parse("2026-10-20T18:00:00Z"),
        merchantId = "m-1",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun item(id: String, quantity: String, price: Long, productId: String? = null) =
        PurchaseItem(id, "p-1", id, BigDecimal(quantity), price, productId = productId)

    private suspend fun givenPurchase(vararg items: PurchaseItem, base: Purchase = purchase) = purchases.create(base, items.toList())

    @Test
    fun completingWithMbCreatesPurchaseExpenseOnBankAccount() = runTest {
        givenPurchase(item("leite", "2", 95), item("pao", "0.5", 300))

        val result = complete(CompletePurchaseInput("p-1", PaymentMethod.MB, "bank")) as CompletePurchaseResult.Completed

        val tx = transactions.transactions.value.single()
        assertEquals(tx, result.transaction)
        assertEquals("bank", tx.accountId)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals(TransactionSource.PURCHASE, tx.source)
        assertEquals(Money(340, "EUR"), tx.amount)
        assertEquals("Pingo Doce", tx.description)
        assertEquals("m-1", tx.merchantId)
        assertEquals(purchase.occurredAt, tx.bookingDate)
        assertEquals("system-food-supermarket", tx.categoryId)
        val stored = purchases.getById("p-1")!!
        assertEquals(PurchaseStatus.COMPLETED, stored.status)
        assertEquals(tx.id, stored.transactionId)
        assertEquals(PaymentMethod.MB, stored.paymentMethod)
        assertEquals(340, stored.totalMinor)
        assertEquals(now, stored.completedAt)
    }

    @Test
    fun expressPurchaseUsesFinanceCategoryOfCatalogItems() = runTest {
        givenPurchase(item("cafe", "2", 80, "catalog-product-espresso"), base = purchase.copy(type = PurchaseType.EXPRESS, merchantId = null))

        complete(CompletePurchaseInput("p-1", PaymentMethod.CASH, "cash"))

        val tx = transactions.transactions.value.single()
        assertEquals("system-food-coffee", tx.categoryId)
        assertEquals("cafe", tx.description)
        assertEquals("cash", tx.accountId)
    }

    @Test
    fun mbRejectsCashAccountAndCashRejectsBankAccountWithoutWriting() = runTest {
        givenPurchase(item("a", "1", 100))

        assertEquals(
            CompletePurchaseResult.Invalid(setOf(CompletePurchaseError.ACCOUNT_NOT_ALLOWED)),
            complete(CompletePurchaseInput("p-1", PaymentMethod.MB, "cash")),
        )
        assertEquals(
            CompletePurchaseResult.Invalid(setOf(CompletePurchaseError.ACCOUNT_NOT_ALLOWED)),
            complete(CompletePurchaseInput("p-1", PaymentMethod.CASH, "bank")),
        )
        assertEquals(
            CompletePurchaseResult.Invalid(setOf(CompletePurchaseError.ACCOUNT_INACTIVE)),
            complete(CompletePurchaseInput("p-1", PaymentMethod.MB, "closed")),
        )
        assertTrue(transactions.transactions.value.isEmpty())
        assertEquals(PurchaseStatus.ACTIVE, purchases.getById("p-1")!!.status)
    }

    @Test
    fun editingCompletedPurchaseUpdatesTheSameTransaction() = runTest {
        givenPurchase(item("a", "1", 100))
        val first = (complete(CompletePurchaseInput("p-1", PaymentMethod.MB, "bank")) as CompletePurchaseResult.Completed).transaction!!

        purchases.saveItem(item("b", "1", 250))
        complete(CompletePurchaseInput("p-1", PaymentMethod.CASH, "cash", date = LocalDate.of(2026, 10, 18)))

        val tx = transactions.transactions.value.single()
        assertEquals(first.id, tx.id)
        assertEquals(first.createdAt, tx.createdAt)
        assertEquals("cash", tx.accountId)
        assertEquals(Money(350, "EUR"), tx.amount)
        assertEquals(Instant.parse("2026-10-18T11:00:00Z"), tx.bookingDate)
        assertEquals(PaymentMethod.CASH, purchases.getById("p-1")!!.paymentMethod)
    }

    @Test
    fun zeroTotalHasNoTransactionAndRemovesPreviousOne() = runTest {
        givenPurchase(item("a", "1", 100))
        complete(CompletePurchaseInput("p-1", PaymentMethod.MB, "bank"))

        purchases.removeItem("p-1", "a")
        val result = complete(CompletePurchaseInput("p-1", PaymentMethod.MB, "bank")) as CompletePurchaseResult.Completed

        assertNull(result.transaction)
        assertNull(purchases.getById("p-1")!!.transactionId)
        assertTrue(transactions.transactions.value.isEmpty())
    }

    @Test
    fun deletingPurchaseDeletesItsTransaction() = runTest {
        givenPurchase(item("a", "1", 100))
        complete(CompletePurchaseInput("p-1", PaymentMethod.MB, "bank"))

        assertTrue(delete("p-1"))

        assertNull(purchases.getById("p-1"))
        assertTrue(purchases.items.value.isEmpty())
        assertTrue(transactions.transactions.value.isEmpty())
    }

    @Test
    fun cancelledOrMissingPurchaseCannotBeCompleted() = runTest {
        givenPurchase(item("a", "1", 100), base = purchase.copy(status = PurchaseStatus.CANCELLED))

        assertEquals(
            CompletePurchaseResult.Invalid(setOf(CompletePurchaseError.CANCELLED)),
            complete(CompletePurchaseInput("p-1", PaymentMethod.MB, "bank")),
        )
        assertEquals(
            CompletePurchaseResult.Invalid(setOf(CompletePurchaseError.NOT_FOUND)),
            complete(CompletePurchaseInput("nope", PaymentMethod.MB, "bank")),
        )
    }

    @Test
    fun purchaseTransactionsCannotBeEditedInMovements() = runTest {
        givenPurchase(item("a", "1", 100))
        val tx = (complete(CompletePurchaseInput("p-1", PaymentMethod.MB, "bank")) as CompletePurchaseResult.Completed).transaction!!
        val save = SaveTransactionUseCase(transactions, accounts, FakeCategoryRepository(SystemCategories.all), clock)

        val result = save(
            TransactionInput(tx.id, "bank", TransactionType.EXPENSE, 999, "x", LocalDate.of(2026, 10, 20)),
        ) as SaveTransactionResult.Invalid

        assertTrue(TransactionValidationError.PURCHASE_LINKED in result.errors)
    }

    @Test
    fun defaultPaymentAccountPrefersDefaultThenBankAccount() {
        val card = account("card", AccountType.CREDIT_CARD)
        assertEquals("bank", PaymentAccounts.defaultFor(PaymentMethod.MB, listOf(card, bank, cash))?.id)
        assertEquals("card", PaymentAccounts.defaultFor(PaymentMethod.MB, listOf(card.copy(isDefault = true), bank))?.id)
        assertEquals("cash", PaymentAccounts.defaultFor(PaymentMethod.CASH, listOf(bank.copy(isDefault = true), cash))?.id)
        assertNull(PaymentAccounts.defaultFor(PaymentMethod.CASH, listOf(bank)))
    }
}
