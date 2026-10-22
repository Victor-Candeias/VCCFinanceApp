package pt.vcc.financeapp.core.data.repository

import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.vcc.financeapp.core.data.mapper.asDomain
import pt.vcc.financeapp.core.data.mapper.asEntity
import pt.vcc.financeapp.core.database.dao.TransactionDao
import pt.vcc.financeapp.core.database.entity.TransactionEntity
import pt.vcc.financeapp.core.model.BalanceMovement
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.Transaction

internal class LocalTransactionRepository @Inject constructor(
    private val dao: TransactionDao,
) : TransactionRepository {
    override fun observeByAccount(accountId: String): Flow<List<Transaction>> =
        dao.observeByAccount(accountId).map { it.map(TransactionEntity::asDomain) }

    override fun observeInPeriod(fromInclusive: Instant, toExclusive: Instant): Flow<List<Transaction>> =
        dao.observeInPeriod(fromInclusive.toEpochMilli(), toExclusive.toEpochMilli())
            .map { it.map(TransactionEntity::asDomain) }

    override fun observeBalanceMovements(): Flow<List<BalanceMovement>> =
        dao.observeBalanceMovements().map { rows ->
            rows.map { BalanceMovement(it.accountId, it.type, it.status, Money(it.amountMinor, it.currency)) }
        }

    override suspend fun getById(id: String): Transaction? = dao.getById(id)?.asDomain()

    override suspend fun countByAccount(accountId: String): Int = dao.countByAccount(accountId)

    override suspend fun getByAccountInPeriod(accountId: String, fromInclusive: Instant, toExclusive: Instant): List<Transaction> =
        dao.getByAccountInPeriod(accountId, fromInclusive.toEpochMilli(), toExclusive.toEpochMilli()).map(TransactionEntity::asDomain)

    override suspend fun create(transaction: Transaction) = dao.insert(transaction.asEntity())

    override suspend fun update(transaction: Transaction) = dao.update(transaction.asEntity())

    override suspend fun delete(transaction: Transaction) = dao.delete(transaction.asEntity())

    override suspend fun getTransferLegs(transferGroupId: String): List<Transaction> =
        dao.getByTransferGroup(transferGroupId).map(TransactionEntity::asDomain)

    override suspend fun createTransfer(legs: List<Transaction>) = dao.insertAtomically(legs.map(Transaction::asEntity))

    override suspend fun updateTransfer(legs: List<Transaction>) = dao.updateAtomically(legs.map(Transaction::asEntity))

    override suspend fun deleteTransfer(transferGroupId: String) = dao.deleteTransferGroup(transferGroupId)

    override suspend fun insertIgnoringDuplicates(transactions: List<Transaction>): Int {
        if (transactions.isEmpty()) return 0
        return dao.insertAll(transactions.map(Transaction::asEntity)).count { it != -1L }
    }
}
