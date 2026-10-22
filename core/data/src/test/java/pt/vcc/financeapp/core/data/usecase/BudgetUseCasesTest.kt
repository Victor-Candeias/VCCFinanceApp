package pt.vcc.financeapp.core.data.usecase

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.financeapp.core.data.testing.FakeBudgetRepository
import pt.vcc.financeapp.core.data.testing.FakeCategoryRepository
import pt.vcc.financeapp.core.data.testing.FakeTransactionRepository
import pt.vcc.financeapp.core.model.Budget
import pt.vcc.financeapp.core.model.BudgetAlertLevel
import pt.vcc.financeapp.core.model.BudgetPeriod
import pt.vcc.financeapp.core.model.Category
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.Transaction
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.model.TransactionType

class BudgetUseCasesTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private val categories = FakeCategoryRepository(
        listOf(
            Category("food", "Alimentação", icon = "", color = "", type = TransactionType.EXPENSE),
            Category("salary", "Salário", icon = "", color = "", type = TransactionType.INCOME),
        ),
    )

    @Test
    fun saveValidatesInputAndKeepsCreationData() = runTest {
        val repository = FakeBudgetRepository()
        val save = SaveBudgetUseCase(repository, categories, clock)

        val invalid = save(BudgetInput(null, " ", "salary", 0, "eu", BudgetPeriod.MONTHLY, 0))
        assertEquals(
            setOf(
                BudgetValidationError.BLANK_NAME,
                BudgetValidationError.INVALID_AMOUNT,
                BudgetValidationError.INVALID_CURRENCY,
                BudgetValidationError.INVALID_THRESHOLD,
                BudgetValidationError.INVALID_CATEGORY,
            ),
            (invalid as SaveBudgetResult.Invalid).errors,
        )
        assertEquals(
            setOf(BudgetValidationError.NOT_FOUND),
            (save(BudgetInput("missing", "Casa", null, 100, "EUR", BudgetPeriod.MONTHLY, 80)) as SaveBudgetResult.Invalid).errors,
        )

        val created = (save(BudgetInput(null, " Comida ", "food", 30_000, "eur", BudgetPeriod.MONTHLY, 80)) as SaveBudgetResult.Saved).budget
        assertEquals("Comida", created.name)
        assertEquals(Money(30_000, "EUR"), created.limit)
        assertEquals(clock.instant(), created.startDate)

        val updated = save(BudgetInput(created.id, "Comida", "food", 40_000, "EUR", BudgetPeriod.WEEKLY, 90)) as SaveBudgetResult.Saved
        assertEquals(created.createdAt, updated.budget.createdAt)
        assertEquals(listOf(updated.budget), repository.budgets.value)
    }

    @Test
    fun observeProgressCoversEveryBudgetPeriodAndSkipsInactive() = runTest {
        val transactions = FakeTransactionRepository(
            listOf(
                expense("1", "2026-10-13T12:00:00Z", 6_000),
                expense("2", "2026-02-01T12:00:00Z", 50_000),
            ),
        )
        val budgets = FakeBudgetRepository(
            listOf(
                budget("week", BudgetPeriod.WEEKLY, 5_000),
                budget("year", BudgetPeriod.YEARLY, 100_000),
                budget("old", BudgetPeriod.MONTHLY, 1).copy(isActive = false),
            ),
        )

        val progress = ObserveBudgetProgressUseCase(budgets, transactions, categories, clock)().first()

        assertEquals(listOf("week", "year"), progress.map { it.budget.id })
        assertEquals(listOf(6_000L, 56_000L), progress.map { it.spent.amountMinor })
        assertEquals(listOf(BudgetAlertLevel.EXCEEDED, BudgetAlertLevel.OK), progress.map { it.alertLevel })
        assertEquals(Instant.parse("2026-01-01T00:00:00Z") to Instant.parse("2027-01-01T00:00:00Z"), transactions.requestedPeriods.last())
        assertTrue(ObserveBudgetProgressUseCase(FakeBudgetRepository(), transactions, categories, clock)().first().isEmpty())
    }

    private fun budget(id: String, period: BudgetPeriod, limit: Long) = Budget(
        id = id,
        name = id,
        limit = Money(limit, "EUR"),
        period = period,
        startDate = Instant.EPOCH,
        createdAt = Instant.EPOCH,
    )

    private fun expense(id: String, date: String, amountMinor: Long) = Transaction(
        id = id,
        accountId = "acc",
        type = TransactionType.EXPENSE,
        amount = Money(amountMinor, "EUR"),
        description = id,
        bookingDate = Instant.parse(date),
        source = TransactionSource.MANUAL,
        categoryId = "food",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
