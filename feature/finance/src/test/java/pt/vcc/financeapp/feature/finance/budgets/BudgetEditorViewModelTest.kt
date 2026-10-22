package pt.vcc.financeapp.feature.finance.budgets

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
import pt.vcc.financeapp.core.data.testing.FakeBudgetRepository
import pt.vcc.financeapp.core.data.testing.FakeCategoryRepository
import pt.vcc.financeapp.core.data.usecase.BudgetValidationError
import pt.vcc.financeapp.core.data.usecase.SaveBudgetUseCase
import pt.vcc.financeapp.core.model.Budget
import pt.vcc.financeapp.core.model.BudgetPeriod
import pt.vcc.financeapp.core.model.Category
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.TransactionType

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetEditorViewModelTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private val categories = FakeCategoryRepository(
        listOf(
            Category("food", "Alimentação", icon = "", color = "#123456", type = TransactionType.EXPENSE),
            Category("salary", "Salário", icon = "", color = "#123456", type = TransactionType.INCOME),
        ),
    )
    private val existing = Budget(
        id = "b-1",
        name = "Comida",
        limit = Money(25_000, "EUR"),
        period = BudgetPeriod.MONTHLY,
        startDate = Instant.EPOCH,
        categoryId = "food",
        alertThresholdPercent = 90,
        createdAt = Instant.EPOCH,
    )
    private val budgets = FakeBudgetRepository(listOf(existing))

    private fun viewModel(budgetId: String? = null) = BudgetEditorViewModel(
        SavedStateHandle(mapOf(BudgetEditorViewModel.BUDGET_ID_ARG to budgetId)),
        budgets,
        categories,
        SaveBudgetUseCase(budgets, categories, Clock.fixed(now, ZoneOffset.UTC)),
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun createsBudgetOnlyOfferingExpenseCategories() {
        val vm = viewModel()
        assertEquals(listOf("food"), vm.uiState.value.categories.map { it.id })

        vm.save()
        assertTrue(BudgetValidationError.INVALID_AMOUNT in vm.uiState.value.errors)

        vm.onLimitChange("1.234,50")
        vm.onAlertThresholdChange("0")
        vm.save()
        assertEquals(setOf(BudgetValidationError.BLANK_NAME, BudgetValidationError.INVALID_THRESHOLD), vm.uiState.value.errors)
        assertFalse(vm.uiState.value.isFinished)

        vm.onNameChange("Supermercado")
        vm.onCategoryChange("food")
        vm.onPeriodChange(BudgetPeriod.WEEKLY)
        vm.onAlertThresholdChange("75")
        vm.save()

        assertTrue(vm.uiState.value.isFinished)
        val created = budgets.budgets.value.single { it.id != existing.id }
        assertEquals(Money(123_450, "EUR"), created.limit)
        assertEquals(BudgetPeriod.WEEKLY, created.period)
        assertEquals("food", created.categoryId)
        assertEquals(75, created.alertThresholdPercent)
        assertEquals(now, created.startDate)
    }

    @Test
    fun editsAndDeletesExistingBudget() {
        val vm = viewModel(existing.id)
        val loaded = vm.uiState.value
        assertFalse(loaded.isNew)
        assertEquals("250,00", loaded.limit)
        assertEquals("90", loaded.alertThreshold)

        vm.onLimitChange("300")
        vm.save()
        val saved = budgets.budgets.value.single()
        assertEquals(Money(30_000, "EUR"), saved.limit)
        assertEquals(Instant.EPOCH, saved.startDate)

        viewModel(existing.id).delete()
        assertFalse(budgets.budgets.value.single().isActive)
    }

    @Test
    fun reportsMissingBudget() {
        assertEquals(setOf(BudgetValidationError.NOT_FOUND), viewModel("missing").uiState.value.errors)
    }
}
