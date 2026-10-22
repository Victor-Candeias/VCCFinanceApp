package pt.vcc.financeapp.core.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.vcc.financeapp.core.data.mapper.asDomain
import pt.vcc.financeapp.core.data.mapper.asEntity
import pt.vcc.financeapp.core.database.dao.ImportBatchDao
import pt.vcc.financeapp.core.database.entity.ImportBatchEntity
import pt.vcc.financeapp.core.model.AccountBalanceSnapshot
import pt.vcc.financeapp.core.model.ImportBatch
import pt.vcc.financeapp.core.model.Transaction

internal class LocalImportBatchRepository @Inject constructor(
    private val dao: ImportBatchDao,
) : ImportBatchRepository {
    override fun observeAll(): Flow<List<ImportBatch>> = dao.observeAll().map { it.map(ImportBatchEntity::asDomain) }

    override suspend fun findCompletedByFile(accountId: String, fileSha256: String): List<ImportBatch> =
        dao.findCompletedByFile(accountId, fileSha256).map(ImportBatchEntity::asDomain)

    override suspend fun saveWithTransactions(batch: ImportBatch, transactions: List<Transaction>, snapshot: AccountBalanceSnapshot?): Int =
        dao.insertWithTransactions(batch.asEntity(), transactions.map(Transaction::asEntity), snapshot?.asEntity())

    override suspend fun revert(batchId: String): Int? = dao.revert(batchId)
}
