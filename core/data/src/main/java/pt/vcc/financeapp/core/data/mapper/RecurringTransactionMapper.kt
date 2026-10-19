package pt.vcc.financeapp.core.data.mapper

import pt.vcc.financeapp.core.database.entity.RecurringTransactionEntity
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.RecurringTransaction

fun RecurringTransactionEntity.asDomain(): RecurringTransaction = RecurringTransaction(
    id = id,
    accountId = accountId,
    type = type,
    amount = Money(amountMinor, currency),
    description = description,
    frequency = frequency,
    interval = interval,
    startDate = startDate.toInstant(),
    nextOccurrenceAt = nextOccurrenceAt.toInstant(),
    endDate = endDate.toInstantOrNull(),
    transferAccountId = transferAccountId,
    categoryId = categoryId,
    merchantId = merchantId,
    isActive = isActive,
    createdAt = createdAt.toInstant(),
)

fun RecurringTransaction.asEntity(): RecurringTransactionEntity = RecurringTransactionEntity(
    id = id,
    accountId = accountId,
    transferAccountId = transferAccountId,
    type = type,
    amountMinor = amount.amountMinor,
    currency = amount.currency,
    description = description,
    categoryId = categoryId,
    merchantId = merchantId,
    frequency = frequency,
    interval = interval,
    startDate = startDate.toEpochMilli(),
    endDate = endDate?.toEpochMilli(),
    nextOccurrenceAt = nextOccurrenceAt.toEpochMilli(),
    isActive = isActive,
    createdAt = createdAt.toEpochMilli(),
)
