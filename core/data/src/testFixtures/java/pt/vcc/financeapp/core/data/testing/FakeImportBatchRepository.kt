package pt.vcc.financeapp.core.data.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import pt.vcc.financeapp.core.data.repository.ImportBatchRepository
import pt.vcc.financeapp.core.model.AccountBalanceSnapshot
import pt.vcc.financeapp.core.model.ImportBatch
import pt.vcc.financeapp.core.model.ImportStatus
import pt.vcc.financeapp.core.model.Transaction

/** Grava os movimentos no [transactions] partilhado, ignorando `(accountId, source, externalId)` repetidos como o Room. */
class FakeImportBatchRepository(
    private val transactions: FakeTransactionRepository,
    initial: List<ImportBatch> = emptyList(),
) : ImportBatchRepository {
    val batches = MutableStateFlow(initial)
    val snapshots = MutableStateFlow<List<AccountBalanceSnapshot>>(emptyList())

    override fun observeAll(): Flow<List<ImportBatch>> = batches.map { list -> list.sortedByDescending { it.importedAt } }

    override suspend fun findCompletedByFile(accountId: String, fileSha256: String): List<ImportBatch> =
        batches.value
            .filter { it.accountId == accountId && it.fileSha256 == fileSha256 && it.status == ImportStatus.COMPLETED }
            .sortedByDescending { it.importedAt }

    override suspend fun saveWithTransactions(batch: ImportBatch, transactions: List<Transaction>, snapshot: AccountBalanceSnapshot?): Int {
        snapshot?.let { s -> snapshots.update { it + s } }
        val existing = this.transactions.transactions.value
            .mapNotNullTo(mutableSetOf()) { tx -> tx.externalId?.let { Triple(tx.accountId, tx.source, it) } }
        val toInsert = transactions.filter { tx ->
            val externalId = tx.externalId
            externalId == null || existing.add(Triple(tx.accountId, tx.source, externalId))
        }
        this.transactions.transactions.update { it + toInsert }
        val ignored = transactions.size - toInsert.size
        batches.update { it + batch.copy(importedRows = toInsert.size, duplicateRows = batch.duplicateRows + ignored) }
        return toInsert.size
    }

    override suspend fun revert(batchId: String): Int? {
        val batch = batches.value.find { it.id == batchId }?.takeIf { it.status == ImportStatus.COMPLETED } ?: return null
        batches.update { list -> list.map { if (it.id == batchId) batch.copy(status = ImportStatus.REVERTED) else it } }
        snapshots.update { list -> list.filterNot { it.importBatchId == batchId } }
        val deleted = transactions.transactions.value.count { it.importBatchId == batchId }
        transactions.transactions.update { list -> list.filterNot { it.importBatchId == batchId } }
        return deleted
    }
}
