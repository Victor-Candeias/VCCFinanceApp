package pt.vcc.financeapp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import pt.vcc.financeapp.core.model.BalanceSource

/**
 * Saldo comunicado pela instituição, guardado à parte do saldo calculado (README §1).
 * `importBatchId` liga o saldo final de um extrato à importação, para ser removido se esta for revertida.
 */
@Entity(
    tableName = "account_balance_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["accountId", "referenceDate"]), Index("importBatchId")],
)
data class AccountBalanceSnapshotEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val accountId: String,
    val balanceMinor: Long,
    val currency: String = "EUR",
    val referenceDate: Long,
    val source: BalanceSource,
    val createdAt: Long = System.currentTimeMillis(),
    val importBatchId: String? = null,
)
