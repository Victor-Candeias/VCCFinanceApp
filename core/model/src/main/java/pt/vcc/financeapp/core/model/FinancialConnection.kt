package pt.vcc.financeapp.core.model

import java.time.Instant

data class FinancialConnection(
    val id: String,
    val provider: String,
    val institutionId: String,
    val institutionName: String,
    val status: ConnectionStatus,
    val consentExpiresAt: Instant? = null,
    val lastSyncAt: Instant? = null,
    val nextSyncAt: Instant? = null,
    val lastErrorCode: String? = null,
)
