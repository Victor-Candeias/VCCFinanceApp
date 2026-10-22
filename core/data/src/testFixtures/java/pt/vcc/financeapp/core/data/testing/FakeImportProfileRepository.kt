package pt.vcc.financeapp.core.data.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import pt.vcc.financeapp.core.data.repository.ImportProfileRepository
import pt.vcc.financeapp.core.model.ImportProfile

class FakeImportProfileRepository(initial: List<ImportProfile> = emptyList()) : ImportProfileRepository {
    val profiles = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<ImportProfile>> = profiles.map { list -> list.sortedBy { it.name.lowercase() } }
    override suspend fun findBySignature(signature: String): ImportProfile? =
        profiles.value.filter { it.headerSignature == signature }.maxByOrNull { it.updatedAt }
    override suspend fun save(profile: ImportProfile) = profiles.update { list -> list.filterNot { it.id == profile.id } + profile }
    override suspend fun delete(id: String) = profiles.update { list -> list.filterNot { it.id == id } }
}
