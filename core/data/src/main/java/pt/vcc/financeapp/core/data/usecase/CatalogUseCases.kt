package pt.vcc.financeapp.core.data.usecase

import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import pt.vcc.financeapp.core.data.repository.CatalogRepository
import pt.vcc.financeapp.core.data.repository.CategoryRepository
import pt.vcc.financeapp.core.data.repository.MerchantRepository
import pt.vcc.financeapp.core.model.Merchant
import pt.vcc.financeapp.core.model.Product
import pt.vcc.financeapp.core.model.ProductCategory
import pt.vcc.financeapp.core.model.TransactionType

enum class CatalogValidationError {
    BLANK_NAME,
    NAME_TOO_LONG,
    DUPLICATE_NAME,
    NEGATIVE_PRICE,
    CATEGORY_NOT_FOUND,
    /** A categoria financeira associada tem de ser de despesa. */
    FINANCE_CATEGORY_MISMATCH,
    NOT_FOUND,
}

sealed interface CatalogSaveResult<out T> {
    data class Saved<T>(val value: T) : CatalogSaveResult<T>
    data class Invalid(val errors: Set<CatalogValidationError>) : CatalogSaveResult<Nothing>
}

private const val MAX_NAME_LENGTH = 80

private fun validateName(name: String, errors: MutableSet<CatalogValidationError>): String {
    val trimmed = name.trim().replace(Regex("\\s+"), " ")
    if (trimmed.isEmpty()) errors += CatalogValidationError.BLANK_NAME
    if (trimmed.length > MAX_NAME_LENGTH) errors += CatalogValidationError.NAME_TOO_LONG
    return trimmed
}

/** Cria ou renomeia um estabelecimento; o nome normalizado (sem acentos/maiúsculas) tem de ser único. */
class SaveMerchantUseCase @Inject constructor(
    private val merchantRepository: MerchantRepository,
) {
    suspend operator fun invoke(id: String?, name: String, isActive: Boolean = true): CatalogSaveResult<Merchant> {
        val errors = mutableSetOf<CatalogValidationError>()
        val trimmed = validateName(name, errors)
        val existing = id?.let { merchantRepository.getById(it) }
        if (id != null && existing == null) errors += CatalogValidationError.NOT_FOUND
        val clash = trimmed.takeIf(String::isNotEmpty)?.let { merchantRepository.findByName(it) }
        if (clash != null && clash.id != id) errors += CatalogValidationError.DUPLICATE_NAME
        if (errors.isNotEmpty()) return CatalogSaveResult.Invalid(errors)

        if (existing == null) {
            val created = merchantRepository.findOrCreate(trimmed)
            return CatalogSaveResult.Saved(created)
        }
        val merchant = existing.copy(name = trimmed, normalizedName = Merchant.normalize(trimmed), isActive = isActive)
        merchantRepository.update(merchant)
        return CatalogSaveResult.Saved(merchant)
    }
}

data class ProductCategoryInput(
    val id: String? = null,
    val name: String,
    val financeCategoryId: String? = null,
    val isActive: Boolean = true,
)

class SaveProductCategoryUseCase @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val categoryRepository: CategoryRepository,
) {
    suspend operator fun invoke(input: ProductCategoryInput): CatalogSaveResult<ProductCategory> {
        val errors = mutableSetOf<CatalogValidationError>()
        val name = validateName(input.name, errors)
        val all = catalogRepository.observeCategories().first()
        val existing = input.id?.let { id -> all.find { it.id == id } }
        if (input.id != null && existing == null) errors += CatalogValidationError.NOT_FOUND
        if (all.any { it.id != input.id && Merchant.normalize(it.name) == Merchant.normalize(name) }) {
            errors += CatalogValidationError.DUPLICATE_NAME
        }
        if (input.financeCategoryId != null && categoryRepository.getById(input.financeCategoryId)?.type != TransactionType.EXPENSE) {
            errors += CatalogValidationError.FINANCE_CATEGORY_MISMATCH
        }
        if (errors.isNotEmpty()) return CatalogSaveResult.Invalid(errors)

        val category = existing?.copy(name = name, financeCategoryId = input.financeCategoryId, isActive = input.isActive)
            ?: ProductCategory(
                id = UUID.randomUUID().toString(),
                name = name,
                financeCategoryId = input.financeCategoryId,
                sortOrder = (all.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                isActive = input.isActive,
            )
        catalogRepository.saveCategory(category)
        return CatalogSaveResult.Saved(category)
    }
}

data class ProductInput(
    val id: String? = null,
    val name: String,
    val productCategoryId: String? = null,
    val defaultPriceMinor: Long? = null,
    val unit: String? = null,
    val barcode: String? = null,
    val isFavorite: Boolean = false,
    val isActive: Boolean = true,
)

/** Produto do catálogo; o nome é único entre os produtos da mesma categoria. */
class SaveProductUseCase @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(input: ProductInput): CatalogSaveResult<Product> {
        val errors = mutableSetOf<CatalogValidationError>()
        val name = validateName(input.name, errors)
        val existing = input.id?.let { catalogRepository.getProduct(it) }
        if (input.id != null && existing == null) errors += CatalogValidationError.NOT_FOUND
        if (input.defaultPriceMinor != null && input.defaultPriceMinor < 0) errors += CatalogValidationError.NEGATIVE_PRICE
        if (input.productCategoryId != null && catalogRepository.getCategory(input.productCategoryId) == null) {
            errors += CatalogValidationError.CATEGORY_NOT_FOUND
        }
        val duplicate = catalogRepository.observeProducts().first().any {
            it.id != input.id && it.productCategoryId == input.productCategoryId && Merchant.normalize(it.name) == Merchant.normalize(name)
        }
        if (duplicate) errors += CatalogValidationError.DUPLICATE_NAME
        if (errors.isNotEmpty()) return CatalogSaveResult.Invalid(errors)

        val now = clock.instant()
        val product = (existing ?: Product(id = UUID.randomUUID().toString(), name = name, createdAt = now, updatedAt = now)).copy(
            name = name,
            productCategoryId = input.productCategoryId,
            defaultPriceMinor = input.defaultPriceMinor,
            unit = input.unit?.trim()?.takeIf(String::isNotEmpty),
            barcode = input.barcode?.trim()?.takeIf(String::isNotEmpty),
            isFavorite = input.isFavorite,
            isActive = input.isActive,
            // Guardar no catálogo torna permanente um produto criado de passagem numa compra.
            isTemporary = false,
            updatedAt = now,
        )
        catalogRepository.saveProduct(product)
        return CatalogSaveResult.Saved(product)
    }
}
