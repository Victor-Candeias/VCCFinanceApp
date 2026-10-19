package pt.vcc.financeapp.core.data.mapper

import pt.vcc.financeapp.core.database.entity.CategoryEntity
import pt.vcc.financeapp.core.model.Category

fun CategoryEntity.asDomain(): Category = Category(
    id = id,
    name = name,
    icon = icon,
    color = color,
    type = type,
    parentCategoryId = parentCategoryId,
    isSystem = isSystem,
    isActive = isActive,
)

fun Category.asEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    icon = icon,
    color = color,
    type = type,
    parentCategoryId = parentCategoryId,
    isSystem = isSystem,
    isActive = isActive,
)
