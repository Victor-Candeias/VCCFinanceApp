package pt.vcc.financeapp.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import pt.vcc.financeapp.core.database.entity.RecurringTransactionEntity

@Dao
interface RecurringTransactionDao {
    @Query("SELECT * FROM recurring_transactions WHERE isActive = 1 ORDER BY nextOccurrenceAt")
    fun observeActive(): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions WHERE id = :id")
    suspend fun getById(id: String): RecurringTransactionEntity?

    @Query(
        """
        SELECT * FROM recurring_transactions
        WHERE isActive = 1 AND nextOccurrenceAt <= :now
        ORDER BY nextOccurrenceAt
        """,
    )
    suspend fun getDue(now: Long): List<RecurringTransactionEntity>

    @Upsert
    suspend fun upsert(recurring: RecurringTransactionEntity)

    @Query("UPDATE recurring_transactions SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: String)
}
