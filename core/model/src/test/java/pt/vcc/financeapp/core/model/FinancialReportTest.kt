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

    @Test
    fun purchaseTotalsByPaymentMatchTheAccountExpenses() {
        fun purchase(id: String, tx: String?, method: PaymentMethod, type: PurchaseType, merchant: String?) = Purchase(
            id = id, type = type, status = PurchaseStatus.COMPLETED, occurredAt = Instant.parse("2026-02-10T12:00:00Z"),
            merchantId = merchant, paymentMethod = method, transactionId = tx, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        )
        fun item(purchase: String, name: String, price: Long, product: String? = null) =
            PurchaseItem(id = "$purchase-$name", purchaseId = purchase, name = name, quantity = java.math.BigDecimal.ONE, unitPriceMinor = price, productId = product)
        val transactions = listOf(
            tx("t-mb", TransactionType.EXPENSE, 1_250, "food", account = "bank").copy(source = TransactionSource.PURCHASE),
            tx("t-cash", TransactionType.EXPENSE, 300, "food", account = "wallet").copy(source = TransactionSource.PURCHASE),
            tx("t-cash-2", TransactionType.EXPENSE, 200, "food", account = "wallet").copy(source = TransactionSource.PURCHASE),
            tx("t-manual", TransactionType.EXPENSE, 5_000, "food", account = "bank"),
            tx("t-old", TransactionType.EXPENSE, 999, "food", date = "2026-03-01T00:00:00Z").copy(source = TransactionSource.PURCHASE),
        )
        val purchases = listOf(
            purchase("p1", "t-mb", PaymentMethod.MB, PurchaseType.QUICK, "pingo"),
            purchase("p2", "t-cash", PaymentMethod.CASH, PurchaseType.EXPRESS, null),
            purchase("p3", "t-cash-2", PaymentMethod.CASH, PurchaseType.EXPRESS, null),
            purchase("p4", "t-old", PaymentMethod.MB, PurchaseType.QUICK, "pingo"),
            purchase("p5", null, PaymentMethod.MB, PurchaseType.QUICK, "pingo"),
        )
        val items = listOf(
            item("p1", "Leite", 950, "milk"), item("p1", "Pão", 300),
            item("p2", "Café", 300, "coffee"), item("p3", "café ", 200), item("p4", "Leite", 999, "milk"),
        )
        val report = FinancialReport.build(ReportPeriod.month(YearMonth.of(2026, 2)), transactions, zone).single()

        val purchasesReport = PurchaseReport.from(report, purchases, items)!!

        assertEquals(Money(1_750, "EUR"), purchasesReport.total)
        assertEquals(3, purchasesReport.purchaseCount)
        val byAccount = report.byAccount.associate { it.accountId to it.totals.expenses.amountMinor }
        val byMethod = purchasesReport.byPaymentMethod.associate { it.key to it.total.amountMinor }
        assertEquals(mapOf(PaymentMethod.MB to 1_250L, PaymentMethod.CASH to 500L), byMethod)
        assertEquals(byAccount.getValue("wallet"), byMethod.getValue(PaymentMethod.CASH))
        assertEquals(listOf(PurchaseTotal(PurchaseType.QUICK, Money(1_250, "EUR"), 1), PurchaseTotal(PurchaseType.EXPRESS, Money(500, "EUR"), 2)), purchasesReport.byType)
        assertEquals(listOf<String?>("pingo", null), purchasesReport.byMerchant.map { it.key })
        assertEquals(listOf("Leite" to 950L, "Café" to 300L, "Pão" to 300L, "café" to 200L), purchasesReport.byProduct.map { it.key to it.total.amountMinor })
        assertEquals(null, PurchaseReport.from(report, emptyList(), emptyList()))
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
