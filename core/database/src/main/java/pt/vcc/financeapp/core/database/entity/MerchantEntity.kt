package pt.vcc.financeapp.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** `normalizedName` é a chave usada para reconhecer o mesmo estabelecimento em importações. */
@Entity(
    tableName = "merchants",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["defaultCategoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["normalizedName"], unique = true),
        Index("defaultCategoryId"),
    ],
)
data class MerchantEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val normalizedName: String,
    val defaultCategoryId: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)
