package pt.vcc.financeapp.core.data.mapper

import pt.vcc.financeapp.core.database.entity.BudgetEntity
import pt.vcc.financeapp.core.model.Budget
import pt.vcc.financeapp.core.model.Money

fun BudgetEntity.asDomain(): Budget = Budget(
    id = id,
    name = name,
    limit = Money(amountMinor, currency),
    period = period,
    startDate = startDate.toInstant(),
    endDate = endDate.toInstantOrNull(),
    categoryId = categoryId,
    alertThresholdPercent = alertThresholdPercent,
    isActive = isActive,
    createdAt = createdAt.toInstant(),
)

fun Budget.asEntity(): BudgetEntity = BudgetEntity(
    id = id,
    name = name,
    categoryId = categoryId,
    amountMinor = limit.amountMinor,
    currency = limit.currency,
    period = period,
    startDate = startDate.toEpochMilli(),
    endDate = endDate?.toEpochMilli(),
    alertThresholdPercent = alertThresholdPercent,
    isActive = isActive,
    createdAt = createdAt.toEpochMilli(),
)
