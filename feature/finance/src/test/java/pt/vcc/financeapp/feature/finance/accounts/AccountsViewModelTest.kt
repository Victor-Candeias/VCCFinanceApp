package pt.vcc.financeapp.feature.finance.accounts

import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import pt.vcc.financeapp.core.data.testing.FakeAccountRepository
import pt.vcc.financeapp.core.data.testing.FakeTransactionRepository
import pt.vcc.financeapp.core.data.usecase.ObserveAccountBalancesUseCase
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.Transaction
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.model.TransactionStatus
import pt.vcc.financeapp.core.model.TransactionType

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsViewModelTest {
    private val checking = Account(
        id = "checking",
        name = "À ordem",
        type = AccountType.BANK_ACCOUNT,
        openingBalance = Money(100_00, "EUR"),
        createdAt = Instant.EPOCH,
    )
    private val accounts = FakeAccountRepository(
        listOf(
            checking,
            checking.copy(id = "card", name = "Cartão", openingBalance = Money(-50_00, "EUR")),
            checking.copy(id = "old", name = "Antiga", isActive = false),
        ),
    )
    private val transactions = FakeTransactionRepository()

    private fun tx(id: String, type: TransactionType, amountMinor: Long, status: TransactionStatus = TransactionStatus.BOOKED) =
        Transaction(
            id = id,
            accountId = "checking",
            type = type,
            amount = Money(amountMinor, "EUR"),
            description = id,
            bookingDate = Instant.EPOCH,
            source = TransactionSource.MANUAL,
            status = status,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun showsBalancesAndTotalOfActiveAccountsUpdatingWithTransactions() = runTest {
        val vm = AccountsViewModel(ObserveAccountBalancesUseCase(accounts, transactions))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        assertEquals(listOf(Money(50_00, "EUR")), vm.uiState.value.totals)

        transactions.transactions.update {
            listOf(
                tx("salary", TransactionType.INCOME, 1_000_00),
                tx("rent", TransactionType.EXPENSE, 600_00),
                tx("pending", TransactionType.EXPENSE, 20_00, TransactionStatus.PENDING),
            )
        }

        val state = vm.uiState.first { it.totals != listOf(Money(50_00, "EUR")) }
        val balance = state.accounts.single { it.account.id == "checking" }.balance
        assertEquals(Money(500_00, "EUR"), balance.booked)
        assertEquals(Money(-20_00, "EUR"), balance.pending)
        assertEquals(listOf(Money(450_00, "EUR")), state.totals)
        assertEquals(listOf("old"), state.inactiveAccounts.map { it.account.id })
    }
}
