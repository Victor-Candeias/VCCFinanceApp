package pt.vcc.financeapp.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import pt.vcc.financeapp.core.database.entity.FinancialConnectionEntity
import pt.vcc.financeapp.core.model.ConnectionStatus

@Dao
interface FinancialConnectionDao {
    @Query("SELECT * FROM financial_connections ORDER BY institutionName")
    fun observeAll(): Flow<List<FinancialConnectionEntity>>

    @Query("SELECT * FROM financial_connections WHERE id = :id")
    suspend fun getById(id: String): FinancialConnectionEntity?

    @Upsert
    suspend fun upsert(connection: FinancialConnectionEntity)

    @Query("UPDATE financial_connections SET status = :status, lastErrorCode = :errorCode WHERE id = :id")
    suspend fun updateStatus(id: String, status: ConnectionStatus, errorCode: String?)

    @Delete
    suspend fun delete(connection: FinancialConnectionEntity)
}
