package pt.vcc.financeapp.core.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.vcc.financeapp.core.data.mapper.asDomain
import pt.vcc.financeapp.core.data.mapper.asEntity
import pt.vcc.financeapp.core.database.dao.AccountDao
import pt.vcc.financeapp.core.database.entity.AccountEntity
import pt.vcc.financeapp.core.model.Account

internal class LocalAccountRepository @Inject constructor(
    private val dao: AccountDao,
) : AccountRepository {
    override fun observeActiveAccounts(): Flow<List<Account>> =
        dao.observeActiveAccounts().map { it.map(AccountEntity::asDomain) }

    override fun observeAllAccounts(): Flow<List<Account>> =
        dao.observeAll().map { it.map(AccountEntity::asDomain) }

    override fun observeAccount(id: String): Flow<Account?> = dao.observeById(id).map { it?.asDomain() }

    override suspend fun getById(id: String): Account? = dao.getById(id)?.asDomain()

    override suspend fun create(account: Account) = dao.insert(account.asEntity())

    override suspend fun update(account: Account) = dao.update(account.asEntity())

    override suspend fun deactivate(id: String) = dao.deactivate(id)
}
