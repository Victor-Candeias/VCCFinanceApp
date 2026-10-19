package pt.vcc.financeapp.core.model

import java.time.Instant

data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val openingBalance: Money,
    val institutionName: String? = null,
    val ibanEncrypted: String? = null,
    val connectionId: String? = null,
    val isActive: Boolean = true,
    val createdAt: Instant,
) {
    val currency: String get() = openingBalance.currency
}
