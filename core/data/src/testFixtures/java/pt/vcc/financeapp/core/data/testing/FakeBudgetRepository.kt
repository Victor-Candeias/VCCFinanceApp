package pt.vcc.financeapp.core.data.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import pt.vcc.financeapp.core.data.repository.BudgetRepository
import pt.vcc.financeapp.core.model.Budget

class FakeBudgetRepository(initial: List<Budget> = emptyList()) : BudgetRepository {
    val budgets = MutableStateFlow(initial)

    override fun observeActiveBudgets(): Flow<List<Budget>> =
        budgets.map { list -> list.filter { it.isActive }.sortedBy { it.name } }
    override suspend fun getById(id: String): Budget? = budgets.value.find { it.id == id }
    override suspend fun save(budget: Budget) = budgets.update { list -> list.filterNot { it.id == budget.id } + budget }
    override suspend fun deactivate(id: String) =
        budgets.update { list -> list.map { if (it.id == id) it.copy(isActive = false) else it } }
}
