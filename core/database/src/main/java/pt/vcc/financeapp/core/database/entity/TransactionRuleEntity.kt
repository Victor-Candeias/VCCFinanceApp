package pt.vcc.financeapp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import pt.vcc.financeapp.core.model.RuleMatchField
import pt.vcc.financeapp.core.model.RuleMatchType

/** Regras são avaliadas por `priority` crescente; `accountId` nulo aplica-se a todas as contas. */
@Entity(
    tableName = "transaction_rules",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MerchantEntity::class,
            parentColumns = ["id"],
            childColumns = ["merchantId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("categoryId"),
        Index("merchantId"),
        Index("accountId"),
        Index("priority"),
    ],
)
data class TransactionRuleEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val matchField: RuleMatchField,
    val matchType: RuleMatchType,
    val pattern: String,
    val categoryId: String,
    val merchantId: String? = null,
    val accountId: String? = null,
    val priority: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)
