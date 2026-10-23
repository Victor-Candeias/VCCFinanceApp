package pt.vcc.financeapp.core.data.mapper

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test
import pt.vcc.financeapp.core.database.entity.ProductEntity
import pt.vcc.financeapp.core.database.entity.PurchaseEntity
import pt.vcc.financeapp.core.database.entity.PurchaseItemEntity
import pt.vcc.financeapp.core.model.PaymentMethod
import pt.vcc.financeapp.core.model.PriceSource
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.core.model.PurchaseType

class PurchaseMappersTest {
    @Test
    fun purchaseRoundTripKeepsAllFields() {
        val entity = PurchaseEntity(
            id = "pur-1",
            type = PurchaseType.PLANNED,
            status = PurchaseStatus.COMPLETED,
            occurredAt = 1_760_000_000_000,
            currency = "EUR",
            totalMinor = 4_250,
            merchantId = "mer-1",
            shoppingListId = "list-1",
            budgetMinor = 5_000,
            paymentMethod = PaymentMethod.CASH,
            accountId = "acc-cash",
            categoryId = "cat-1",
            transactionId = "tx-1",
            notes = "nota",
            createdAt = 1_759_000_000_000,
            updatedAt = 1_760_000_000_001,
            completedAt = 1_760_000_000_002,
        )

        assertEquals(entity, entity.asDomain().asEntity())
    }

    @Test
    fun purchaseItemRoundTripKeepsQuantityAndStoresComputedTotal() {
        val entity = PurchaseItemEntity(
            id = "item-1",
            purchaseId = "pur-1",
            name = "Bananas",
            quantity = BigDecimal("1.25"),
            unitPriceMinor = 129,
            discountMinor = 10,
            totalMinor = 151,
            unit = "kg",
            productId = "prod-1",
            shoppingListItemId = "li-1",
            categoryName = "Fruta",
            priceSource = PriceSource.OCR,
            position = 3,
        )

        val domain = entity.asDomain()

        assertEquals(151, domain.totalMinor)
        assertEquals(entity, domain.asEntity())
    }

    @Test
    fun productRoundTripKeepsFlags() {
        val entity = ProductEntity(
            id = "prod-1",
            name = "Café",
            productCategoryId = "cat-coffee",
            barcode = "5601234567890",
            brand = "Delta",
            defaultPriceMinor = 80,
            unit = "un",
            isFavorite = true,
            isActive = false,
            isTemporary = true,
            sortOrder = 4,
            createdAt = 1_760_000_000_000,
            updatedAt = 1_760_000_000_001,
        )

        assertEquals(entity, entity.asDomain().asEntity())
    }
}
