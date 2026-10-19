package pt.vcc.financeapp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.model.TransactionStatus
import pt.vcc.financeapp.core.model.TransactionType

/**
 * `amountMinor` é sempre positivo; `type` indica a direção. Uma transferência interna são dois
 * movimentos `TRANSFER` ligados por `transferGroupId` (README §5).
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
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
        ForeignKey(
            entity = ImportBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["importBatchId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("accountId"),
        Index("bookingDate"),
        Index("categoryId"),
        Index("merchantId"),
        Index("importBatchId"),
        Index("transferGroupId"),
        Index(value = ["accountId", "source", "externalId"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val accountId: String,
    val type: TransactionType,
    val amountMinor: Long,
    val currency: String = "EUR",
    val description: String,
    val categoryId: String? = null,
    val merchantId: String? = null,
    val bookingDate: Long,
    val valueDate: Long? = null,
    val status: TransactionStatus = TransactionStatus.BOOKED,
    val source: TransactionSource,
    val externalId: String? = null,
    val transferGroupId: String? = null,
    val importBatchId: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
