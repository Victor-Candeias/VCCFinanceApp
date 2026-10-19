package pt.vcc.financeapp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import pt.vcc.financeapp.core.model.SyncStatus

@Entity(
    tableName = "sync_history",
    foreignKeys = [
        ForeignKey(
            entity = FinancialConnectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["connectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["connectionId", "startedAt"])],
)
data class SyncHistoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val connectionId: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val status: SyncStatus,
    val accountsSynced: Int = 0,
    val transactionsAdded: Int = 0,
    val transactionsUpdated: Int = 0,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)
