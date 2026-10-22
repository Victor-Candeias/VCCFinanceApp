package pt.vcc.financeapp.feature.finance.reports

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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import pt.vcc.financeapp.core.data.testing.FakeAccountRepository
import pt.vcc.financeapp.core.data.testing.FakeCategoryRepository
import pt.vcc.financeapp.core.data.testing.FakeTransactionRepository
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.Category
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.ReportPeriod
import pt.vcc.financeapp.core.model.ReportPeriodType
import pt.vcc.financeapp.core.model.Transaction
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.model.TransactionType

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private val accounts = listOf(
        Account("cgd", "CGD", AccountType.BANK_ACCOUNT, Money.zero(), createdAt = Instant.EPOCH),
        Account("meal", "Cartão refeição", AccountType.MEAL_CARD, Money.zero(), createdAt = Instant.EPOCH),
    )
    private val categories = listOf(
        Category("food", "Alimentação", icon = "", color = "#123456", type = TransactionType.EXPENSE),
        Category("restaurants", "Restaurantes", icon = "", color = "#123456", type = TransactionType.EXPENSE, parentCategoryId = "food"),
        Category("salary", "Salário", icon = "", color = "#654321", type = TransactionType.INCOME),
    )
    private val transactions = listOf(
        tx("1", "2026-10-01T09:00:00Z", 200_000, "salary", type = TransactionType.INCOME),
        tx("2", "2026-10-02T12:00:00Z", 3_000, "food"),
        tx("3", "2026-10-03T12:00:00Z", 1_500, "restaurants", account = "meal"),
        tx("4", "2026-10-04T12:00:00Z", 500, null),
        tx("5", "2026-03-10T12:00:00Z", 10_000, "food"),
        tx("usd", "2026-10-05T12:00:00Z", 900, null, currency = "USD"),
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun monthlyReportResolvesCategoriesAndAccounts() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals(ReportPeriod.month(YearMonth.of(2026, 10)), state.period)
        assertEquals(listOf("EUR", "USD"), state.currencies)
        val content = requireNotNull(state.content)
        assertEquals(Money(200_000, "EUR"), content.totals.income)
        assertEquals(Money(5_000, "EUR"), content.totals.expenses)
        assertEquals(listOf("Alimentação" to 4_500L, null to 500L), content.expensesByCategory.map { it.name to it.total.amountMinor })
        assertEquals(listOf("Salário"), content.incomeByCategory.map { it.name })
        assertEquals(listOf("CGD", "Cartão refeição"), content.byAccount.map { it.name })
        assertEquals(emptyList<Any>(), content.monthly)
        assertEquals(4, content.transactionCount)

        viewModel.selectCurrency("USD")
        assertEquals(Money(900, "USD"), viewModel.uiState.value.content?.totals?.expenses)
    }

    @Test
    fun yearlyReportHasMonthlyEvolutionAndPeriodNavigation() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.selectType(ReportPeriodType.YEAR)
        val year = viewModel.uiState.first { !it.isLoading && it.period == ReportPeriod.year(2026) }
        val content = requireNotNull(year.content)
        assertEquals(Money(15_000, "EUR"), content.totals.expenses)
        assertEquals(12, content.monthly.size)
        assertEquals(10_000L, content.monthly[2].totals.expenses.amountMinor)

        viewModel.previousPeriod()
        val empty = viewModel.uiState.first { !it.isLoading && it.period == ReportPeriod.year(2025) }
        assertNull(empty.content)

        viewModel.nextPeriod()
        viewModel.selectType(ReportPeriodType.MONTH)
        assertEquals(ReportPeriod.month(YearMonth.of(2026, 10)), viewModel.uiState.value.period)
    }

    private fun viewModel() = ReportsViewModel(
        FakeTransactionRepository(transactions),
        FakeAccountRepository(accounts),
        FakeCategoryRepository(categories),
        clock,
    )

    private fun tx(
        id: String,
        date: String,
        amountMinor: Long,
        categoryId: String?,
        type: TransactionType = TransactionType.EXPENSE,
        currency: String = "EUR",
        account: String = "cgd",
    ) = Transaction(
        id = id,
        accountId = account,
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
