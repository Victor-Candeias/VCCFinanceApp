package pt.vcc.financeapp.core.data.usecase

import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import pt.vcc.financeapp.core.data.repository.CatalogRepository
import pt.vcc.financeapp.core.model.Product
import pt.vcc.financeapp.core.model.ProductCategory

/**
 * Catálogo inicial das despesas rápidas (S8). IDs estáveis: a sementeira é idempotente e não
 * sobrepõe alterações do utilizador. Cada categoria aponta para uma categoria de despesa de [SystemCategories].
 */
object DefaultProductCatalog {
    val categories: List<ProductCategory> = listOf(
        ProductCategory("catalog-coffee", "Café", "local_cafe", "system-food-coffee", sortOrder = 0),
        ProductCategory("catalog-pastry", "Pastelaria", "bakery_dining", "system-food-coffee", sortOrder = 1),
        ProductCategory("catalog-meals", "Refeições", "restaurant_menu", "system-food-restaurant", sortOrder = 2),
        ProductCategory("catalog-transport", "Transportes", "directions_bus", "system-transport", sortOrder = 3),
        ProductCategory("catalog-supermarket", "Supermercado", "shopping_cart", "system-food-supermarket", sortOrder = 4),
        ProductCategory("catalog-other", "Outros", "more_horiz", "system-other-expense", sortOrder = 5),
    )

    fun products(now: Instant): List<Product> {
        fun product(id: String, name: String, category: String, priceMinor: Long, order: Int, favorite: Boolean = false) =
            Product(
                id = "catalog-product-$id",
                name = name,
                productCategoryId = "catalog-$category",
                defaultPriceMinor = priceMinor,
                isFavorite = favorite,
                sortOrder = order,
                createdAt = now,
                updatedAt = now,
            )
        return listOf(
            product("espresso", "Café", "coffee", 80, 0, favorite = true),
            product("galao", "Galão", "coffee", 140, 1),
            product("abatanado", "Abatanado", "coffee", 110, 2),
            product("pastel-nata", "Pastel de nata", "pastry", 130, 0, favorite = true),
            product("croissant", "Croissant", "pastry", 150, 1),
            product("torrada", "Torrada", "pastry", 180, 2),
            product("prato-dia", "Prato do dia", "meals", 900, 0, favorite = true),
            product("menu-almoco", "Menu almoço", "meals", 1050, 1),
            product("sopa", "Sopa", "meals", 200, 2),
            product("bilhete-metro", "Bilhete metro", "transport", 185, 0),
            product("bilhete-autocarro", "Bilhete autocarro", "transport", 210, 1),
            product("estacionamento", "Estacionamento", "transport", 150, 2),
            product("agua", "Água", "supermarket", 60, 0),
            product("pao", "Pão", "supermarket", 20, 1),
        )
    }
}

/** Insere o [DefaultProductCatalog] em falta; deve correr depois de [SeedSystemCategoriesUseCase]. */
class SeedProductCatalogUseCase @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(): Int =
        catalogRepository.insertIfAbsent(DefaultProductCatalog.categories, DefaultProductCatalog.products(clock.instant()))
}
