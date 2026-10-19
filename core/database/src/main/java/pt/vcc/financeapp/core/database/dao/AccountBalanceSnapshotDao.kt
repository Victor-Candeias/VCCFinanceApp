package pt.vcc.financeapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pt.vcc.financeapp.core.database.entity.AccountBalanceSnapshotEntity

@Dao
interface AccountBalanceSnapshotDao {
    @Query("SELECT * FROM account_balance_snapshots WHERE accountId = :accountId ORDER BY referenceDate DESC")
    fun observeByAccount(accountId: String): Flow<List<AccountBalanceSnapshotEntity>>

    @Query(
        """
        SELECT * FROM account_balance_snapshots
        WHERE accountId = :accountId
        ORDER BY referenceDate DESC, createdAt DESC
        LIMIT 1
        """,
    )
    fun observeLatest(accountId: String): Flow<AccountBalanceSnapshotEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(snapshot: AccountBalanceSnapshotEntity)
}
