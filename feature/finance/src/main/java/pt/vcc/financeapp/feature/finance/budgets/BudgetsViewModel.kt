package pt.vcc.financeapp.feature.finance.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import pt.vcc.financeapp.core.data.repository.CategoryRepository
import pt.vcc.financeapp.core.data.usecase.ObserveBudgetProgressUseCase
import pt.vcc.financeapp.core.model.BudgetProgress
import pt.vcc.financeapp.core.model.Category

/** Orçamento com o nome da categoria resolvido (`null` = todas as despesas). */
data class BudgetItem(val progress: BudgetProgress, val categoryName: String?)

data class BudgetsUiState(
    val isLoading: Boolean = true,
    val budgets: List<BudgetItem> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    observeBudgetProgress: ObserveBudgetProgressUseCase,
    categoryRepository: CategoryRepository,
) : ViewModel() {
    val uiState: StateFlow<BudgetsUiState> =
        combine(observeBudgetProgress(), categoryRepository.observeAllCategories()) { progress, categories ->
            BudgetsUiState(isLoading = false, budgets = progress.toItems(categories))
        }
            .catch { emit(BudgetsUiState(isLoading = false, error = it.message)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())
}

internal fun List<BudgetProgress>.toItems(categories: List<Category>): List<BudgetItem> {
    val names = categories.associate { it.id to it.name }
    return map { BudgetItem(it, it.budget.categoryId?.let(names::get)) }
}
