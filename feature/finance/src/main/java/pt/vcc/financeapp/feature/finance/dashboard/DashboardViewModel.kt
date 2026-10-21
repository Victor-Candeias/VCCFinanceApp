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
import pt.vcc.financeapp.core.data.repository.AccountRepository
import pt.vcc.financeapp.core.data.repository.CategoryRepository
import pt.vcc.financeapp.core.data.repository.TransactionRepository
import pt.vcc.financeapp.core.model.PeriodTotals
import pt.vcc.financeapp.feature.finance.transactions.TransactionItem
import pt.vcc.financeapp.feature.finance.transactions.toItems
import pt.vcc.financeapp.feature.finance.ui.toInstantRange

data class DashboardUiState(
    val month: YearMonth,
    val isLoading: Boolean = true,
    val activeAccountCount: Int = 0,
    val monthTotals: List<PeriodTotals> = emptyList(),
    val recentTransactions: List<TransactionItem> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    clock: Clock,
) : ViewModel() {
    private val month = YearMonth.now(clock)

    val uiState: StateFlow<DashboardUiState> = run {
        val (from, to) = month.toInstantRange(clock.zone)
        combine(
            accountRepository.observeAllAccounts(),
            transactionRepository.observeInPeriod(from, to),
            categoryRepository.observeAllCategories(),
        ) { accounts, transactions, categories ->
            DashboardUiState(
                month = month,
                isLoading = false,
                activeAccountCount = accounts.count { it.isActive },
                monthTotals = PeriodTotals.byCurrency(transactions),
                recentTransactions = transactions.take(RECENT_COUNT).toItems(accounts, categories),
            )
        }
            .catch { emit(DashboardUiState(month = month, isLoading = false, error = it.message)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState(month))
    }

    private companion object {
        const val RECENT_COUNT = 5
    }
}
