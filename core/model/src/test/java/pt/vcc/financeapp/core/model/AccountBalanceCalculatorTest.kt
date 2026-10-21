package pt.vcc.financeapp.core.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountBalanceCalculatorTest {
    private val checking = account("checking", openingMinor = 100_00)
    private val savings = account("savings", openingMinor = 0)

    private fun account(id: String, openingMinor: Long, currency: String = "EUR") = Account(
        id = id,
        name = id,
        type = AccountType.BANK_ACCOUNT,
        openingBalance = Money(openingMinor, currency),
        createdAt = Instant.EPOCH,
    )

    private var nextId = 0

    private fun tx(
        accountId: String,
        type: TransactionType,
        amountMinor: Long,
        status: TransactionStatus = TransactionStatus.BOOKED,
        currency: String = "EUR",
        transferGroupId: String? = null,
    ) = Transaction(
        id = "tx-${nextId++}",
        accountId = accountId,
        type = type,
        amount = Money(amountMinor, currency),
        description = "x",
        bookingDate = Instant.EPOCH,
        source = TransactionSource.MANUAL,
        status = status,
        transferGroupId = transferGroupId,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private val transactions = listOf(
        tx("checking", TransactionType.INCOME, 1_500_00),
        tx("checking", TransactionType.EXPENSE, 12_50),
        tx("checking", TransactionType.EXPENSE, 40_00, status = TransactionStatus.PENDING),
        tx("checking", TransactionType.EXPENSE, 99_99, status = TransactionStatus.CANCELLED),
        tx("checking", TransactionType.EXPENSE, 10_00, currency = "USD"),
        tx("checking", TransactionType.TRANSFER, 5_00),
        tx("checking", TransactionType.EXPENSE, 300_00, transferGroupId = "grp"),
        tx("savings", TransactionType.INCOME, 300_00, transferGroupId = "grp"),
    )

    @Test
    fun combinesOpeningBalanceBookedMovementsAndTransferLegs() {
        val balance = AccountBalanceCalculator.calculate(checking, transactions.map(Transaction::asBalanceMovement))

        assertEquals(Money(100_00 + 1_500_00 - 12_50 - 300_00, "EUR"), balance.booked)
        assertEquals(Money(-40_00, "EUR"), balance.pending)
        assertEquals(Money(100_00 + 1_500_00 - 12_50 - 300_00 - 40_00, "EUR"), balance.projected)
        assertEquals(1, balance.ignoredForeignMovements)
    }

    @Test
    fun transfersMoveMoneyBetweenAccountsWithoutChangingTheTotal() {
        val withoutTransfer = transactions.filter { it.transferGroupId == null }
        val before = AccountBalanceCalculator.calculateAll(listOf(checking, savings), withoutTransfer.map(Transaction::asBalanceMovement))
        val after = AccountBalanceCalculator.calculateAll(listOf(checking, savings), transactions.map(Transaction::asBalanceMovement))

        assertEquals(Money(300_00, "EUR"), after.getValue("savings").booked)
        assertEquals(
            AccountBalanceCalculator.totalsByCurrency(before.values),
            AccountBalanceCalculator.totalsByCurrency(after.values),
        )
    }

    @Test
    fun aggregatedMovementsGiveTheSameResultAsIndividualOnes() {
        val aggregated = transactions
            .groupBy { listOf(it.accountId, it.type, it.status, it.amount.currency) }
            .map { (_, items) ->
                val first = items.first()
                BalanceMovement(first.accountId, first.type, first.status, Money(items.sumOf { it.amount.amountMinor }, first.amount.currency))
            }

        assertEquals(
            AccountBalanceCalculator.calculateAll(listOf(checking, savings), transactions.map(Transaction::asBalanceMovement)),
            AccountBalanceCalculator.calculateAll(listOf(checking, savings), aggregated),
        )
    }

    @Test
    fun totalsNeverMixCurrencies() {
        val usd = account("usd", openingMinor = 50_00, currency = "USD")
        val balances = AccountBalanceCalculator.calculateAll(listOf(checking, savings, usd), emptyList()).values

        assertEquals(listOf(Money(100_00, "EUR"), Money(50_00, "USD")), AccountBalanceCalculator.totalsByCurrency(balances))
    }
}
