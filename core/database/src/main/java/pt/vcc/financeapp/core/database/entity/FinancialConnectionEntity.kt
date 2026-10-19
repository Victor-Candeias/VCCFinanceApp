package pt.vcc.financeapp.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import pt.vcc.financeapp.core.model.ConnectionStatus

/** Nunca guardar tokens bancários nesta tabela: ficam no backend seguro (README §5). */
@Entity(tableName = "financial_connections")
data class FinancialConnectionEntity(
    @PrimaryKey val id: String,
    val provider: String,
    val institutionId: String,
    val institutionName: String,
    val status: ConnectionStatus,
    val consentExpiresAt: Long? = null,
    val lastSyncAt: Long? = null,
    val nextSyncAt: Long? = null,
    val lastErrorCode: String? = null,
)
