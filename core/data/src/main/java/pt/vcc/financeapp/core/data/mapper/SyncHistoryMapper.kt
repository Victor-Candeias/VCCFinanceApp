package pt.vcc.financeapp.core.data.mapper

import pt.vcc.financeapp.core.database.entity.SyncHistoryEntity
import pt.vcc.financeapp.core.model.SyncHistoryEntry

fun SyncHistoryEntity.asDomain(): SyncHistoryEntry = SyncHistoryEntry(
    id = id,
    connectionId = connectionId,
    startedAt = startedAt.toInstant(),
    status = status,
    finishedAt = finishedAt.toInstantOrNull(),
    accountsSynced = accountsSynced,
    transactionsAdded = transactionsAdded,
    transactionsUpdated = transactionsUpdated,
    errorCode = errorCode,
    errorMessage = errorMessage,
)

fun SyncHistoryEntry.asEntity(): SyncHistoryEntity = SyncHistoryEntity(
    id = id,
    connectionId = connectionId,
    startedAt = startedAt.toEpochMilli(),
    finishedAt = finishedAt?.toEpochMilli(),
    status = status,
    accountsSynced = accountsSynced,
    transactionsAdded = transactionsAdded,
    transactionsUpdated = transactionsUpdated,
    errorCode = errorCode,
    errorMessage = errorMessage,
)
