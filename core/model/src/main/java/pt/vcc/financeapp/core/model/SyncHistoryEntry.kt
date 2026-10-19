package pt.vcc.financeapp.core.model

import java.time.Instant

data class SyncHistoryEntry(
    val id: String,
    val connectionId: String,
    val startedAt: Instant,
    val status: SyncStatus,
    val finishedAt: Instant? = null,
    val accountsSynced: Int = 0,
    val transactionsAdded: Int = 0,
    val transactionsUpdated: Int = 0,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)
