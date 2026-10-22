package pt.vcc.financeapp.core.data.repository

import kotlinx.coroutines.flow.Flow
import pt.vcc.financeapp.core.model.AccountBalanceSnapshot
import pt.vcc.financeapp.core.model.ImportBatch
import pt.vcc.financeapp.core.model.Transaction

interface ImportBatchRepository {
    fun observeAll(): Flow<List<ImportBatch>>

    /** Importações concluídas do mesmo ficheiro (mesmo SHA-256) para a conta, da mais recente para a mais antiga. */
    suspend fun findCompletedByFile(accountId: String, fileSha256: String): List<ImportBatch>

    /**
     * Grava o lote, os movimentos e o saldo final do extrato numa única transação (tudo ou nada); movimentos
     * com `(accountId, source, externalId)` repetido são ignorados. Devolve o número de movimentos inseridos.
     */
    suspend fun saveWithTransactions(batch: ImportBatch, transactions: List<Transaction>, snapshot: AccountBalanceSnapshot? = null): Int

    /**
     * Apaga os movimentos e o saldo criados pela importação e marca-a como revertida, numa transação.
     * Devolve o número de movimentos apagados, ou `null` se não existir ou já estiver revertida.
     */
    suspend fun revert(batchId: String): Int?
}
