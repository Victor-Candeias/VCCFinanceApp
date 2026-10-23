package pt.vcc.financeapp.core.data.usecase

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import pt.vcc.financeapp.core.data.testing.FakeCatalogRepository
import pt.vcc.financeapp.core.data.testing.FakeCategoryRepository
import pt.vcc.financeapp.core.data.testing.FakeMerchantRepository
import pt.vcc.financeapp.core.model.Merchant
import pt.vcc.financeapp.core.model.Product
import pt.vcc.financeapp.core.data.usecase.CatalogValidationError as E

class CatalogUseCasesTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-21T09:30:00Z"), ZoneOffset.UTC)
    private val catalog = FakeCatalogRepository(DefaultProductCatalog.categories, DefaultProductCatalog.products(Instant.EPOCH))
    private val merchants = FakeMerchantRepository(listOf(Merchant("m-1", "Café Central", "cafe central", createdAt = Instant.EPOCH)))
    private val saveMerchant = SaveMerchantUseCase(merchants)
    private val saveCategory = SaveProductCategoryUseCase(catalog, FakeCategoryRepository(SystemCategories.all))
    private val saveProduct = SaveProductUseCase(catalog, clock)

    private fun errors(result: CatalogSaveResult<*>) = (result as CatalogSaveResult.Invalid).errors

    @Test
    fun merchantNamesAreUniqueIgnoringCaseAndAccents() = runTest {
        assertEquals(setOf(E.DUPLICATE_NAME), errors(saveMerchant(null, "  CAFE   central ")))
        assertEquals(setOf(E.BLANK_NAME), errors(saveMerchant(null, "   ")))
        assertEquals(setOf(E.NAME_TOO_LONG), errors(saveMerchant(null, "x".repeat(81))))
    }

    @Test
    fun renamingAndDeactivatingMerchantKeepsId() = runTest {
        val saved = saveMerchant("m-1", "Café Central (Baixa)", isActive = false) as CatalogSaveResult.Saved

        assertEquals("m-1", saved.value.id)
        assertEquals("cafe central (baixa)", saved.value.normalizedName)
        assertFalse(merchants.getById("m-1")!!.isActive)
        // O próprio nome não conta como duplicado.
        assertEquals("m-1", (saveMerchant("m-1", "café central (baixa)") as CatalogSaveResult.Saved).value.id)
    }

    @Test
    fun productCategoryRequiresExpenseFinanceCategoryAndUniqueName() = runTest {
        val incomeCategory = SystemCategories.all.first { it.type == pt.vcc.financeapp.core.model.TransactionType.INCOME }

        assertEquals(setOf(E.FINANCE_CATEGORY_MISMATCH), errors(saveCategory(ProductCategoryInput(name = "Bebidas", financeCategoryId = incomeCategory.id))))
        assertEquals(setOf(E.DUPLICATE_NAME), errors(saveCategory(ProductCategoryInput(name = "café"))))

        val saved = saveCategory(ProductCategoryInput(name = "Bebidas", financeCategoryId = "system-food-supermarket")) as CatalogSaveResult.Saved
        assertEquals(DefaultProductCatalog.categories.maxOf { it.sortOrder } + 1, saved.value.sortOrder)
    }

    @Test
    fun productValidatesPriceCategoryAndDuplicatesWithinCategory() = runTest {
        assertEquals(
            setOf(E.NEGATIVE_PRICE, E.CATEGORY_NOT_FOUND),
            errors(saveProduct(ProductInput(name = "Água", productCategoryId = "nope", defaultPriceMinor = -1))),
        )
        val espresso = catalog.getProduct("catalog-product-espresso")!!
        assertEquals(setOf(E.DUPLICATE_NAME), errors(saveProduct(ProductInput(name = espresso.name.uppercase(), productCategoryId = espresso.productCategoryId))))
        // Mesmo nome noutra categoria é permitido.
        assertEquals(CatalogSaveResult.Saved::class, saveProduct(ProductInput(name = espresso.name, productCategoryId = null))::class)
    }

    @Test
    fun savingTemporaryProductPromotesItToCatalog() = runTest {
        catalog.saveProduct(Product("tmp", "Iogurte", isTemporary = true, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH))

        val saved = saveProduct(ProductInput(id = "tmp", name = "Iogurte", defaultPriceMinor = 45, unit = " un ")) as CatalogSaveResult.Saved

        assertFalse(saved.value.isTemporary)
        assertEquals("un", saved.value.unit)
        assertEquals(clock.instant(), saved.value.updatedAt)
        assertEquals(true, catalog.observeProducts().first().any { it.id == "tmp" })
    }
}
