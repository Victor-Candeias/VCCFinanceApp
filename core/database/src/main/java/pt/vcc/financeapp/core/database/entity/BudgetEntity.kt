package pt.vcc.financeapp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import pt.vcc.financeapp.core.model.BudgetPeriod

/** Sem `categoryId` o orçamento aplica-se a todas as despesas. */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId")],
)
data class BudgetEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val categoryId: String? = null,
    val amountMinor: Long,
    val currency: String = "EUR",
    val period: BudgetPeriod,
    val startDate: Long,
    val endDate: Long? = null,
    val alertThresholdPercent: Int = 80,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)
