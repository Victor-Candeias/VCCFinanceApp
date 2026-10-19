package pt.vcc.financeapp.core.data.mapper

import pt.vcc.financeapp.core.database.entity.MerchantEntity
import pt.vcc.financeapp.core.model.Merchant

fun MerchantEntity.asDomain(): Merchant = Merchant(
    id = id,
    name = name,
    normalizedName = normalizedName,
    defaultCategoryId = defaultCategoryId,
    isActive = isActive,
    createdAt = createdAt.toInstant(),
)

fun Merchant.asEntity(): MerchantEntity = MerchantEntity(
    id = id,
    name = name,
    normalizedName = normalizedName,
    defaultCategoryId = defaultCategoryId,
    isActive = isActive,
    createdAt = createdAt.toEpochMilli(),
)
