package pt.vcc.financeapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pt.vcc.financeapp.core.database.entity.SyncHistoryEntity

@Dao
interface SyncHistoryDao {
    @Query("SELECT * FROM sync_history WHERE connectionId = :connectionId ORDER BY startedAt DESC LIMIT :limit")
    fun observeByConnection(connectionId: String, limit: Int): Flow<List<SyncHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: SyncHistoryEntity)

    @Update
    suspend fun update(entry: SyncHistoryEntity)
}
