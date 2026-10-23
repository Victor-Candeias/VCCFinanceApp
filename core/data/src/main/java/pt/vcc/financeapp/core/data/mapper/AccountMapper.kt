package pt.vcc.financeapp.core.data.mapper

import pt.vcc.financeapp.core.database.entity.AccountEntity
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.Money

fun AccountEntity.asDomain(): Account = Account(
    id = id,
    name = name,
    type = type,
    openingBalance = Money(openingBalanceMinor, currency),
    institutionName = institutionName,
    ibanEncrypted = ibanEncrypted,
    connectionId = connectionId,
    isActive = isActive,
    isDefault = isDefault,
    createdAt = createdAt.toInstant(),
)

fun Account.asEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    type = type,
    institutionName = institutionName,
    ibanEncrypted = ibanEncrypted,
    currency = openingBalance.currency,
    openingBalanceMinor = openingBalance.amountMinor,
    connectionId = connectionId,
    isActive = isActive,
    isDefault = isDefault,
    createdAt = createdAt.toEpochMilli(),
)
