package pt.vcc.financeapp.feature.finance.transactions

import androidx.lifecycle.SavedStateHandle
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
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
import pt.vcc.financeapp.core.data.usecase.SaveTransferUseCase
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.TransactionType

@OptIn(ExperimentalCoroutinesApi::class)
class TransferEditorViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-21T09:00:00Z"), ZoneId.of("Europe/Lisbon"))
    private val checking = Account(
        id = "checking",
        name = "À ordem",
        type = AccountType.BANK_ACCOUNT,
        openingBalance = Money(0, "EUR"),
        createdAt = Instant.EPOCH,
    )
    private val accounts = FakeAccountRepository(
        listOf(
            checking,
            checking.copy(id = "savings", name = "Poupança", type = AccountType.SAVINGS),
            checking.copy(id = "usd", name = "Dólares", openingBalance = Money(0, "USD")),
        ),
    )
    private val transactions = FakeTransactionRepository()

    private fun viewModel(groupId: String? = null, withdrawal: Boolean = false) = TransferEditorViewModel(
        SavedStateHandle(
            mapOf(TransferEditorViewModel.TRANSFER_GROUP_ID_ARG to groupId, TransferEditorViewModel.WITHDRAWAL_ARG to withdrawal),
        ),
        transactions,
        accounts,
        SaveTransferUseCase(transactions, accounts, clock),
        clock,
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun createsTransferThenEditsAndDeletesBothLegs() {
        val vm = viewModel()
        assertEquals("checking" to "savings", vm.uiState.value.fromAccountId to vm.uiState.value.toAccountId)
        vm.onAmountChange("250")
        vm.onDescriptionChange("Poupança mensal")
        vm.save()

        assertTrue(vm.uiState.value.isFinished)
        val legs = transactions.transactions.value
        assertEquals(setOf(TransactionType.EXPENSE, TransactionType.INCOME), legs.map { it.type }.toSet())
        val groupId = legs.first().transferGroupId!!

        val editor = viewModel(groupId)
        assertEquals("250,00", editor.uiState.value.amount)
        editor.swapAccounts()
        editor.save()
        val outgoing = transactions.transactions.value.single { it.type == TransactionType.EXPENSE }
        assertEquals("savings", outgoing.accountId)

        viewModel(groupId).delete()
        assertTrue(transactions.transactions.value.isEmpty())
    }

    @Test
    fun reportsSameAccountAndCurrencyErrors() {
        val vm = viewModel()
        vm.onAmountChange("10")
        vm.onDescriptionChange("Teste")
        vm.onToAccountChange("checking")
        vm.save()
        assertEquals(setOf(TransferEditorError.SAME_ACCOUNT), vm.uiState.value.errors)

        vm.onToAccountChange("usd")
        vm.save()
        assertEquals(setOf(TransferEditorError.CURRENCY_MISMATCH), vm.uiState.value.errors)
        assertFalse(vm.uiState.value.isFinished)
        assertTrue(transactions.transactions.value.isEmpty())
    }

    @Test
    fun withdrawalMovesMoneyFromDefaultAccountToCash() {
        accounts.accounts.value = accounts.accounts.value.map { if (it.id == "savings") it.copy(isDefault = true) else it } +
            checking.copy(id = "cash", name = "Carteira", type = AccountType.CASH)

        val vm = viewModel(withdrawal = true)
        val state = vm.uiState.value
        assertTrue(state.isWithdrawal)
        assertEquals("savings" to "cash", state.fromAccountId to state.toAccountId)
        assertEquals(listOf("cash"), state.toOptions.map { it.id })
        assertTrue(state.fromOptions.none { it.type == AccountType.CASH })

        vm.prefillWithdrawalDescription("Levantamento")
        vm.onAmountChange("40")
        vm.save()

        assertTrue(vm.uiState.value.isFinished)
        val legs = transactions.transactions.value
        assertEquals("savings", legs.single { it.type == TransactionType.EXPENSE }.accountId)
        val incoming = legs.single { it.type == TransactionType.INCOME }
        assertEquals("cash", incoming.accountId)
        assertEquals(Money(4_000, "EUR"), incoming.amount)
        assertEquals("Levantamento", incoming.description)
        assertTrue(legs.all { it.isInternalTransfer })
    }

    @Test
    fun withdrawalDescriptionIsPrefilledOnlyOnce() {
        accounts.accounts.value = accounts.accounts.value + checking.copy(id = "cash", name = "Carteira", type = AccountType.CASH)
        val vm = viewModel(withdrawal = true)

        vm.prefillWithdrawalDescription("Levantamento")
        vm.onDescriptionChange("")
        vm.prefillWithdrawalDescription("Levantamento")

        assertEquals("", vm.uiState.value.description)
        val transfer = viewModel()
        transfer.prefillWithdrawalDescription("Levantamento")
        assertEquals("", transfer.uiState.value.description)
    }
}
