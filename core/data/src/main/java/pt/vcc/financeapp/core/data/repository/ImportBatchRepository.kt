package pt.vcc.financeapp.core.data.repository

import kotlinx.coroutines.flow.Flow
import pt.vcc.financeapp.core.model.ImportBatch
import pt.vcc.financeapp.core.model.Transaction

interface ImportBatchRepository {
    fun observeAll(): Flow<List<ImportBatch>>

    /** Importações concluídas do mesmo ficheiro (mesmo SHA-256) para a conta, da mais recente para a mais antiga. */
    suspend fun findCompletedByFile(accountId: String, fileSha256: String): List<ImportBatch>

    /**
     * Grava o lote e os movimentos numa única transação (tudo ou nada); movimentos com
     * `(accountId, source, externalId)` repetido são ignorados. Devolve o número de movimentos inseridos.
     */
    suspend fun saveWithTransactions(batch: ImportBatch, transactions: List<Transaction>): Int
}
