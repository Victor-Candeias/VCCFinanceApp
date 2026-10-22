package pt.vcc.financeapp.feature.finance.dashboard

import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.vcc.financeapp.core.data.testing.FakeAccountRepository
import pt.vcc.financeapp.core.data.testing.FakeCategoryRepository
import pt.vcc.financeapp.core.data.testing.FakeTransactionRepository
import pt.vcc.financeapp.core.data.usecase.ObserveAccountBalancesUseCase
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.Category
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.Transaction
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.model.TransactionType

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private val account = Account("acc", "CGD", AccountType.BANK_ACCOUNT, Money.zero(), createdAt = Instant.EPOCH)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun chartsGroupSubcategoriesAndCoverTheLastSixMonths() = runTest {
        val categories = listOf(
            category("food", "Alimentação"),
            category("restaurants", "Restaurantes", parent = "food"),
        ) + (1..6).map { category("c$it", "Categoria $it") }
        val transactions = listOf(
            tx("1", "2026-10-02T12:00:00Z", 1_000, "food"),
            tx("2", "2026-10-03T12:00:00Z", 2_000, "restaurants"),
            tx("3", "2026-10-04T12:00:00Z", 500, null),
            tx("4", "2026-05-31T12:00:00Z", 9_999, "food"),
            tx("5", "2026-05-01T12:00:00Z", 7_777, "food"),
            // Abril fica fora dos 6 meses.
            tx("6", "2026-04-30T12:00:00Z", 5_555, "food"),
            tx("7", "2026-10-05T12:00:00Z", 100_000, null, TransactionType.INCOME),
            tx("usd", "2026-10-05T12:00:00Z", 100, null, currency = "USD"),
        ) + (1..6).map { tx("c$it", "2026-10-06T12:00:00Z", 100L * it, "c$it") }
        val repo = FakeTransactionRepository(transactions)
        val viewModel = DashboardViewModel(
            ObserveAccountBalancesUseCase(FakeAccountRepository(listOf(account)), repo),
            repo,
            FakeCategoryRepository(categories),
            clock,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals("EUR", state.chartCurrency)
        assertTrue(state.chartOmitsCurrencies)
        assertEquals(
            listOf("Alimentação" to 3_000L, "Categoria 6" to 600L, null to 500L, "Categoria 5" to 500L, "Categoria 4" to 400L, null to 600L),
            state.expensesByCategory.map { it.name to it.total.amountMinor },
        )
        assertTrue(state.expensesByCategory.last().isOthers)
        assertEquals((5L downTo 0L).map { YearMonth.of(2026, 10).minusMonths(it) }, state.monthlyTotals.map { it.month })
        assertEquals(listOf(17_776L, 0L, 0L, 0L, 0L, 5_600L), state.monthlyTotals.map { it.totals.expenses.amountMinor })
        assertEquals(100_000L, state.monthlyTotals.last().totals.income.amountMinor)
        assertEquals(Money(5_600, "EUR"), state.monthTotals.first { it.currency == "EUR" }.expenses)
    }

    private fun category(id: String, name: String, parent: String? = null) =
        Category(id, name, icon = "", color = "#123456", type = TransactionType.EXPENSE, parentCategoryId = parent)

    private fun tx(
        id: String,
        date: String,
        amountMinor: Long,
        categoryId: String?,
        type: TransactionType = TransactionType.EXPENSE,
        currency: String = "EUR",
    ) = Transaction(
        id = id,
        accountId = account.id,
        type = type,
        amount = Money(amountMinor, currency),
        description = id,
        bookingDate = Instant.parse(date),
        source = TransactionSource.MANUAL,
        categoryId = categoryId,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
