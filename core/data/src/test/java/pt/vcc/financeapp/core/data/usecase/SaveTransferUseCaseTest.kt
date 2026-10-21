package pt.vcc.financeapp.core.data.usecase

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.financeapp.core.data.testing.FakeAccountRepository
import pt.vcc.financeapp.core.data.testing.FakeTransactionRepository
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.PeriodTotals
import pt.vcc.financeapp.core.model.TransactionType

class SaveTransferUseCaseTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    private val now = Instant.parse("2026-10-21T09:00:00Z")
    private fun account(id: String, currency: String = "EUR", active: Boolean = true) = Account(
        id = id,
        name = id,
        type = AccountType.BANK_ACCOUNT,
        openingBalance = Money(0, currency),
        isActive = active,
        createdAt = Instant.EPOCH,
    )

    private val accounts = FakeAccountRepository(
        listOf(account("checking"), account("savings"), account("usd", currency = "USD"), account("old", active = false)),
    )
    private val transactions = FakeTransactionRepository()
    private val save = SaveTransferUseCase(transactions, accounts, Clock.fixed(now, zone))

    private val input = TransferInput(
        fromAccountId = "checking",
        toAccountId = "savings",
        amountMinor = 20_000,
        description = "Poupança mensal",
        bookingDate = LocalDate.of(2026, 10, 21),
    )

    @Test
    fun createsLinkedOutgoingAndIncomingLegsExcludedFromGlobalTotals() = runTest {
        val legs = (save(input) as SaveTransferResult.Saved).legs

        val stored = transactions.transactions.value
        assertEquals(listOf(legs.outgoing, legs.incoming), stored)
        assertEquals(TransactionType.EXPENSE to "checking", legs.outgoing.type to legs.outgoing.accountId)
        assertEquals(TransactionType.INCOME to "savings", legs.incoming.type to legs.incoming.accountId)
        assertEquals(legs.outgoing.transferGroupId, legs.incoming.transferGroupId)
        assertEquals(legs.outgoing.amount, legs.incoming.amount)
        assertTrue(stored.all { it.isInternalTransfer && it.categoryId == null })
        assertTrue(PeriodTotals.byCurrency(stored).isEmpty())
    }

    @Test
    fun rejectsInvalidTransfers() = runTest {
        assertEquals(
            SaveTransferResult.Invalid(
                setOf(
                    TransferValidationError.SAME_ACCOUNT,
                    TransferValidationError.AMOUNT_NOT_POSITIVE,
                    TransferValidationError.BLANK_DESCRIPTION,
                ),
            ),
            save(input.copy(toAccountId = "checking", amountMinor = 0, description = " ")),
        )
        assertEquals(
            SaveTransferResult.Invalid(setOf(TransferValidationError.CURRENCY_MISMATCH)),
            save(input.copy(toAccountId = "usd")),
        )
        assertEquals(
            SaveTransferResult.Invalid(setOf(TransferValidationError.ACCOUNT_INACTIVE)),
            save(input.copy(toAccountId = "old")),
        )
        assertEquals(
            SaveTransferResult.Invalid(setOf(TransferValidationError.NOT_FOUND)),
            save(input.copy(transferGroupId = "missing")),
        )
        assertTrue(transactions.transactions.value.isEmpty())
    }

    @Test
    fun editingUpdatesBothLegsInPlace() = runTest {
        val original = (save(input) as SaveTransferResult.Saved).legs

        val updated = (
            save(input.copy(transferGroupId = original.transferGroupId, fromAccountId = "savings", toAccountId = "checking", amountMinor = 5_000))
                as SaveTransferResult.Saved
            ).legs

        assertEquals(original.outgoing.id, updated.outgoing.id)
        assertEquals(original.incoming.id, updated.incoming.id)
        assertEquals("savings", updated.outgoing.accountId)
        assertEquals(original.outgoing.bookingDate, updated.outgoing.bookingDate)
        assertEquals(listOf(updated.outgoing, updated.incoming), transactions.transactions.value)
    }
}
