package pt.vcc.financeapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pt.vcc.financeapp.core.database.entity.AccountEntity

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY name")
    fun observeActiveAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY isActive DESC, name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeById(id: String): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity)

    @Update
    suspend fun update(account: AccountEntity)

    /** Desativar retira também a marca de conta por omissão. */
    @Query("UPDATE accounts SET isActive = 0, isDefault = 0 WHERE id = :id")
    suspend fun deactivate(id: String)

    @Query("UPDATE accounts SET isActive = 1 WHERE id = :id")
    suspend fun reactivate(id: String)

    /** Marca [id] como conta por omissão e desmarca todas as outras, numa só instrução. */
    @Query("UPDATE accounts SET isDefault = CASE WHEN id = :id THEN 1 ELSE 0 END WHERE isDefault = 1 OR id = :id")
    suspend fun setDefault(id: String)

    @Query("UPDATE accounts SET isDefault = 0 WHERE id = :id")
    suspend fun clearDefault(id: String)
}
