package pt.vcc.financeapp.core.model

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetProgressTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-15T10:00:00Z") // quinta-feira
    private val categories = listOf(
        Category("food", "Alimentação", icon = "", color = "", type = TransactionType.EXPENSE),
        Category("restaurants", "Restaurantes", icon = "", color = "", type = TransactionType.EXPENSE, parentCategoryId = "food"),
        Category("coffee", "Café", icon = "", color = "", type = TransactionType.EXPENSE, parentCategoryId = "restaurants"),
        Category("home", "Casa", icon = "", color = "", type = TransactionType.EXPENSE),
    )

    @Test
    fun calendarPeriodsContainNow() {
        fun range(period: BudgetPeriod) = BudgetPeriods.range(period, now, zone).let { (from, to) -> from.toString() to to.toString() }

        assertEquals("2026-10-12T00:00:00Z" to "2026-10-19T00:00:00Z", range(BudgetPeriod.WEEKLY))
        assertEquals("2026-10-01T00:00:00Z" to "2026-11-01T00:00:00Z", range(BudgetPeriod.MONTHLY))
        assertEquals("2026-10-01T00:00:00Z" to "2027-01-01T00:00:00Z", range(BudgetPeriod.QUARTERLY))
        assertEquals("2026-01-01T00:00:00Z" to "2027-01-01T00:00:00Z", range(BudgetPeriod.YEARLY))
    }

    @Test
    fun categoryBudgetCountsSubcategoriesAndOnlyBookedExpensesOfThePeriod() {
        val transactions = listOf(
            tx("1", 2_000, "food"),
            tx("2", 1_500, "restaurants"),
            tx("3", 500, "coffee"),
            tx("4", 9_999, "home"),
            tx("5", 9_999, "food", date = "2026-09-30T23:59:59Z"),
            tx("6", 9_999, "food", status = TransactionStatus.PENDING),
            tx("7", 9_999, "food", transferGroup = "t"),
            tx("8", 9_999, "food", currency = "USD"),
            tx("9", 9_999, "food", type = TransactionType.INCOME),
        )

        val progress = requireNotNull(BudgetProgress.calculate(budget(limit = 5_000, category = "food"), transactions, categories, now, zone))

        assertEquals(Money(4_000, "EUR"), progress.spent)
        assertEquals(Money(1_000, "EUR"), progress.remaining)
        assertEquals(80, progress.percentUsed)
        assertEquals(BudgetAlertLevel.WARNING, progress.alertLevel)
        assertEquals(Money(13_999, "EUR"), BudgetProgress.calculate(budget(limit = 5_000), transactions, categories, now, zone)?.spent)
    }

    @Test
    fun alertLevelsAndValidity() {
        val transactions = listOf(tx("1", 3_000, null))

        assertEquals(BudgetAlertLevel.OK, BudgetProgress.calculate(budget(limit = 4_000), transactions, categories, now, zone)?.alertLevel)
        assertEquals(BudgetAlertLevel.WARNING, BudgetProgress.calculate(budget(limit = 3_000), transactions, categories, now, zone)?.alertLevel)
        val exceeded = BudgetProgress.calculate(budget(limit = 2_000), transactions, categories, now, zone)
        assertEquals(BudgetAlertLevel.EXCEEDED, exceeded?.alertLevel)
        assertEquals(150, exceeded?.percentUsed)
        assertNull(BudgetProgress.calculate(budget(limit = 2_000).copy(isActive = false), transactions, categories, now, zone))
        assertNull(BudgetProgress.calculate(budget(limit = 2_000).copy(startDate = Instant.parse("2026-11-01T00:00:00Z")), transactions, categories, now, zone))
        assertNull(BudgetProgress.calculate(budget(limit = 2_000).copy(endDate = now), transactions, categories, now, zone))
    }

    private fun budget(limit: Long, category: String? = null) = Budget(
        id = "b",
        name = "Orçamento",
        limit = Money(limit, "EUR"),
        period = BudgetPeriod.MONTHLY,
        startDate = Instant.EPOCH,
        categoryId = category,
        createdAt = Instant.EPOCH,
    )

    private fun tx(
        id: String,
        amountMinor: Long,
        category: String?,
        type: TransactionType = TransactionType.EXPENSE,
        currency: String = "EUR",
        status: TransactionStatus = TransactionStatus.BOOKED,
        transferGroup: String? = null,
        date: String = "2026-10-10T12:00:00Z",
    ) = Transaction(
        id = id,
        accountId = "acc",
        type = type,
        amount = Money(amountMinor, currency),
        description = id,
        bookingDate = Instant.parse(date),
        source = TransactionSource.MANUAL,
        status = status,
        categoryId = category,
        transferGroupId = transferGroup,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
