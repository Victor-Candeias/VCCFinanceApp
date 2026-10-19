package pt.vcc.financeapp.core.model

data class Category(
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val type: TransactionType,
    val parentCategoryId: String? = null,
    val isSystem: Boolean = false,
    val isActive: Boolean = true,
)
