package pt.vcc.financeapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import pt.vcc.financeapp.core.database.entity.MerchantEntity

@Dao
interface MerchantDao {
    @Query("SELECT * FROM merchants WHERE isActive = 1 ORDER BY name")
    fun observeActive(): Flow<List<MerchantEntity>>

    @Query("SELECT * FROM merchants WHERE id = :id")
    suspend fun getById(id: String): MerchantEntity?

    @Query("SELECT * FROM merchants WHERE normalizedName = :normalizedName")
    suspend fun getByNormalizedName(normalizedName: String): MerchantEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(merchant: MerchantEntity)

    @Update
    suspend fun update(merchant: MerchantEntity)
}
