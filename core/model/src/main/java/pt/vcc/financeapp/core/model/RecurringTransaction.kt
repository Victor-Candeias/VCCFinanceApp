package pt.vcc.financeapp.core.model

import java.time.Instant

data class RecurringTransaction(
    val id: String,
    val accountId: String,
    val type: TransactionType,
    val amount: Money,
    val description: String,
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val startDate: Instant,
    val nextOccurrenceAt: Instant,
    val endDate: Instant? = null,
    val transferAccountId: String? = null,
    val categoryId: String? = null,
    val merchantId: String? = null,
    val isActive: Boolean = true,
    val createdAt: Instant,
)
