package pt.vcc.financeapp.core.data.mapper

import pt.vcc.financeapp.core.database.entity.ProductCategoryEntity
import pt.vcc.financeapp.core.database.entity.ProductEntity
import pt.vcc.financeapp.core.database.entity.PurchaseEntity
import pt.vcc.financeapp.core.database.entity.PurchaseItemEntity
import pt.vcc.financeapp.core.database.entity.ShoppingListEntity
import pt.vcc.financeapp.core.database.entity.ShoppingListItemEntity
import pt.vcc.financeapp.core.model.Product
import pt.vcc.financeapp.core.model.ProductCategory
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.core.model.PurchaseItem
import pt.vcc.financeapp.core.model.ShoppingList
import pt.vcc.financeapp.core.model.ShoppingListItem

fun PurchaseEntity.asDomain(): Purchase = Purchase(
    id = id,
    type = type,
    status = status,
    occurredAt = occurredAt.toInstant(),
    currency = currency,
    totalMinor = totalMinor,
    merchantId = merchantId,
    shoppingListId = shoppingListId,
    budgetMinor = budgetMinor,
    paymentMethod = paymentMethod,
    accountId = accountId,
    categoryId = categoryId,
    transactionId = transactionId,
    notes = notes,
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
    completedAt = completedAt.toInstantOrNull(),
)

fun Purchase.asEntity(): PurchaseEntity = PurchaseEntity(
    id = id,
    type = type,
    status = status,
    occurredAt = occurredAt.toEpochMilli(),
    currency = currency,
    totalMinor = totalMinor,
    merchantId = merchantId,
    shoppingListId = shoppingListId,
    budgetMinor = budgetMinor,
    paymentMethod = paymentMethod,
    accountId = accountId,
    categoryId = categoryId,
    transactionId = transactionId,
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    completedAt = completedAt?.toEpochMilli(),
)

fun PurchaseItemEntity.asDomain(): PurchaseItem = PurchaseItem(
    id = id,
    purchaseId = purchaseId,
    name = name,
    quantity = quantity,
    unitPriceMinor = unitPriceMinor,
    discountMinor = discountMinor,
    unit = unit,
    productId = productId,
    shoppingListItemId = shoppingListItemId,
    categoryName = categoryName,
    priceSource = priceSource,
    position = position,
)

fun PurchaseItem.asEntity(): PurchaseItemEntity = PurchaseItemEntity(
    id = id,
    purchaseId = purchaseId,
    name = name,
    quantity = quantity,
    unitPriceMinor = unitPriceMinor,
    discountMinor = discountMinor,
    totalMinor = totalMinor,
    unit = unit,
    productId = productId,
    shoppingListItemId = shoppingListItemId,
    categoryName = categoryName,
    priceSource = priceSource,
    position = position,
)

fun ProductCategoryEntity.asDomain(): ProductCategory =
    ProductCategory(id, name, icon, financeCategoryId, sortOrder, isActive)

fun ProductCategory.asEntity(): ProductCategoryEntity =
    ProductCategoryEntity(id, name, icon, financeCategoryId, sortOrder, isActive)

fun ProductEntity.asDomain(): Product = Product(
    id = id,
    name = name,
    productCategoryId = productCategoryId,
    barcode = barcode,
    brand = brand,
    defaultPriceMinor = defaultPriceMinor,
    unit = unit,
    isFavorite = isFavorite,
    isActive = isActive,
    isTemporary = isTemporary,
    sortOrder = sortOrder,
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
)

fun Product.asEntity(): ProductEntity = ProductEntity(
    id = id,
    name = name,
    productCategoryId = productCategoryId,
    barcode = barcode,
    brand = brand,
    defaultPriceMinor = defaultPriceMinor,
    unit = unit,
    isFavorite = isFavorite,
    isActive = isActive,
    isTemporary = isTemporary,
    sortOrder = sortOrder,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

fun ShoppingListEntity.asDomain(): ShoppingList =
    ShoppingList(id, name, budgetMinor, isArchived, createdAt.toInstant(), updatedAt.toInstant())

fun ShoppingList.asEntity(): ShoppingListEntity =
    ShoppingListEntity(id, name, budgetMinor, isArchived, createdAt.toEpochMilli(), updatedAt.toEpochMilli())

fun ShoppingListItemEntity.asDomain(): ShoppingListItem =
    ShoppingListItem(id, listId, name, quantity, unit, estimatedPriceMinor, productId, position)

fun ShoppingListItem.asEntity(): ShoppingListItemEntity =
    ShoppingListItemEntity(id, listId, name, quantity, unit, estimatedPriceMinor, productId, position)
