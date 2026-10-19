package pt.vcc.financeapp.core.data.mapper

import pt.vcc.financeapp.core.database.entity.TransactionRuleEntity
import pt.vcc.financeapp.core.model.TransactionRule

fun TransactionRuleEntity.asDomain(): TransactionRule = TransactionRule(
    id = id,
    name = name,
    matchField = matchField,
    matchType = matchType,
    pattern = pattern,
    categoryId = categoryId,
    merchantId = merchantId,
    accountId = accountId,
    priority = priority,
    isActive = isActive,
    createdAt = createdAt.toInstant(),
)

fun TransactionRule.asEntity(): TransactionRuleEntity = TransactionRuleEntity(
    id = id,
    name = name,
    matchField = matchField,
    matchType = matchType,
    pattern = pattern,
    categoryId = categoryId,
    merchantId = merchantId,
    accountId = accountId,
    priority = priority,
    isActive = isActive,
    createdAt = createdAt.toEpochMilli(),
)
