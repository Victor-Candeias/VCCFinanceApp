package pt.vcc.financeapp.core.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.vcc.financeapp.core.database.entity.AccountBalanceSnapshotEntity
import pt.vcc.financeapp.core.database.entity.AccountEntity
import pt.vcc.financeapp.core.database.entity.BudgetEntity
import pt.vcc.financeapp.core.database.entity.RecurringTransactionEntity
import pt.vcc.financeapp.core.database.entity.TransactionEntity
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.BalanceSource
import pt.vcc.financeapp.core.model.BudgetPeriod
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.RecurrenceFrequency
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.model.TransactionStatus
import pt.vcc.financeapp.core.model.TransactionType

/** Round-trip entidade → domínio → entidade com todos os campos preenchidos (incluindo opcionais). */
class EntityMappersTest {
    @Test
    fun accountRoundTripKeepsCurrencyAndOpeningBalance() {
        val entity = AccountEntity(
            id = "acc-1",
            name = "Cartão refeição",
            type = AccountType.MEAL_CARD,
            institutionName = "Edenred",
            ibanEncrypted = "cipher",
            currency = "EUR",
            openingBalanceMinor = 15_075,
            connectionId = "conn-1",
            isActive = false,
            createdAt = 1_760_000_000_123,
        )

        val domain = entity.asDomain()

        assertEquals(Money(15_075, "EUR"), domain.openingBalance)
        assertEquals(entity, domain.asEntity())
    }

    @Test
    fun transactionRoundTripKeepsAllFields() {
        val entity = TransactionEntity(
            id = "tx-1",
            accountId = "acc-1",
            type = TransactionType.TRANSFER,
            amountMinor = 123_456,
            currency = "EUR",
            description = "Transferência para poupança",
            categoryId = "cat-1",
            merchantId = "mer-1",
            bookingDate = 1_760_000_000_000,
            valueDate = 1_760_086_400_000,
            status = TransactionStatus.PENDING,
            source = TransactionSource.BANK_SYNC,
            externalId = "provider:REF-1",
            transferGroupId = "grp-1",
            importBatchId = "batch-1",
            notes = "nota",
            createdAt = 1_760_000_000_001,
            updatedAt = 1_760_000_000_002,
        )

        assertEquals(entity, entity.asDomain().asEntity())
    }

    @Test
    fun moneyBackedEntitiesRoundTrip() {
        val budget = BudgetEntity(
            id = "bud-1",
            name = "Supermercado",
            categoryId = "cat-1",
            amountMinor = 40_000,
            currency = "EUR",
            period = BudgetPeriod.MONTHLY,
            startDate = 1_759_276_800_000,
            endDate = 1_767_225_600_000,
            alertThresholdPercent = 90,
            isActive = true,
            createdAt = 1,
        )
        val recurring = RecurringTransactionEntity(
            id = "rec-1",
            accountId = "acc-1",
            transferAccountId = "acc-2",
            type = TransactionType.TRANSFER,
            amountMinor = 20_000,
            currency = "EUR",
            description = "Poupança mensal",
            categoryId = null,
            merchantId = null,
            frequency = RecurrenceFrequency.MONTHLY,
            interval = 1,
            startDate = 1_759_276_800_000,
            endDate = null,
            nextOccurrenceAt = 1_761_955_200_000,
            isActive = true,
            createdAt = 2,
        )
        val snapshot = AccountBalanceSnapshotEntity(
            id = "snap-1",
            accountId = "acc-1",
            balanceMinor = -5_000,
            currency = "EUR",
            referenceDate = 1_760_000_000_000,
            source = BalanceSource.BANK_SYNC,
            createdAt = 3,
            importBatchId = "batch-1",
        )

        assertEquals(budget, budget.asDomain().asEntity())
        assertEquals(recurring, recurring.asDomain().asEntity())
        assertEquals(snapshot, snapshot.asDomain().asEntity())
    }
}
