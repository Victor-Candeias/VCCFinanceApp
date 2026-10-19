package pt.vcc.financeapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pt.vcc.financeapp.core.database.entity.ImportBatchEntity

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
}
