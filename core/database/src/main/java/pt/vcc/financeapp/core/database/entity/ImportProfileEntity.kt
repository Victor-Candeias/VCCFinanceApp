package pt.vcc.financeapp.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Perfil de importação por banco; o mapeamento fica em colunas explícitas (sem JSON) para migrar com SQL simples. */
@Entity(
    tableName = "import_profiles",
    indices = [Index(value = ["headerSignature"])],
)
data class ImportProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val headerSignature: String,
    val headerRowIndex: Int,
    val dateColumn: Int?,
    val descriptionColumn: Int?,
    val amountColumn: Int?,
    val debitColumn: Int?,
    val creditColumn: Int?,
    val valueDateColumn: Int?,
    val referenceColumn: Int?,
    val balanceColumn: Int?,
    val dateFormat: String,
    val invertAmounts: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
