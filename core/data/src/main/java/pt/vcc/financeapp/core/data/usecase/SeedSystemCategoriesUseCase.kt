package pt.vcc.financeapp.core.data.usecase

import javax.inject.Inject
import pt.vcc.financeapp.core.data.repository.CategoryRepository
import pt.vcc.financeapp.core.model.Category
import pt.vcc.financeapp.core.model.TransactionType

/**
 * Categorias iniciais do README §5. Os IDs são estáveis para que a sementeira seja idempotente e
 * nunca sobreponha alterações do utilizador (nome, cor, desativação).
 */
object SystemCategories {
    private fun expense(id: String, name: String, icon: String, color: String, parent: String? = null) =
        Category("system-$id", name, icon, color, TransactionType.EXPENSE, parent?.let { "system-$it" }, isSystem = true)

    private fun income(id: String, name: String, icon: String, color: String) =
        Category("system-$id", name, icon, color, TransactionType.INCOME, isSystem = true)

    val all: List<Category> = listOf(
        expense("food", "Alimentação", "restaurant", "#E65100"),
        expense("food-supermarket", "Supermercado", "shopping_cart", "#EF6C00", parent = "food"),
        expense("food-coffee", "Café", "local_cafe", "#F57C00", parent = "food"),
        expense("food-restaurant", "Restaurante", "restaurant_menu", "#FB8C00", parent = "food"),
        expense("housing", "Habitação", "home", "#5D4037"),
        expense("transport", "Transportes", "directions_car", "#1565C0"),
        expense("health", "Saúde", "medical_services", "#C62828"),
        expense("education", "Educação", "school", "#6A1B9A"),
        expense("leisure", "Lazer", "sports_esports", "#2E7D32"),
        expense("subscriptions", "Subscrições", "subscriptions", "#00838F"),
        expense("other-expense", "Outros", "more_horiz", "#616161"),
        income("salary", "Ordenado", "payments", "#2E7D32"),
        income("allowance", "Subsídio", "card_giftcard", "#388E3C"),
        income("refund", "Reembolso", "undo", "#43A047"),
        income("other-income", "Outros", "more_horiz", "#616161"),
    )
}

/** Insere as [SystemCategories] em falta; seguro de chamar em cada arranque. */
class SeedSystemCategoriesUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
) {
    suspend operator fun invoke(): Int = categoryRepository.insertIfAbsent(SystemCategories.all)
}
