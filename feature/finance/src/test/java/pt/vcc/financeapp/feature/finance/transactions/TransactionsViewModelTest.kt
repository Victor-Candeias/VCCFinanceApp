package pt.vcc.financeapp.feature.finance.transactions

import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
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
import org.junit.Before
import org.junit.Test
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.Transaction
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.model.TransactionType
import pt.vcc.financeapp.core.data.testing.FakeAccountRepository
import pt.vcc.financeapp.core.data.testing.FakeCategoryRepository
import pt.vcc.financeapp.core.data.testing.FakeTransactionRepository

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), zone)
    private val account = Account(
        id = "acc-1",
        name = "Conta à ordem",
        type = AccountType.BANK_ACCOUNT,
        openingBalance = Money.zero(),
        createdAt = Instant.EPOCH,
    )

    private fun tx(id: String, date: String, amountMinor: Long) = Transaction(
        id = id,
        accountId = account.id,
        type = TransactionType.EXPENSE,
        amount = Money(amountMinor, "EUR"),
        description = id,
        bookingDate = Instant.parse(date),
        source = TransactionSource.MANUAL,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun showsCurrentMonthAndNavigatesBetweenMonthsInLocalTime() = runTest {
        val transactions = FakeTransactionRepository(
            listOf(
                tx("outubro", "2026-09-30T23:30:00Z", 1_000),
                // 30/09 22:30 UTC = 23:30 em Lisboa (UTC+1): pertence a setembro.
                tx("setembro", "2026-09-30T22:30:00Z", 2_500),
            ),
        )
        val viewModel = TransactionsViewModel(transactions, FakeAccountRepository(listOf(account)), FakeCategoryRepository(), clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val october = viewModel.uiState.first { !it.isLoading }
        assertEquals(YearMonth.of(2026, 10), october.month)
        assertEquals(listOf("outubro"), october.items.map { it.transaction.id })
        assertEquals("Conta à ordem", october.items.single().accountName)

        viewModel.previousMonth()

        val september = viewModel.uiState.first { it.month == YearMonth.of(2026, 9) && !it.isLoading }
        assertEquals(listOf("setembro"), september.items.map { it.transaction.id })
        assertEquals(Money(2_500, "EUR"), september.totals.single().expenses)
    }
}
