package pt.vcc.financeapp.core.model

import java.time.Instant

data class Budget(
    val id: String,
    val name: String,
    val limit: Money,
    val period: BudgetPeriod,
    val startDate: Instant,
    val endDate: Instant? = null,
    val categoryId: String? = null,
    val alertThresholdPercent: Int = 80,
    val isActive: Boolean = true,
    val createdAt: Instant,
)
