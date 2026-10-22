package pt.vcc.financeapp.core.model

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialReportTest {
    private val zone = ZoneOffset.UTC

    @Test
    fun periodNavigationAndTypeChanges() {
        val march = ReportPeriod.month(YearMonth.of(2026, 3))

        assertEquals(ReportPeriod.month(YearMonth.of(2026, 2)), march.previous())
        assertEquals(ReportPeriod.year(2027), ReportPeriod.year(2026).next())
        assertEquals(ReportPeriod.year(2026), march.withType(ReportPeriodType.YEAR, YearMonth.of(2026, 10)))
        assertEquals(march.withType(ReportPeriodType.YEAR, YearMonth.of(2026, 10)).withType(ReportPeriodType.MONTH, YearMonth.of(2026, 10)), ReportPeriod.month(YearMonth.of(2026, 10)))
        assertEquals(ReportPeriod.month(YearMonth.of(2025, 1)), ReportPeriod.year(2025).withType(ReportPeriodType.MONTH, YearMonth.of(2026, 10)))
        assertEquals(12, ReportPeriod.year(2026).months.size)
        assertEquals(
            Instant.parse("2026-01-01T00:00:00Z") to Instant.parse("2027-01-01T00:00:00Z"),
            ReportPeriod.year(2026).range(zone),
        )
    }

    @Test
    fun yearlyReportIsSplitByCurrencyAndGroupsCategories() {
        val transactions = listOf(
            tx("1", TransactionType.INCOME, 100_000, "salary", date = "2026-01-31T10:00:00Z"),
            tx("2", TransactionType.EXPENSE, 4_000, "food", date = "2026-01-15T10:00:00Z"),
            tx("3", TransactionType.EXPENSE, 1_000, "restaurants", date = "2026-06-15T10:00:00Z", account = "card"),
            tx("4", TransactionType.EXPENSE, 9_999, "food", date = "2025-12-31T23:59:59Z"),
            tx("5", TransactionType.EXPENSE, 9_999, "food", date = "2027-01-01T00:00:00Z"),
            tx("6", TransactionType.EXPENSE, 9_999, null, status = TransactionStatus.PENDING),
            tx("7", TransactionType.EXPENSE, 9_999, null, transferGroup = "t"),
            tx("8", TransactionType.EXPENSE, 700, null, currency = "USD"),
        )

        val reports = FinancialReport.build(ReportPeriod.year(2026), transactions, zone) { if (it == "restaurants") "food" else it }

        assertEquals(listOf("EUR", "USD"), reports.map { it.currency })
        val eur = reports.first()
        assertEquals(PeriodTotals(Money(100_000, "EUR"), Money(5_000, "EUR")), eur.totals)
        assertEquals(listOf(CategoryTotal("food", Money(5_000, "EUR"))), eur.expensesByCategory)
        assertEquals(listOf(CategoryTotal("salary", Money(100_000, "EUR"))), eur.incomeByCategory)
        assertEquals(
            listOf(
                AccountTotals("acc", PeriodTotals(Money(100_000, "EUR"), Money(4_000, "EUR"))),
                AccountTotals("card", PeriodTotals(Money(0, "EUR"), Money(1_000, "EUR"))),
            ),
            eur.byAccount,
        )
        assertEquals(12, eur.monthly.size)
        assertEquals(4_000L, eur.monthly[0].totals.expenses.amountMinor)
        assertEquals(1_000L, eur.monthly[5].totals.expenses.amountMinor)
        assertEquals(listOf("3", "1", "2"), eur.transactions.map { it.id })
        assertEquals(PeriodTotals(Money(0, "USD"), Money(700, "USD")), reports[1].totals)
        assertTrue(FinancialReport.build(ReportPeriod.year(2024), transactions, zone).isEmpty())
    }

    private fun tx(
        id: String,
        type: TransactionType,
        amountMinor: Long,
        category: String?,
        currency: String = "EUR",
        status: TransactionStatus = TransactionStatus.BOOKED,
        transferGroup: String? = null,
        date: String = "2026-02-10T12:00:00Z",
        account: String = "acc",
    ) = Transaction(
        id = id,
        accountId = account,
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
