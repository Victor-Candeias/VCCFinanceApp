package pt.vcc.financeapp.core.data.repository

import kotlinx.coroutines.flow.Flow
import pt.vcc.financeapp.core.model.Category

interface CategoryRepository {
    fun observeActiveCategories(): Flow<List<Category>>
    fun observeAllCategories(): Flow<List<Category>>
    suspend fun getById(id: String): Category?
    suspend fun save(category: Category)
    suspend fun deactivate(id: String)
}
