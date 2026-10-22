package pt.vcc.financeapp.core.data.mapper

import pt.vcc.financeapp.core.database.entity.AccountBalanceSnapshotEntity
import pt.vcc.financeapp.core.model.AccountBalanceSnapshot
import pt.vcc.financeapp.core.model.Money

fun AccountBalanceSnapshotEntity.asDomain(): AccountBalanceSnapshot = AccountBalanceSnapshot(
    id = id,
    accountId = accountId,
    balance = Money(balanceMinor, currency),
    referenceDate = referenceDate.toInstant(),
    source = source,
    createdAt = createdAt.toInstant(),
    importBatchId = importBatchId,
)

fun AccountBalanceSnapshot.asEntity(): AccountBalanceSnapshotEntity = AccountBalanceSnapshotEntity(
    id = id,
    accountId = accountId,
    balanceMinor = balance.amountMinor,
    currency = balance.currency,
    referenceDate = referenceDate.toEpochMilli(),
    source = source,
    createdAt = createdAt.toEpochMilli(),
    importBatchId = importBatchId,
)
