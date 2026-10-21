package pt.vcc.financeapp.core.model

import java.time.Instant

/** `amount` nunca é negativo: a direção vem de `type` (README §5). */
data class Transaction(
    val id: String,
    val accountId: String,
    val type: TransactionType,
    val amount: Money,
    val description: String,
    val bookingDate: Instant,
    val source: TransactionSource,
    val status: TransactionStatus = TransactionStatus.BOOKED,
    val valueDate: Instant? = null,
    val categoryId: String? = null,
    val merchantId: String? = null,
    val externalId: String? = null,
    val transferGroupId: String? = null,
    val importBatchId: String? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(!amount.isNegative) { "O montante de um movimento não pode ser negativo" }
    }

    /**
     * Perna de uma transferência entre contas próprias: guardada como EXPENSE na origem e INCOME no
     * destino (contam para o saldo de cada conta) mas excluída de receitas/despesas globais.
     * `TRANSFER` fica reservado a movimentos externos sem perna correspondente.
     */
    val isInternalTransfer: Boolean get() = transferGroupId != null || type == TransactionType.TRANSFER
}
