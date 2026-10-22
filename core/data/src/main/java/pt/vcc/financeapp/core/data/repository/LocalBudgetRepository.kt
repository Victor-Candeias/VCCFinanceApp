package pt.vcc.financeapp.core.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.vcc.financeapp.core.data.mapper.asDomain
import pt.vcc.financeapp.core.data.mapper.asEntity
import pt.vcc.financeapp.core.database.dao.BudgetDao
import pt.vcc.financeapp.core.database.entity.BudgetEntity
import pt.vcc.financeapp.core.model.Budget

internal class LocalBudgetRepository @Inject constructor(
    private val dao: BudgetDao,
) : BudgetRepository {
    override fun observeActiveBudgets(): Flow<List<Budget>> = dao.observeActive().map { it.map(BudgetEntity::asDomain) }

    override suspend fun getById(id: String): Budget? = dao.getById(id)?.asDomain()

    override suspend fun save(budget: Budget) = dao.upsert(budget.asEntity())

    override suspend fun deactivate(id: String) = dao.deactivate(id)
}
