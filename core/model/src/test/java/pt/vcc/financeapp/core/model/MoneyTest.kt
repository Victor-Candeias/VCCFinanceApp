package pt.vcc.financeapp.core.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {
    @Test
    fun addsAndSubtractsSameCurrency() {
        val total = Money(123456, "EUR") + Money(44, "EUR") - Money(500, "EUR")

        assertEquals(Money(123000, "EUR"), total)
    }

    @Test
    fun rejectsMixingCurrencies() {
        assertThrows(IllegalArgumentException::class.java) { Money(100, "EUR") + Money(100, "USD") }
    }

    @Test
    fun rejectsInvalidCurrencyCode() {
        assertThrows(IllegalArgumentException::class.java) { Money(100, "eur") }
        assertThrows(IllegalArgumentException::class.java) { Money(100, "EURO") }
    }

    @Test
    fun failsOnOverflowInsteadOfWrapping() {
        assertThrows(ArithmeticException::class.java) { Money(Long.MAX_VALUE, "EUR") + Money(1, "EUR") }
        assertThrows(ArithmeticException::class.java) { -Money(Long.MIN_VALUE, "EUR") }
    }

    @Test
    fun transactionRejectsNegativeAmount() {
        val now = Instant.EPOCH
        assertThrows(IllegalArgumentException::class.java) {
            Transaction(
                id = "tx-1",
                accountId = "acc-1",
                type = TransactionType.EXPENSE,
                amount = Money(-1250, "EUR"),
                description = "Café",
                bookingDate = now,
                source = TransactionSource.MANUAL,
                createdAt = now,
                updatedAt = now,
            )
        }
    }
}
