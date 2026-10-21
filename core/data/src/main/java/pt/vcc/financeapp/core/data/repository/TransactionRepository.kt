package pt.vcc.financeapp.core.data.repository

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import pt.vcc.financeapp.core.model.Transaction

interface TransactionRepository {
    fun observeByAccount(accountId: String): Flow<List<Transaction>>

    /** Movimentos com `bookingDate` em `[fromInclusive, toExclusive)`. */
    fun observeInPeriod(fromInclusive: Instant, toExclusive: Instant): Flow<List<Transaction>>

    suspend fun getById(id: String): Transaction?
    suspend fun countByAccount(accountId: String): Int
    suspend fun create(transaction: Transaction)
    suspend fun update(transaction: Transaction)
    suspend fun delete(transaction: Transaction)

    /** Pernas de uma transferência interna. */
    suspend fun getTransferLegs(transferGroupId: String): List<Transaction>

    /** Insere ou atualiza todas as pernas numa única transação (tudo ou nada). */
    suspend fun createTransfer(legs: List<Transaction>)
    suspend fun updateTransfer(legs: List<Transaction>)
    suspend fun deleteTransfer(transferGroupId: String)

    /**
     * Insere movimentos importados/sincronizados, ignorando os que repetem
     * `(accountId, source, externalId)`. Devolve o número de movimentos efetivamente inseridos.
     */
    suspend fun insertIgnoringDuplicates(transactions: List<Transaction>): Int
}
