package pt.vcc.financeapp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import pt.vcc.financeapp.core.model.TransactionType

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentCategoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("parentCategoryId")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val type: TransactionType,
    val parentCategoryId: String? = null,
    val isSystem: Boolean = false,
    val isActive: Boolean = true,
)
