package pt.vcc.financeapp.core.data.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import pt.vcc.financeapp.core.data.repository.AccountRepository
import pt.vcc.financeapp.core.data.repository.TransactionRepository
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AccountBalance
import pt.vcc.financeapp.core.model.AccountBalanceCalculator

/** Conta com o saldo calculado. */
data class AccountWithBalance(val account: Account, val balance: AccountBalance)

/** Todas as contas (ativas e inativas) com o saldo atualizado sempre que há movimentos. */
class ObserveAccountBalancesUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
) {
    operator fun invoke(): Flow<List<AccountWithBalance>> =
        combine(
            accountRepository.observeAllAccounts(),
            transactionRepository.observeBalanceMovements(),
        ) { accounts, movements ->
            val balances = AccountBalanceCalculator.calculateAll(accounts, movements)
            accounts.map { AccountWithBalance(it, balances.getValue(it.id)) }
        }
}
