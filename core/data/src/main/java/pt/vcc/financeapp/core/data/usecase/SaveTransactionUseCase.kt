package pt.vcc.financeapp.core.data.usecase

import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import pt.vcc.financeapp.core.data.repository.AccountRepository
import pt.vcc.financeapp.core.data.repository.CategoryRepository
import pt.vcc.financeapp.core.data.repository.TransactionRepository
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.Transaction
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.model.TransactionStatus
import pt.vcc.financeapp.core.model.TransactionType

/** Receita ou despesa introduzida no formulário; [id] nulo cria um movimento manual novo. */
data class TransactionInput(
    val id: String? = null,
    val accountId: String,
    val type: TransactionType,
    val amountMinor: Long,
    val description: String,
    val bookingDate: LocalDate,
    val categoryId: String? = null,
    val status: TransactionStatus = TransactionStatus.BOOKED,
    val notes: String? = null,
)

enum class TransactionValidationError {
    ACCOUNT_NOT_FOUND,
    ACCOUNT_INACTIVE,
    UNSUPPORTED_TYPE,
    AMOUNT_NOT_POSITIVE,
    BLANK_DESCRIPTION,
    DESCRIPTION_TOO_LONG,
    CATEGORY_MISMATCH,
    TRANSFER_LEG,
    /** Despesa gerada por uma compra: altera-se em Compras (S8-02). */
    PURCHASE_LINKED,
    NOT_FOUND,
}

sealed interface SaveTransactionResult {
    data class Saved(val transaction: Transaction) : SaveTransactionResult
    data class Invalid(val errors: Set<TransactionValidationError>) : SaveTransactionResult
}

/**
 * Cria ou atualiza receitas e despesas. Transferências têm um caso de uso próprio, porque as duas
 * pernas têm de mudar em conjunto.
 */
class SaveTransactionUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(input: TransactionInput): SaveTransactionResult {
        val errors = mutableSetOf<TransactionValidationError>()
        val description = input.description.trim()

        val existing = input.id?.let { transactionRepository.getById(it) }
        if (input.id != null && existing == null) errors += TransactionValidationError.NOT_FOUND
        if (existing?.transferGroupId != null) errors += TransactionValidationError.TRANSFER_LEG
        if (existing?.source == TransactionSource.PURCHASE) errors += TransactionValidationError.PURCHASE_LINKED

        val account = accountRepository.getById(input.accountId)
        if (account == null) {
            errors += TransactionValidationError.ACCOUNT_NOT_FOUND
        } else if (!account.isActive && existing?.accountId != account.id) {
            // Movimentos antigos de contas desativadas continuam editáveis; novos não.
            errors += TransactionValidationError.ACCOUNT_INACTIVE
        }
        if (input.type == TransactionType.TRANSFER) errors += TransactionValidationError.UNSUPPORTED_TYPE
        if (input.amountMinor <= 0) errors += TransactionValidationError.AMOUNT_NOT_POSITIVE
        if (description.isEmpty()) errors += TransactionValidationError.BLANK_DESCRIPTION
        if (description.length > MAX_DESCRIPTION_LENGTH) errors += TransactionValidationError.DESCRIPTION_TOO_LONG
        if (input.categoryId != null) {
            val category = categoryRepository.getById(input.categoryId)
            if (category == null || category.type != input.type) errors += TransactionValidationError.CATEGORY_MISMATCH
        }
        if (errors.isNotEmpty() || account == null) return SaveTransactionResult.Invalid(errors)

        val now = clock.instant()
        val bookingDate = if (existing != null && existing.bookingDate.atZone(clock.zone).toLocalDate() == input.bookingDate) {
            existing.bookingDate
        } else {
            input.bookingDate.atTime(BOOKING_TIME).atZone(clock.zone).toInstant()
        }
        val transaction = Transaction(
            id = existing?.id ?: UUID.randomUUID().toString(),
            accountId = account.id,
            type = input.type,
            amount = Money(input.amountMinor, account.currency),
            description = description,
            bookingDate = bookingDate,
            source = existing?.source ?: TransactionSource.MANUAL,
            status = input.status,
            valueDate = existing?.valueDate,
            categoryId = input.categoryId,
            merchantId = existing?.merchantId,
            externalId = existing?.externalId,
            importBatchId = existing?.importBatchId,
            notes = input.notes?.trim()?.takeIf(String::isNotEmpty),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        if (existing == null) transactionRepository.create(transaction) else transactionRepository.update(transaction)
        return SaveTransactionResult.Saved(transaction)
    }

    companion object {
        const val MAX_DESCRIPTION_LENGTH = 140

        /** Meio-dia local: a data mantém-se mesmo que o fuso do telemóvel mude umas horas. */
        private val BOOKING_TIME: LocalTime = LocalTime.NOON
    }
}
