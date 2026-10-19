package pt.vcc.financeapp.core.model

import java.time.Instant

data class Merchant(
    val id: String,
    val name: String,
    val normalizedName: String,
    val defaultCategoryId: String? = null,
    val isActive: Boolean = true,
    val createdAt: Instant,
)
