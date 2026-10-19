package pt.vcc.financeapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import pt.vcc.financeapp.core.database.entity.CategoryEntity

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE isActive = 1 ORDER BY name")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    // @Upsert faz UPDATE in-place; REPLACE apagaria a linha e dispararia SET_NULL nos movimentos.
    @Upsert
    suspend fun upsert(category: CategoryEntity)

    /** Para semear categorias de sistema sem sobrepor alterações do utilizador. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfAbsent(categories: List<CategoryEntity>): List<Long>

    @Query("UPDATE categories SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: String)
}
