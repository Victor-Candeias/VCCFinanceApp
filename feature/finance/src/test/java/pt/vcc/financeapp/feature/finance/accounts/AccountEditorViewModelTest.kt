package pt.vcc.financeapp.feature.finance.accounts

import androidx.lifecycle.SavedStateHandle
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.vcc.financeapp.core.data.testing.FakeAccountRepository
import pt.vcc.financeapp.core.data.testing.FakeTransactionRepository
import pt.vcc.financeapp.core.data.usecase.SaveAccountUseCase
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.Transaction
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.model.TransactionType

@OptIn(ExperimentalCoroutinesApi::class)
class AccountEditorViewModelTest {
    private val accounts = FakeAccountRepository()
    private val transactions = FakeTransactionRepository()
    private val existing = Account(
        id = "acc-1",
        name = "Cartão",
        type = AccountType.CREDIT_CARD,
        openingBalance = Money(-123_450, "EUR"),
        createdAt = Instant.EPOCH,
    )

    private fun viewModel(accountId: String? = null) = AccountEditorViewModel(
        SavedStateHandle(mapOf(AccountEditorViewModel.ACCOUNT_ID_ARG to accountId)),
        accounts,
        transactions,
        SaveAccountUseCase(accounts, transactions, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun createsAccountFromPortugueseAmount() {
        val vm = viewModel()
        vm.onNameChange("Poupança")
        vm.onTypeChange(AccountType.SAVINGS)
        vm.onOpeningBalanceChange("1.234,56")

        vm.save()

        val stored = accounts.accounts.value.single()
        assertEquals("Poupança", stored.name)
        assertEquals(AccountType.SAVINGS, stored.type)
        assertEquals(Money(123_456, "EUR"), stored.openingBalance)
        assertTrue(vm.uiState.value.isFinished)
    }

    @Test
    fun invalidAmountAndBlankNameAreReportedWithoutSaving() {
        val vm = viewModel()
        vm.onOpeningBalanceChange("12,3,4")
        vm.save()
        assertEquals(setOf(AccountEditorError.INVALID_AMOUNT), vm.uiState.value.errors)

        vm.onOpeningBalanceChange("")
        vm.save()
        assertEquals(setOf(AccountEditorError.BLANK_NAME), vm.uiState.value.errors)
        assertTrue(accounts.accounts.value.isEmpty())
        assertFalse(vm.uiState.value.isFinished)
    }

    @Test
    fun loadsExistingAccountAndLocksCurrencyWhenItHasTransactions() {
        accounts.accounts.value = listOf(existing)
        transactions.transactions.value = listOf(
            Transaction(
                id = "tx-1",
                accountId = existing.id,
                type = TransactionType.EXPENSE,
                amount = Money(500, "EUR"),
                description = "Compra",
                bookingDate = Instant.EPOCH,
                source = TransactionSource.MANUAL,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )

        val vm = viewModel(existing.id)
        vm.onCurrencyChange("USD")

        val state = vm.uiState.value
        assertFalse(state.isNew)
        assertEquals("-1234,50", state.openingBalance)
        assertEquals("EUR", state.currency)
        assertTrue(state.isCurrencyLocked)
    }

    @Test
    fun deactivatesAndReactivatesWithoutDeleting() {
        accounts.accounts.value = listOf(existing)
        val vm = viewModel(existing.id)

        vm.toggleActive()
        assertFalse(accounts.accounts.value.single().isActive)

        viewModel(existing.id).toggleActive()
        assertTrue(accounts.accounts.value.single().isActive)
    }
}
