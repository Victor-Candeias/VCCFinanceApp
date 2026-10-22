package pt.vcc.financeapp.core.data.usecase

import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import pt.vcc.financeapp.core.data.repository.BudgetRepository
import pt.vcc.financeapp.core.data.repository.CategoryRepository
import pt.vcc.financeapp.core.data.repository.TransactionRepository
import pt.vcc.financeapp.core.model.Budget
import pt.vcc.financeapp.core.model.BudgetPeriod
import pt.vcc.financeapp.core.model.BudgetPeriods
import pt.vcc.financeapp.core.model.BudgetProgress
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.TransactionType

/** Progresso dos orçamentos ativos no período atual de cada um, pela ordem do repositório (nome). */
class ObserveBudgetProgressUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val clock: Clock,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<List<BudgetProgress>> =
        budgetRepository.observeActiveBudgets().flatMapLatest { budgets ->
            if (budgets.isEmpty()) return@flatMapLatest flowOf(emptyList())
            val now = clock.instant()
            val ranges = budgets.map { BudgetPeriods.range(it.period, now, clock.zone) }
            combine(
                transactionRepository.observeInPeriod(ranges.minOf { it.first }, ranges.maxOf { it.second }),
                categoryRepository.observeAllCategories(),
            ) { transactions, categories ->
                budgets.mapNotNull { BudgetProgress.calculate(it, transactions, categories, now, clock.zone) }
            }
        }
}

/** Dados do formulário de orçamento; [id] nulo cria um orçamento novo e [categoryId] nulo abrange todas as despesas. */
data class BudgetInput(
    val id: String? = null,
    val name: String,
    val categoryId: String?,
    val limitMinor: Long,
    val currency: String,
    val period: BudgetPeriod,
    val alertThresholdPercent: Int,
)

enum class BudgetValidationError { BLANK_NAME, NAME_TOO_LONG, INVALID_AMOUNT, INVALID_CURRENCY, INVALID_THRESHOLD, INVALID_CATEGORY, NOT_FOUND }

sealed interface SaveBudgetResult {
    data class Saved(val budget: Budget) : SaveBudgetResult
    data class Invalid(val errors: Set<BudgetValidationError>) : SaveBudgetResult
}

class SaveBudgetUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(input: BudgetInput): SaveBudgetResult {
        val name = input.name.trim()
        val currency = input.currency.trim().uppercase()
        val errors = mutableSetOf<BudgetValidationError>()
        if (name.isEmpty()) errors += BudgetValidationError.BLANK_NAME
        if (name.length > MAX_NAME_LENGTH) errors += BudgetValidationError.NAME_TOO_LONG
        if (input.limitMinor <= 0) errors += BudgetValidationError.INVALID_AMOUNT
        if (!CURRENCY_CODE.matches(currency)) errors += BudgetValidationError.INVALID_CURRENCY
        if (input.alertThresholdPercent !in 1..100) errors += BudgetValidationError.INVALID_THRESHOLD
        if (input.categoryId != null && categoryRepository.getById(input.categoryId)?.type != TransactionType.EXPENSE) {
            errors += BudgetValidationError.INVALID_CATEGORY
        }
        val existing = input.id?.let { budgetRepository.getById(it) }
        if (input.id != null && existing == null) errors += BudgetValidationError.NOT_FOUND
        if (errors.isNotEmpty()) return SaveBudgetResult.Invalid(errors)

        val now = clock.instant()
        val budget = Budget(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name,
            limit = Money(input.limitMinor, currency),
            period = input.period,
            startDate = existing?.startDate ?: now,
            endDate = existing?.endDate,
            categoryId = input.categoryId,
            alertThresholdPercent = input.alertThresholdPercent,
            isActive = existing?.isActive ?: true,
            createdAt = existing?.createdAt ?: now,
        )
        budgetRepository.save(budget)
        return SaveBudgetResult.Saved(budget)
    }

    companion object {
        const val MAX_NAME_LENGTH = 60
        const val DEFAULT_ALERT_THRESHOLD = 80
        private val CURRENCY_CODE = Regex("[A-Z]{3}")
    }
}
