package pt.vcc.financeapp.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pt.vcc.financeapp.core.database.entity.TransactionEntity

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY bookingDate DESC, id DESC")
    fun observeByAccount(accountId: String): Flow<List<TransactionEntity>>

    /** Intervalo semiaberto `[fromInclusive, toExclusive)` em epoch millis. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE bookingDate >= :fromInclusive AND bookingDate < :toExclusive
        ORDER BY bookingDate DESC, id DESC
        """,
    )
    fun observeInPeriod(fromInclusive: Long, toExclusive: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE transferGroupId = :transferGroupId")
    suspend fun getByTransferGroup(transferGroupId: String): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId")
    suspend fun countByAccount(accountId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity)

    /** Ignora movimentos com `(accountId, source, externalId)` repetido; devolve -1 nas linhas ignoradas. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    /** Tudo ou nada: o Room insere a lista numa única transação e reverte se uma linha falhar. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAtomically(transactions: List<TransactionEntity>)

    @Update
    suspend fun update(transaction: TransactionEntity)

    /** Atualiza a lista numa única transação. */
    @Update
    suspend fun updateAtomically(transactions: List<TransactionEntity>)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE transferGroupId = :transferGroupId")
    suspend fun deleteTransferGroup(transferGroupId: String)

    /**
     * Soma dos movimentos confirmados (receitas menos despesas, incluindo pernas de transferência).
     * Não é o saldo final: falta o saldo inicial da conta (ver `AccountBalanceCalculator`).
     */
    @Query(
        """
        SELECT COALESCE(SUM(CASE
            WHEN type = 'INCOME' THEN amountMinor
            WHEN type = 'EXPENSE' THEN -amountMinor
            ELSE 0 END), 0)
        FROM transactions
        WHERE accountId = :accountId AND status = 'BOOKED'
        """,
    )
    fun observeNetMovement(accountId: String): Flow<Long>
}
