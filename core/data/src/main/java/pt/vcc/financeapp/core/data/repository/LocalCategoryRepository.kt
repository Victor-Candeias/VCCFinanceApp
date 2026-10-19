package pt.vcc.financeapp.core.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.vcc.financeapp.core.data.mapper.asDomain
import pt.vcc.financeapp.core.data.mapper.asEntity
import pt.vcc.financeapp.core.database.dao.CategoryDao
import pt.vcc.financeapp.core.database.entity.CategoryEntity
import pt.vcc.financeapp.core.model.Category

internal class LocalCategoryRepository @Inject constructor(
    private val dao: CategoryDao,
) : CategoryRepository {
    override fun observeActiveCategories(): Flow<List<Category>> =
        dao.observeActive().map { it.map(CategoryEntity::asDomain) }

    override fun observeAllCategories(): Flow<List<Category>> =
        dao.observeAll().map { it.map(CategoryEntity::asDomain) }

    override suspend fun getById(id: String): Category? = dao.getById(id)?.asDomain()

    override suspend fun save(category: Category) = dao.upsert(category.asEntity())

    override suspend fun deactivate(id: String) = dao.deactivate(id)
}
