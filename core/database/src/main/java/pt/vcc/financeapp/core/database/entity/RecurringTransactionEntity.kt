package pt.vcc.financeapp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import pt.vcc.financeapp.core.model.RecurrenceFrequency
import pt.vcc.financeapp.core.model.TransactionType

/** Para recorrências `TRANSFER`, `transferAccountId` é a conta de destino. */
@Entity(
    tableName = "recurring_transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["transferAccountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = MerchantEntity::class,
            parentColumns = ["id"],
            childColumns = ["merchantId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("accountId"),
        Index("transferAccountId"),
        Index("categoryId"),
        Index("merchantId"),
        Index("nextOccurrenceAt"),
    ],
)
data class RecurringTransactionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val accountId: String,
    val transferAccountId: String? = null,
    val type: TransactionType,
    val amountMinor: Long,
    val currency: String = "EUR",
    val description: String,
    val categoryId: String? = null,
    val merchantId: String? = null,
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val startDate: Long,
    val endDate: Long? = null,
    val nextOccurrenceAt: Long,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)
