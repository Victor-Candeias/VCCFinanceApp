package pt.vcc.financeapp.feature.finance.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import pt.vcc.financeapp.core.data.repository.CategoryRepository
import pt.vcc.financeapp.core.data.repository.TransactionRepository
import pt.vcc.financeapp.core.data.usecase.ObserveAccountBalancesUseCase
import pt.vcc.financeapp.core.model.AccountBalanceCalculator
import pt.vcc.financeapp.core.model.Category
import pt.vcc.financeapp.core.model.FinancialAggregations
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.MonthTotals
import pt.vcc.financeapp.core.model.PeriodTotals
import pt.vcc.financeapp.core.model.Transaction
import pt.vcc.financeapp.feature.finance.transactions.TransactionItem
import pt.vcc.financeapp.feature.finance.transactions.toItems
import pt.vcc.financeapp.feature.finance.ui.toInstantRange

/** Fatia do gráfico de despesas: categoria principal, "sem categoria" (`categoryId == null`) ou "outras". */
data class DashboardCategory(
    val categoryId: String?,
    val name: String?,
    val color: String?,
    val total: Money,
    val isOthers: Boolean = false,
)

data class DashboardUiState(
    val month: YearMonth,
    val isLoading: Boolean = true,
    val activeAccountCount: Int = 0,
    /** Saldo das contas ativas, por moeda. */
    val balanceTotals: List<Money> = emptyList(),
    val monthTotals: List<PeriodTotals> = emptyList(),
    val recentTransactions: List<TransactionItem> = emptyList(),
    /** Moeda dos gráficos (sem conversões); `null` se não houver movimentos nos últimos meses. */
    val chartCurrency: String? = null,
    /** Há movimentos noutras moedas que ficam fora dos gráficos. */
    val chartOmitsCurrencies: Boolean = false,
    val expensesByCategory: List<DashboardCategory> = emptyList(),
    val monthlyTotals: List<MonthTotals> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeAccountBalances: ObserveAccountBalancesUseCase,
    transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    clock: Clock,
) : ViewModel() {
    private val month = YearMonth.now(clock)

    val uiState: StateFlow<DashboardUiState> = run {
        val zone = clock.zone
        val months = FinancialAggregations.monthsEndingAt(month, CHART_MONTHS)
        val (from, to) = FinancialAggregations.range(months, zone)
        val (monthFrom, _) = month.toInstantRange(zone)
        combine(
            observeAccountBalances(),
            transactionRepository.observeInPeriod(from, to),
            categoryRepository.observeAllCategories(),
        ) { balances, recent, categories ->
            val accounts = balances.map { it.account }
            val active = balances.filter { it.account.isActive }
            val transactions = recent.filter { it.bookingDate >= monthFrom }
            val currency = FinancialAggregations.mainCurrency(recent)
            DashboardUiState(
                month = month,
                isLoading = false,
                activeAccountCount = active.size,
                balanceTotals = AccountBalanceCalculator.totalsByCurrency(active.map { it.balance }),
                monthTotals = PeriodTotals.byCurrency(transactions),
                recentTransactions = transactions.take(RECENT_COUNT).toItems(accounts, categories),
                chartCurrency = currency,
                chartOmitsCurrencies = currency != null && recent.any { it.amount.currency != currency && !it.isInternalTransfer },
                expensesByCategory = currency?.let { categorySlices(transactions, it, categories) }.orEmpty(),
                monthlyTotals = currency?.let { FinancialAggregations.monthlyTotals(recent, it, months, zone) }.orEmpty(),
            )
        }
            .catch { emit(DashboardUiState(month = month, isLoading = false, error = it.message)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState(month))
    }

    private companion object {
        const val RECENT_COUNT = 5
        const val CHART_MONTHS = 6
        const val MAX_SLICES = 5

        /** Maiores categorias principais do mês; as restantes juntam-se numa fatia "outras". */
        fun categorySlices(transactions: List<Transaction>, currency: String, categories: List<Category>): List<DashboardCategory> {
            val byId = categories.associateBy { it.id }
            val totals = FinancialAggregations.expensesByCategory(transactions, currency) { id ->
                id?.let { byId[it]?.parentCategoryId ?: it }
            }
            val slices = totals.take(MAX_SLICES).map { total ->
                val category = total.categoryId?.let(byId::get)
                DashboardCategory(total.categoryId, category?.name, category?.color, total.total)
            }
            val rest = totals.drop(MAX_SLICES)
            if (rest.isEmpty()) return slices
            val others = rest.fold(Money.zero(currency)) { sum, item -> sum + item.total }
            return slices + DashboardCategory(null, null, null, others, isOthers = true)
        }
    }
}
