package pt.vcc.financeapp.feature.finance.transactions

import androidx.lifecycle.SavedStateHandle
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.vcc.financeapp.core.data.testing.FakeAccountRepository
import pt.vcc.financeapp.core.data.testing.FakeCategoryRepository
import pt.vcc.financeapp.core.data.testing.FakeTransactionRepository
import pt.vcc.financeapp.core.data.usecase.SaveTransactionUseCase
import pt.vcc.financeapp.core.data.usecase.SystemCategories
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.TransactionStatus
import pt.vcc.financeapp.core.model.TransactionType

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionEditorViewModelTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    private val clock = Clock.fixed(Instant.parse("2026-10-21T09:00:00Z"), zone)
    private val account = Account(
        id = "acc-1",
        name = "Conta",
        type = AccountType.BANK_ACCOUNT,
        openingBalance = Money(0, "EUR"),
        createdAt = Instant.EPOCH,
    )
    private val accounts = FakeAccountRepository(listOf(account, account.copy(id = "acc-off", name = "Antiga", isActive = false)))
    private val categories = FakeCategoryRepository(SystemCategories.all)
    private val transactions = FakeTransactionRepository()

    private fun viewModel(transactionId: String? = null) = TransactionEditorViewModel(
        SavedStateHandle(mapOf(TransactionEditorViewModel.TRANSACTION_ID_ARG to transactionId)),
        transactions,
        accounts,
        categories,
        SaveTransactionUseCase(transactions, accounts, categories, clock),
        clock,
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun newTransactionDefaultsToTodayFirstActiveAccountAndGroupedCategories() {
        val state = viewModel().uiState.value

        assertEquals(LocalDate.of(2026, 10, 21), state.date)
        assertEquals(listOf("acc-1"), state.accounts.map { it.id })
        assertEquals("acc-1", state.accountId)
        val labels = state.categoriesForType.map { it.label }
        assertEquals(
            listOf("Alimentação", "Alimentação › Café", "Alimentação › Restaurante", "Alimentação › Supermercado"),
            labels.take(4),
        )
        assertTrue(state.categoriesForType.all { it.type == TransactionType.EXPENSE })
    }

    @Test
    fun newTransactionPreselectsDefaultAccount() {
        accounts.accounts.value = listOf(account, account.copy(id = "acc-2", name = "Zeta", isDefault = true))

        assertEquals("acc-2", viewModel().uiState.value.accountId)
    }

    @Test
    fun savesIncomeAndEditsItLater() {
        val vm = viewModel()
        vm.onTypeChange(TransactionType.INCOME)
        vm.onAmountChange("1.500,00")
        vm.onDescriptionChange("Ordenado outubro")
        vm.onCategoryChange("system-salary")
        vm.onPendingChange(true)
        vm.save()

        val saved = transactions.transactions.value.single()
        assertTrue(vm.uiState.value.isFinished)
        assertEquals(TransactionType.INCOME, saved.type)
        assertEquals(Money(150_000, "EUR"), saved.amount)
        assertEquals(TransactionStatus.PENDING, saved.status)

        val editor = viewModel(saved.id)
        assertEquals("1500,00", editor.uiState.value.amount)
        editor.onTypeChange(TransactionType.EXPENSE)
        assertNull(editor.uiState.value.categoryId)
        editor.onAmountChange("20")
        editor.save()
        assertEquals(Money(2_000, "EUR"), transactions.transactions.value.single().amount)
    }

    @Test
    fun reportsErrorsAndDeletesManualTransaction() {
        val vm = viewModel()
        vm.onAmountChange("-3")
        vm.save()
        assertEquals(setOf(TransactionEditorError.INVALID_AMOUNT), vm.uiState.value.errors)

        vm.onAmountChange("3")
        vm.save()
        assertEquals(setOf(TransactionEditorError.BLANK_DESCRIPTION), vm.uiState.value.errors)
        assertFalse(vm.uiState.value.isFinished)

        vm.onDescriptionChange("Café")
        vm.save()
        val editor = viewModel(transactions.transactions.value.single().id)
        editor.delete()
        assertTrue(transactions.transactions.value.isEmpty())
    }
}
