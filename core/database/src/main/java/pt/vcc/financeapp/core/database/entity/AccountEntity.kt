package pt.vcc.financeapp.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import pt.vcc.financeapp.core.model.AccountType

@Entity(
    tableName = "accounts",
    foreignKeys = [
        ForeignKey(
            entity = FinancialConnectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["connectionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("connectionId")],
)
data class AccountEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: AccountType,
    val institutionName: String? = null,
    val ibanEncrypted: String? = null,
    val currency: String = "EUR",
    val openingBalanceMinor: Long = 0L,
    val connectionId: String? = null,
    val isActive: Boolean = true,
    @ColumnInfo(defaultValue = "0") val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
