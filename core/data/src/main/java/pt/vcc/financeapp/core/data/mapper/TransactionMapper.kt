package pt.vcc.financeapp.core.data.mapper

import pt.vcc.financeapp.core.database.entity.TransactionEntity
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.Transaction

fun TransactionEntity.asDomain(): Transaction = Transaction(
    id = id,
    accountId = accountId,
    type = type,
    amount = Money(amountMinor, currency),
    description = description,
    bookingDate = bookingDate.toInstant(),
    source = source,
    status = status,
    valueDate = valueDate.toInstantOrNull(),
    categoryId = categoryId,
    merchantId = merchantId,
    externalId = externalId,
    transferGroupId = transferGroupId,
    importBatchId = importBatchId,
    notes = notes,
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
)

fun Transaction.asEntity(): TransactionEntity = TransactionEntity(
    id = id,
    accountId = accountId,
    type = type,
    amountMinor = amount.amountMinor,
    currency = amount.currency,
    description = description,
    categoryId = categoryId,
    merchantId = merchantId,
    bookingDate = bookingDate.toEpochMilli(),
    valueDate = valueDate?.toEpochMilli(),
    status = status,
    source = source,
    externalId = externalId,
    transferGroupId = transferGroupId,
    importBatchId = importBatchId,
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)
