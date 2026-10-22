package pt.vcc.financeapp.core.data.repository

import kotlinx.coroutines.flow.Flow
import pt.vcc.financeapp.core.model.Budget

interface BudgetRepository {
    fun observeActiveBudgets(): Flow<List<Budget>>
    suspend fun getById(id: String): Budget?
    suspend fun save(budget: Budget)

    /** Remove o orçamento das listas e alertas; o histórico de movimentos não é afetado. */
    suspend fun deactivate(id: String)
}
