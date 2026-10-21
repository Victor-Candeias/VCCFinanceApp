package pt.vcc.financeapp.core.data.usecase

import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import pt.vcc.financeapp.core.data.repository.AccountRepository
import pt.vcc.financeapp.core.data.repository.TransactionRepository
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.Transaction
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.model.TransactionStatus
import pt.vcc.financeapp.core.model.TransactionType

/** Transferência entre contas próprias; [transferGroupId] nulo cria uma nova. */
data class TransferInput(
    val transferGroupId: String? = null,
    val fromAccountId: String,
    val toAccountId: String,
    val amountMinor: Long,
    val description: String,
    val bookingDate: LocalDate,
    val status: TransactionStatus = TransactionStatus.BOOKED,
    val notes: String? = null,
)

enum class TransferValidationError {
    FROM_ACCOUNT_NOT_FOUND,
    TO_ACCOUNT_NOT_FOUND,
    ACCOUNT_INACTIVE,
    SAME_ACCOUNT,
    CURRENCY_MISMATCH,
    AMOUNT_NOT_POSITIVE,
    BLANK_DESCRIPTION,
    DESCRIPTION_TOO_LONG,
    NOT_FOUND,
}

/** As duas pernas da transferência: saída (EXPENSE) e entrada (INCOME). */
data class TransferLegs(val outgoing: Transaction, val incoming: Transaction) {
    val transferGroupId: String get() = outgoing.transferGroupId!!

    companion object {
        /** Reconstrói as pernas guardadas; `null` se o grupo não tiver exatamente uma saída e uma entrada. */
        fun from(legs: List<Transaction>): TransferLegs? {
            if (legs.size != 2) return null
            val outgoing = legs.singleOrNull { it.type == TransactionType.EXPENSE } ?: return null
            val incoming = legs.singleOrNull { it.type == TransactionType.INCOME } ?: return null
            if (outgoing.transferGroupId == null || outgoing.transferGroupId != incoming.transferGroupId) return null
            return TransferLegs(outgoing, incoming)
        }
    }
}

sealed interface SaveTransferResult {
    data class Saved(val legs: TransferLegs) : SaveTransferResult
    data class Invalid(val errors: Set<TransferValidationError>) : SaveTransferResult
}

/**
 * Cria ou altera uma transferência interna, gravando as duas pernas numa única transação para que
 * nunca exista uma sem a outra. Só são suportadas transferências na mesma moeda.
 */
class SaveTransferUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(input: TransferInput): SaveTransferResult {
        val errors = mutableSetOf<TransferValidationError>()
        val description = input.description.trim()

        val existing = input.transferGroupId?.let { TransferLegs.from(transactionRepository.getTransferLegs(it)) }
        if (input.transferGroupId != null && existing == null) errors += TransferValidationError.NOT_FOUND

        val from = accountRepository.getById(input.fromAccountId)
        val to = accountRepository.getById(input.toAccountId)
        if (from == null) errors += TransferValidationError.FROM_ACCOUNT_NOT_FOUND
        if (to == null) errors += TransferValidationError.TO_ACCOUNT_NOT_FOUND
        val previousAccounts = setOfNotNull(existing?.outgoing?.accountId, existing?.incoming?.accountId)
        if (listOfNotNull(from, to).any { !it.isActive && it.id !in previousAccounts }) {
            errors += TransferValidationError.ACCOUNT_INACTIVE
        }
        if (input.fromAccountId == input.toAccountId) errors += TransferValidationError.SAME_ACCOUNT
        if (from != null && to != null && from.currency != to.currency) errors += TransferValidationError.CURRENCY_MISMATCH
        if (input.amountMinor <= 0) errors += TransferValidationError.AMOUNT_NOT_POSITIVE
        if (description.isEmpty()) errors += TransferValidationError.BLANK_DESCRIPTION
        if (description.length > SaveTransactionUseCase.MAX_DESCRIPTION_LENGTH) errors += TransferValidationError.DESCRIPTION_TOO_LONG
        if (errors.isNotEmpty() || from == null || to == null) return SaveTransferResult.Invalid(errors)

        val now = clock.instant()
        val bookingDate = existing?.outgoing?.bookingDate
            ?.takeIf { it.atZone(clock.zone).toLocalDate() == input.bookingDate }
            ?: input.bookingDate.atTime(LocalTime.NOON).atZone(clock.zone).toInstant()
        val groupId = existing?.transferGroupId ?: UUID.randomUUID().toString()
        val notes = input.notes?.trim()?.takeIf(String::isNotEmpty)

        fun leg(previous: Transaction?, account: Account, type: TransactionType) = Transaction(
            id = previous?.id ?: UUID.randomUUID().toString(),
            accountId = account.id,
            type = type,
            amount = Money(input.amountMinor, account.currency),
            description = description,
            bookingDate = bookingDate,
            source = previous?.source ?: TransactionSource.MANUAL,
            status = input.status,
            transferGroupId = groupId,
            notes = notes,
            createdAt = previous?.createdAt ?: now,
            updatedAt = now,
        )

        val legs = TransferLegs(
            outgoing = leg(existing?.outgoing, from, TransactionType.EXPENSE),
            incoming = leg(existing?.incoming, to, TransactionType.INCOME),
        )
        val list = listOf(legs.outgoing, legs.incoming)
        if (existing == null) transactionRepository.createTransfer(list) else transactionRepository.updateTransfer(list)
        return SaveTransferResult.Saved(legs)
    }
}
