package pt.vcc.financeapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pt.vcc.financeapp.core.database.entity.AccountBalanceSnapshotEntity
import pt.vcc.financeapp.core.database.entity.ImportBatchEntity
import pt.vcc.financeapp.core.database.entity.TransactionEntity

@Dao
interface ImportBatchDao {
    @Query("SELECT * FROM import_batches ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<ImportBatchEntity>>

    @Query("SELECT * FROM import_batches WHERE accountId = :accountId ORDER BY importedAt DESC")
    fun observeByAccount(accountId: String): Flow<List<ImportBatchEntity>>

    @Query("SELECT * FROM import_batches WHERE id = :id")
    suspend fun getById(id: String): ImportBatchEntity?

    @Query(
        """
        SELECT * FROM import_batches
        WHERE accountId = :accountId AND fileSha256 = :fileSha256 AND status = 'COMPLETED'
        ORDER BY importedAt DESC
        """,
    )
    suspend fun findCompletedByFile(accountId: String, fileSha256: String): List<ImportBatchEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(batch: ImportBatchEntity)

    @Update
    suspend fun update(batch: ImportBatchEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSnapshot(snapshot: AccountBalanceSnapshotEntity)

    @Query("UPDATE import_batches SET status = 'REVERTED' WHERE id = :batchId AND status = 'COMPLETED'")
    suspend fun markReverted(batchId: String): Int

    @Query("DELETE FROM transactions WHERE importBatchId = :batchId")
    suspend fun deleteTransactionsOfBatch(batchId: String): Int

    @Query("DELETE FROM account_balance_snapshots WHERE importBatchId = :batchId")
    suspend fun deleteSnapshotsOfBatch(batchId: String)

    /**
     * Grava o lote, os seus movimentos e o saldo final do extrato numa única transação (tudo ou nada).
     * Movimentos com `(accountId, source, externalId)` já existente são ignorados e contados como
     * duplicados. Devolve o número de movimentos inseridos.
     */
    @Transaction
    suspend fun insertWithTransactions(
        batch: ImportBatchEntity,
        transactions: List<TransactionEntity>,
        snapshot: AccountBalanceSnapshotEntity? = null,
    ): Int {
        insert(batch)
        snapshot?.let { insertSnapshot(it) }
        val inserted = if (transactions.isEmpty()) 0 else insertTransactions(transactions).count { it != -1L }
        val ignored = transactions.size - inserted
        if (ignored > 0) update(batch.copy(importedRows = inserted, duplicateRows = batch.duplicateRows + ignored))
        return inserted
    }

    /**
     * Reverte uma importação concluída: apaga os movimentos e o saldo do extrato que criou (com os
     * respetivos anexos) e marca o lote como `REVERTED`, tudo numa transação. Devolve o número de
     * movimentos apagados, ou `null` se o lote não existir ou já estiver revertido.
     */
    @Transaction
    suspend fun revert(batchId: String): Int? {
        if (markReverted(batchId) == 0) return null
        deleteSnapshotsOfBatch(batchId)
        return deleteTransactionsOfBatch(batchId)
    }
}
