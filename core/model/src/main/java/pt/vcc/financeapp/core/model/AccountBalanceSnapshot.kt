package pt.vcc.financeapp.core.model

import java.time.Instant

/** Saldo comunicado pela instituição; distinto do saldo calculado a partir dos movimentos. */
data class AccountBalanceSnapshot(
    val id: String,
    val accountId: String,
    val balance: Money,
    val referenceDate: Instant,
    val source: BalanceSource,
    val createdAt: Instant,
)
