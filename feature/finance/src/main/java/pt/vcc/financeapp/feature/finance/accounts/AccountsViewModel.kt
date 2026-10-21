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
import pt.vcc.financeapp.core.data.repository.AccountRepository
import pt.vcc.financeapp.core.model.Account

data class AccountsUiState(
    val isLoading: Boolean = true,
    val accounts: List<Account> = emptyList(),
    val inactiveAccounts: List<Account> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    accountRepository: AccountRepository,
) : ViewModel() {
    val uiState: StateFlow<AccountsUiState> = accountRepository.observeAllAccounts()
        .map { all ->
            val (active, inactive) = all.partition(Account::isActive)
            AccountsUiState(isLoading = false, accounts = active, inactiveAccounts = inactive)
        }
        .catch { emit(AccountsUiState(isLoading = false, error = it.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())
}
