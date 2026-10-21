package pt.vcc.financeapp.feature.finance.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import pt.vcc.financeapp.core.data.repository.AccountRepository
import pt.vcc.financeapp.core.data.repository.CategoryRepository
import pt.vcc.financeapp.core.data.repository.TransactionRepository
import pt.vcc.financeapp.core.model.PeriodTotals
import pt.vcc.financeapp.feature.finance.ui.toInstantRange

data class TransactionsUiState(
    val month: YearMonth,
    val isLoading: Boolean = true,
    val items: List<TransactionItem> = emptyList(),
    val totals: List<PeriodTotals> = emptyList(),
    val hasAccounts: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now(clock))

    val uiState: StateFlow<TransactionsUiState> = month
        .flatMapLatest { selected ->
            val (from, to) = selected.toInstantRange(clock.zone)
            combine(
                transactionRepository.observeInPeriod(from, to),
                accountRepository.observeAllAccounts(),
                categoryRepository.observeAllCategories(),
            ) { transactions, accounts, categories ->
                TransactionsUiState(
                    month = selected,
                    isLoading = false,
                    items = transactions.toItems(accounts, categories),
                    totals = PeriodTotals.byCurrency(transactions),
                    hasAccounts = accounts.any { it.isActive },
                )
            }.catch { emit(TransactionsUiState(month = selected, isLoading = false, error = it.message)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState(month.value))

    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update { it.plusMonths(1) }
}
