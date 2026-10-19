package pt.vcc.financeapp.core.model

import java.time.Instant

data class TransactionRule(
    val id: String,
    val name: String,
    val matchField: RuleMatchField,
    val matchType: RuleMatchType,
    val pattern: String,
    val categoryId: String,
    val merchantId: String? = null,
    val accountId: String? = null,
    val priority: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Instant,
)
