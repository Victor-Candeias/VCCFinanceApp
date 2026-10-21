package pt.vcc.financeapp.feature.finance.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pt.vcc.financeapp.core.data.usecase.AccountWithBalance
import pt.vcc.financeapp.core.data.usecase.ObserveAccountBalancesUseCase
import pt.vcc.financeapp.core.model.AccountBalanceCalculator
import pt.vcc.financeapp.core.model.Money

data class AccountsUiState(
    val isLoading: Boolean = true,
    val accounts: List<AccountWithBalance> = emptyList(),
    val inactiveAccounts: List<AccountWithBalance> = emptyList(),
    /** Soma dos saldos das contas ativas, por moeda. */
    val totals: List<Money> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    observeAccountBalances: ObserveAccountBalancesUseCase,
) : ViewModel() {
    val uiState: StateFlow<AccountsUiState> = observeAccountBalances()
        .map { all ->
            val (active, inactive) = all.partition { it.account.isActive }
            AccountsUiState(
                isLoading = false,
                accounts = active,
                inactiveAccounts = inactive,
                totals = AccountBalanceCalculator.totalsByCurrency(active.map { it.balance }),
            )
        }
        .catch { emit(AccountsUiState(isLoading = false, error = it.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())
}