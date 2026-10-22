package pt.vcc.financeapp.feature.finance.budgets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.data.repository.BudgetRepository
import pt.vcc.financeapp.core.data.repository.CategoryRepository
import pt.vcc.financeapp.core.data.usecase.BudgetInput
import pt.vcc.financeapp.core.data.usecase.BudgetValidationError
import pt.vcc.financeapp.core.data.usecase.SaveBudgetResult
import pt.vcc.financeapp.core.data.usecase.SaveBudgetUseCase
import pt.vcc.financeapp.core.model.AmountParser
import pt.vcc.financeapp.core.model.BudgetPeriod
import pt.vcc.financeapp.core.model.TransactionType
import pt.vcc.financeapp.core.model.currencyFractionDigits
import pt.vcc.financeapp.feature.finance.transactions.CategoryOption
import pt.vcc.financeapp.feature.finance.transactions.toOptions
import pt.vcc.financeapp.feature.finance.ui.formatAmountInput

data class BudgetEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    /** Categorias de despesa; o orçamento inclui as subcategorias da escolhida. */
    val categories: List<CategoryOption> = emptyList(),
    val categoryId: String? = null,
    val limit: String = "",
    val currency: String = DEFAULT_CURRENCY,
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val alertThreshold: String = SaveBudgetUseCase.DEFAULT_ALERT_THRESHOLD.toString(),
    val errors: Set<BudgetValidationError> = emptySet(),
    val isSaving: Boolean = false,
    val isFinished: Boolean = false,
) {
    companion object {
        const val DEFAULT_CURRENCY = "EUR"
    }
}

@HiltViewModel
class BudgetEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val saveBudget: SaveBudgetUseCase,
) : ViewModel() {
    private val budgetId: String? = savedStateHandle.get<String>(BUDGET_ID_ARG)

    private val _uiState = MutableStateFlow(BudgetEditorUiState(isNew = budgetId == null))
    val uiState: StateFlow<BudgetEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val budget = budgetId?.let { budgetRepository.getById(it) }
            val categories = categoryRepository.observeAllCategories().first()
                .toOptions(keepId = budget?.categoryId)
                .filter { it.type == TransactionType.EXPENSE }
            _uiState.value = when {
                budgetId != null && budget == null ->
                    BudgetEditorUiState(isLoading = false, isNew = false, errors = setOf(BudgetValidationError.NOT_FOUND))
                budget != null -> BudgetEditorUiState(
                    isLoading = false,
                    isNew = false,
                    name = budget.name,
                    categories = categories,
                    categoryId = budget.categoryId,
                    limit = formatAmountInput(budget.limit.amountMinor, currencyFractionDigits(budget.limit.currency)),
                    currency = budget.limit.currency,
                    period = budget.period,
                    alertThreshold = budget.alertThresholdPercent.toString(),
                )
                else -> BudgetEditorUiState(isLoading = false, categories = categories)
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, errors = it.errors - NAME_ERRORS) }

    fun onCategoryChange(value: String?) =
        _uiState.update { it.copy(categoryId = value, errors = it.errors - BudgetValidationError.INVALID_CATEGORY) }

    fun onLimitChange(value: String) = _uiState.update { it.copy(limit = value, errors = it.errors - BudgetValidationError.INVALID_AMOUNT) }

    fun onCurrencyChange(value: String) =
        _uiState.update { it.copy(currency = value.uppercase().take(3), errors = it.errors - BudgetValidationError.INVALID_CURRENCY) }

    fun onPeriodChange(value: BudgetPeriod) = _uiState.update { it.copy(period = value) }

    fun onAlertThresholdChange(value: String) = _uiState.update {
        it.copy(alertThreshold = value.filter(Char::isDigit).take(3), errors = it.errors - BudgetValidationError.INVALID_THRESHOLD)
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading) return
        val currency = state.currency.trim().uppercase()
        val limit = AmountParser.parseOrNull(state.limit, currencyFractionDigits(currency))
        if (limit == null) {
            _uiState.update { it.copy(errors = it.errors + BudgetValidationError.INVALID_AMOUNT) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = saveBudget(
                BudgetInput(
                    id = budgetId,
                    name = state.name,
                    categoryId = state.categoryId,
                    limitMinor = limit,
                    currency = currency,
                    period = state.period,
                    alertThresholdPercent = state.alertThreshold.toIntOrNull() ?: 0,
                ),
            )
            _uiState.update {
                when (result) {
                    is SaveBudgetResult.Saved -> it.copy(isSaving = false, isFinished = true)
                    is SaveBudgetResult.Invalid -> it.copy(isSaving = false, errors = result.errors)
                }
            }
        }
    }

    fun delete() {
        val id = budgetId ?: return
        viewModelScope.launch {
            budgetRepository.deactivate(id)
            _uiState.update { it.copy(isFinished = true) }
        }
    }

    companion object {
        const val BUDGET_ID_ARG = "budgetId"
        private val NAME_ERRORS = setOf(BudgetValidationError.BLANK_NAME, BudgetValidationError.NAME_TOO_LONG)
    }
}
