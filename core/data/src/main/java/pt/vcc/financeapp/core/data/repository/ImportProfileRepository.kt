package pt.vcc.financeapp.core.data.repository

import kotlinx.coroutines.flow.Flow
import pt.vcc.financeapp.core.model.ImportProfile

interface ImportProfileRepository {
    fun observeAll(): Flow<List<ImportProfile>>

    /** Perfil mais recente com o mesmo cabeçalho normalizado. */
    suspend fun findBySignature(signature: String): ImportProfile?

    suspend fun save(profile: ImportProfile)

    suspend fun delete(id: String)
}
