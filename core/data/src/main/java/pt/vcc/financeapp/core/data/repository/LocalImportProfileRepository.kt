package pt.vcc.financeapp.core.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.vcc.financeapp.core.data.mapper.asDomain
import pt.vcc.financeapp.core.data.mapper.asEntity
import pt.vcc.financeapp.core.database.dao.ImportProfileDao
import pt.vcc.financeapp.core.database.entity.ImportProfileEntity
import pt.vcc.financeapp.core.model.ImportProfile

internal class LocalImportProfileRepository @Inject constructor(
    private val dao: ImportProfileDao,
) : ImportProfileRepository {
    override fun observeAll(): Flow<List<ImportProfile>> = dao.observeAll().map { it.map(ImportProfileEntity::asDomain) }

    override suspend fun findBySignature(signature: String): ImportProfile? = dao.findBySignature(signature)?.asDomain()

    override suspend fun save(profile: ImportProfile) = dao.upsert(profile.asEntity())

    override suspend fun delete(id: String) = dao.delete(id)
}
