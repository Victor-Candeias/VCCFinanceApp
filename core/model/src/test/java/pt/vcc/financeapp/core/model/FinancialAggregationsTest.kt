package pt.vcc.financeapp.core.model

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinancialAggregationsTest {
    private val zone = ZoneOffset.UTC

    @Test
    fun expensesAreGroupedByRootCategoryExcludingTransfersAndPending() {
        val transactions = listOf(
            tx("1", TransactionType.EXPENSE, 1_000, category = "food"),
            tx("2", TransactionType.EXPENSE, 500, category = "restaurants"),
            tx("3", TransactionType.EXPENSE, 2_000, category = "home"),
            tx("4", TransactionType.EXPENSE, 300),
            tx("5", TransactionType.EXPENSE, 9_999, category = "home", transferGroup = "t"),
            tx("6", TransactionType.EXPENSE, 9_999, category = "home", status = TransactionStatus.PENDING),
            tx("7", TransactionType.EXPENSE, 9_999, category = "home", currency = "USD"),
            tx("8", TransactionType.INCOME, 9_999, category = "salary"),
        )

        val totals = FinancialAggregations.expensesByCategory(transactions, "EUR") { if (it == "restaurants") "food" else it }

        assertEquals(
            listOf(CategoryTotal("home", eur(2_000)), CategoryTotal("food", eur(1_500)), CategoryTotal(null, eur(300))),
            totals,
        )
    }

    @Test
    fun monthlyTotalsIncludeEmptyMonthsInOrder() {
        val months = FinancialAggregations.monthsEndingAt(YearMonth.of(2026, 2), 3)
        val transactions = listOf(
            tx("1", TransactionType.INCOME, 5_000, date = "2025-12-31T23:59:00Z"),
            tx("2", TransactionType.EXPENSE, 1_200, date = "2026-02-01T00:00:00Z"),
            tx("3", TransactionType.EXPENSE, 800, date = "2026-02-15T10:00:00Z", transferGroup = "t"),
        )

        val totals = FinancialAggregations.monthlyTotals(transactions, "EUR", months, zone)

        assertEquals(listOf(YearMonth.of(2025, 12), YearMonth.of(2026, 1), YearMonth.of(2026, 2)), totals.map { it.month })
        assertEquals(listOf(5_000L, 0L, 0L), totals.map { it.totals.income.amountMinor })
        assertEquals(listOf(0L, 0L, 1_200L), totals.map { it.totals.expenses.amountMinor })
        assertEquals(
            Instant.parse("2025-12-01T00:00:00Z") to Instant.parse("2026-03-01T00:00:00Z"),
            FinancialAggregations.range(months, zone),
        )
    }

    @Test
    fun mainCurrencyIsTheMostUsed() {
        val transactions = listOf(
            tx("1", TransactionType.EXPENSE, 1, currency = "USD"),
            tx("2", TransactionType.EXPENSE, 1, currency = "USD"),
            tx("3", TransactionType.EXPENSE, 1),
            tx("4", TransactionType.EXPENSE, 1, transferGroup = "t"),
            tx("5", TransactionType.EXPENSE, 1, transferGroup = "t"),
        )

        assertEquals("USD", FinancialAggregations.mainCurrency(transactions))
        assertEquals("EUR", FinancialAggregations.mainCurrency(transactions.take(1).map { it.copy(amount = eur(1)) } + transactions[2]))
        assertNull(FinancialAggregations.mainCurrency(emptyList()))
    }

    private fun eur(amountMinor: Long) = Money(amountMinor, "EUR")

    private fun tx(
        id: String,
        type: TransactionType,
        amountMinor: Long,
        category: String? = null,
        currency: String = "EUR",
        status: TransactionStatus = TransactionStatus.BOOKED,
        transferGroup: String? = null,
        date: String = "2026-02-10T12:00:00Z",
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
