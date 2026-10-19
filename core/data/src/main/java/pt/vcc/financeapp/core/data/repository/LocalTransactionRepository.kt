package pt.vcc.financeapp.core.data.repository

import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.vcc.financeapp.core.data.mapper.asDomain
import pt.vcc.financeapp.core.data.mapper.asEntity
import pt.vcc.financeapp.core.database.dao.TransactionDao
import pt.vcc.financeapp.core.database.entity.TransactionEntity
import pt.vcc.financeapp.core.model.Transaction

internal class LocalTransactionRepository @Inject constructor(
    private val dao: TransactionDao,
) : TransactionRepository {
    override fun observeByAccount(accountId: String): Flow<List<Transaction>> =
        dao.observeByAccount(accountId).map { it.map(TransactionEntity::asDomain) }

    override fun observeInPeriod(fromInclusive: Instant, toExclusive: Instant): Flow<List<Transaction>> =
        dao.observeInPeriod(fromInclusive.toEpochMilli(), toExclusive.toEpochMilli())
            .map { it.map(TransactionEntity::asDomain) }

    override suspend fun getById(id: String): Transaction? = dao.getById(id)?.asDomain()

    override suspend fun create(transaction: Transaction) = dao.insert(transaction.asEntity())

    override suspend fun update(transaction: Transaction) = dao.update(transaction.asEntity())

    override suspend fun delete(transaction: Transaction) = dao.delete(transaction.asEntity())

    override suspend fun insertIgnoringDuplicates(transactions: List<Transaction>): Int {
        if (transactions.isEmpty()) return 0
        return dao.insertAll(transactions.map(Transaction::asEntity)).count { it != -1L }
    }
}
