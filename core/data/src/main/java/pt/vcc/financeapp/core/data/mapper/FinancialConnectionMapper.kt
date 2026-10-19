package pt.vcc.financeapp.core.data.mapper

import pt.vcc.financeapp.core.database.entity.FinancialConnectionEntity
import pt.vcc.financeapp.core.model.FinancialConnection

fun FinancialConnectionEntity.asDomain(): FinancialConnection = FinancialConnection(
    id = id,
    provider = provider,
    institutionId = institutionId,
    institutionName = institutionName,
    status = status,
    consentExpiresAt = consentExpiresAt.toInstantOrNull(),
    lastSyncAt = lastSyncAt.toInstantOrNull(),
    nextSyncAt = nextSyncAt.toInstantOrNull(),
    lastErrorCode = lastErrorCode,
)

fun FinancialConnection.asEntity(): FinancialConnectionEntity = FinancialConnectionEntity(
    id = id,
    provider = provider,
    institutionId = institutionId,
    institutionName = institutionName,
    status = status,
    consentExpiresAt = consentExpiresAt?.toEpochMilli(),
    lastSyncAt = lastSyncAt?.toEpochMilli(),
    nextSyncAt = nextSyncAt?.toEpochMilli(),
    lastErrorCode = lastErrorCode,
)
