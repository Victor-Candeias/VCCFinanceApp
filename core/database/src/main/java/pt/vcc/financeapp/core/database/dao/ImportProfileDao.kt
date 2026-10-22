package pt.vcc.financeapp.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import pt.vcc.financeapp.core.database.entity.ImportProfileEntity

@Dao
interface ImportProfileDao {
    @Query("SELECT * FROM import_profiles ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ImportProfileEntity>>

    @Query("SELECT * FROM import_profiles WHERE headerSignature = :signature ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findBySignature(signature: String): ImportProfileEntity?

    @Query("SELECT * FROM import_profiles WHERE id = :id")
    suspend fun getById(id: String): ImportProfileEntity?

    @Upsert
    suspend fun upsert(profile: ImportProfileEntity)

    @Query("DELETE FROM import_profiles WHERE id = :id")
    suspend fun delete(id: String)
}
