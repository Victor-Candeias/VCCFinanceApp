package pt.vcc.financeapp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** `uri` aponta para o ficheiro no armazenamento privado da app ou para um URI do SAF. */
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("transactionId")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val transactionId: String,
    val fileName: String,
    val mimeType: String,
    val uri: String,
    val sizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis(),
)
