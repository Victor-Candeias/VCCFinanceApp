package pt.vcc.financeapp.core.data.repository

import kotlinx.coroutines.flow.Flow
import pt.vcc.financeapp.core.model.Account

interface AccountRepository {
    fun observeActiveAccounts(): Flow<List<Account>>
    fun observeAllAccounts(): Flow<List<Account>>
    fun observeAccount(id: String): Flow<Account?>
    suspend fun getById(id: String): Account?
    suspend fun create(account: Account)
    suspend fun update(account: Account)

    /** As contas nunca são apagadas, para preservar o histórico de movimentos. */
    suspend fun deactivate(id: String)
    suspend fun reactivate(id: String)
}
