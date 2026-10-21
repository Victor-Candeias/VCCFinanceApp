package pt.vcc.financeapp.core.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodTotalsTest {
    private fun tx(
        type: TransactionType,
        amountMinor: Long,
        currency: String = "EUR",
        status: TransactionStatus = TransactionStatus.BOOKED,
    ) = Transaction(
        id = "tx-$type-$amountMinor-$currency-$status",
        accountId = "acc-1",
        type = type,
        amount = Money(amountMinor, currency),
        description = "Movimento",
        bookingDate = Instant.EPOCH,
        source = TransactionSource.MANUAL,
        status = status,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun sumsBookedIncomeAndExpensesPerCurrencyWithoutMixing() {
        val totals = PeriodTotals.byCurrency(
            listOf(
                tx(TransactionType.INCOME, 150_000),
                tx(TransactionType.EXPENSE, 4_550),
                tx(TransactionType.EXPENSE, 1_000, currency = "USD"),
            ),
        )

        assertEquals(
            listOf(
                PeriodTotals(income = Money(150_000, "EUR"), expenses = Money(4_550, "EUR")),
                PeriodTotals(income = Money(0, "USD"), expenses = Money(1_000, "USD")),
            ),
            totals,
        )
        assertEquals(Money(145_450, "EUR"), totals.first().net)
    }

    @Test
    fun ignoresPendingCancelledAndTransfers() {
        val totals = PeriodTotals.byCurrency(
            listOf(
                tx(TransactionType.EXPENSE, 1_000),
                tx(TransactionType.EXPENSE, 2_000, status = TransactionStatus.PENDING),
                tx(TransactionType.INCOME, 3_000, status = TransactionStatus.CANCELLED),
                tx(TransactionType.TRANSFER, 5_000),
            ),
        )

        assertEquals(listOf(PeriodTotals(income = Money(0, "EUR"), expenses = Money(1_000, "EUR"))), totals)
    }
}
