package pt.vcc.financeapp.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import pt.vcc.financeapp.core.database.entity.TransactionRuleEntity

@Dao
interface TransactionRuleDao {
    @Query("SELECT * FROM transaction_rules ORDER BY priority, name")
    fun observeAll(): Flow<List<TransactionRuleEntity>>

    /** Regras aplicáveis a uma conta (inclui as globais), pela ordem de avaliação. */
    @Query(
        """
        SELECT * FROM transaction_rules
        WHERE isActive = 1 AND (accountId IS NULL OR accountId = :accountId)
        ORDER BY priority, name
        """,
    )
    suspend fun getActiveForAccount(accountId: String): List<TransactionRuleEntity>

    @Upsert
    suspend fun upsert(rule: TransactionRuleEntity)

    @Delete
    suspend fun delete(rule: TransactionRuleEntity)
}
