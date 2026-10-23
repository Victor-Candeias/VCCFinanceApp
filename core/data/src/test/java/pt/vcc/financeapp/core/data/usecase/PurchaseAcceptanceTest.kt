package pt.vcc.financeapp.core.data.usecase

import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
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
import pt.vcc.financeapp.core.model.FinancialReport
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.PaymentMethod
import pt.vcc.financeapp.core.model.PurchaseReport
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.core.model.PurchaseType
import pt.vcc.financeapp.core.model.ReportPeriod
import pt.vcc.financeapp.core.model.TransactionSource

/**
 * Aceitação das Compras (S8-12): os fluxos principais de ponta a ponta, encadeando os casos de uso reais sobre
 * repositórios em memória, e a coerência final entre compras, movimentos das contas e relatório.
 */
class PurchaseAcceptanceTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    private val clock = Clock.fixed(Instant.parse("2026-10-21T09:30:00Z"), zone)

    private val bank = Account("bank", "CGD", AccountType.BANK_ACCOUNT, Money(0, "EUR"), createdAt = Instant.EPOCH)
    private val cash = Account("cash", "Carteira", AccountType.CASH, Money(0, "EUR"), createdAt = Instant.EPOCH)
    private val accounts = FakeAccountRepository(listOf(bank, cash))
    private val transactions = FakeTransactionRepository()
    private val purchases = FakePurchaseRepository(transactions)
    private val catalog = FakeCatalogRepository(DefaultProductCatalog.categories, DefaultProductCatalog.products(Instant.EPOCH))
    private val merchants = FakeMerchantRepository()
    private val lists = FakeShoppingListRepository()

    private val start = GetOrCreateActivePurchaseUseCase(purchases, clock)
    private val saveItem = SavePurchaseItemUseCase(purchases, catalog)
    private val express = ExpressPurchaseUseCase(
        purchases, catalog, SuggestPriceUseCase(purchases), saveItem, SaveProductUseCase(catalog, clock), clock,
    )
    private val editHeader = EditPurchaseHeaderUseCase(purchases, merchants, clock)
    private val complete = CompletePurchaseUseCase(
        purchases, accounts, FakeCategoryRepository(SystemCategories.all), transactions, catalog, merchants, lists, clock,
    )
    private val delete = DeletePurchaseUseCase(purchases)
    private val saveList = SaveShoppingListUseCase(lists, clock)
    private val saveListItem = SaveShoppingListItemUseCase(lists, purchases, catalog, clock)
    private val startPlanned = StartPlannedPurchaseUseCase(purchases, lists, clock)

    private fun purchaseTransactions() = transactions.transactions.value.filter { it.source == TransactionSource.PURCHASE }

    @Test
    fun quickPurchasePaidByMbDebitsTheBankAccount() = runTest {
        val purchase = start(PurchaseType.QUICK)
        editHeader.setMerchant(purchase.id, "Pingo Doce")
        saveItem(PurchaseItemInput(purchase.id, name = "Leite", quantity = BigDecimal("2"), unitPriceMinor = 95))
        saveItem(PurchaseItemInput(purchase.id, name = "Pão", quantity = BigDecimal.ONE, unitPriceMinor = 150))

        val completed = complete(CompletePurchaseInput(purchase.id, PaymentMethod.MB, bank.id)) as CompletePurchaseResult.Completed

        val tx = purchaseTransactions().single()
        assertEquals(bank.id, tx.accountId)
        assertEquals(Money(340, "EUR"), tx.amount)
        assertEquals("Pingo Doce", tx.description)
        assertEquals(PurchaseStatus.COMPLETED, completed.purchase.status)
        assertEquals(tx.id, completed.purchase.transactionId)
        assertNull(purchases.getActive(PurchaseType.QUICK))
    }

    @Test
    fun expressPurchasePaidInCashDebitsTheCashAccount() = runTest {
        val purchase = start(PurchaseType.EXPRESS)
        assertTrue(express.add(purchase.id, "catalog-product-espresso", priceMinor = 80) is ExpressAddResult.Added)
        assertTrue(express.add(purchase.id, "catalog-product-espresso") is ExpressAddResult.Added)

        complete(CompletePurchaseInput(purchase.id, PaymentMethod.CASH, cash.id))

        val tx = purchaseTransactions().single()
        assertEquals(cash.id, tx.accountId)
        assertEquals(Money(160, "EUR"), tx.amount)
        assertEquals("system-food-coffee", tx.categoryId)
    }

    @Test
    fun plannedPurchaseFromListIsPaidAndEditedToCashThenDeleted() = runTest {
        val list = (saveList(null, "Semana", budgetMinor = 5_000) as SaveShoppingListResult.Saved).list
        val listItem = (saveListItem(ShoppingListItemInput(list.id, name = "Arroz", quantity = BigDecimal.ONE, estimatedPriceMinor = 120))
            as SaveShoppingListItemResult.Saved).item
        val purchase = (startPlanned(list.id) as StartPlannedPurchaseResult.Started).purchase
        saveItem(
            PurchaseItemInput(purchase.id, name = listItem.name, quantity = BigDecimal("2"), unitPriceMinor = 130, shoppingListItemId = listItem.id),
        )

        complete(CompletePurchaseInput(purchase.id, PaymentMethod.MB, bank.id))
        val mb = purchaseTransactions().single()
        assertEquals(Money(260, "EUR"), mb.amount)

        // Editar o pagamento atualiza o mesmo movimento, agora na conta Dinheiro.
        complete(CompletePurchaseInput(purchase.id, PaymentMethod.CASH, cash.id))
        val edited = purchaseTransactions().single()
        assertEquals(mb.id, edited.id)
        assertEquals(cash.id, edited.accountId)
        assertEquals(PaymentMethod.CASH, purchases.getById(purchase.id)!!.paymentMethod)

        // Eliminar a compra apaga também o movimento: o saldo da conta volta ao que era.
        assertTrue(delete(purchase.id))
        assertNull(purchases.getById(purchase.id))
        assertTrue(purchaseTransactions().isEmpty())
    }

    @Test
    fun reportTotalsByPaymentMatchTheAccountsAfterAllFlows() = runTest {
        val quick = start(PurchaseType.QUICK)
        saveItem(PurchaseItemInput(quick.id, name = "Detergente", quantity = BigDecimal.ONE, unitPriceMinor = 450))
        complete(CompletePurchaseInput(quick.id, PaymentMethod.MB, bank.id))
        val coffee = start(PurchaseType.EXPRESS)
        express.add(coffee.id, "catalog-product-espresso", priceMinor = 75)
        complete(CompletePurchaseInput(coffee.id, PaymentMethod.CASH, cash.id))

        val report = FinancialReport.build(ReportPeriod.month(YearMonth.of(2026, 10)), transactions.transactions.value, zone).single()
        val purchaseReport = requireNotNull(
            PurchaseReport.from(report, purchases.purchases.value, purchases.items.value),
        )

        val expensesByAccount = report.byAccount.associate { it.accountId to it.totals.expenses }
        val byPayment = purchaseReport.byPaymentMethod.associate { it.key to it.total }
        assertEquals(expensesByAccount.getValue(bank.id), byPayment.getValue(PaymentMethod.MB))
        assertEquals(expensesByAccount.getValue(cash.id), byPayment.getValue(PaymentMethod.CASH))
        assertEquals(report.totals.expenses, purchaseReport.total)
        assertEquals(2, purchaseReport.purchaseCount)
    }
}
