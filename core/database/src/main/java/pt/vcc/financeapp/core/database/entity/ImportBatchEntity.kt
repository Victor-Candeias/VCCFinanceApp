package pt.vcc.financeapp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import pt.vcc.financeapp.core.model.ImportFormat
import pt.vcc.financeapp.core.model.ImportStatus

/** `fileSha256` permite avisar quando o mesmo ficheiro é importado de novo na mesma conta. */
@Entity(
    tableName = "import_batches",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["accountId", "importedAt"]),
        Index(value = ["accountId", "fileSha256"]),
    ],
)
data class ImportBatchEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val accountId: String,
    val format: ImportFormat,
    val fileName: String,
    val fileSha256: String,
    val profileName: String? = null,
    val status: ImportStatus,
    val totalRows: Int = 0,
    val importedRows: Int = 0,
    val duplicateRows: Int = 0,
    val errorRows: Int = 0,
    val importedAt: Long = System.currentTimeMillis(),
)
