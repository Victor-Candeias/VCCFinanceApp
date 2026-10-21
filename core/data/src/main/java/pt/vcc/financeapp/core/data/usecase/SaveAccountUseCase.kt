package pt.vcc.financeapp.core.data.usecase

import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import pt.vcc.financeapp.core.data.repository.AccountRepository
import pt.vcc.financeapp.core.data.repository.TransactionRepository
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.Money

/** Dados de uma conta tal como introduzidos no formulário; [id] nulo cria uma conta nova. */
data class AccountInput(
    val id: String? = null,
    val name: String,
    val type: AccountType,
    val institutionName: String?,
    val currency: String,
    val openingBalanceMinor: Long,
)

enum class AccountValidationError { BLANK_NAME, NAME_TOO_LONG, INVALID_CURRENCY, CURRENCY_LOCKED, NOT_FOUND }

sealed interface SaveAccountResult {
    data class Saved(val account: Account) : SaveAccountResult
    data class Invalid(val errors: Set<AccountValidationError>) : SaveAccountResult
}

/**
 * Cria ou atualiza uma conta. A moeda de uma conta com movimentos não pode mudar, para nunca
 * misturar moedas no cálculo do saldo (README §1).
 */
class SaveAccountUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(input: AccountInput): SaveAccountResult {
        val name = input.name.trim()
        val currency = input.currency.trim().uppercase()
        val errors = mutableSetOf<AccountValidationError>()
        if (name.isEmpty()) errors += AccountValidationError.BLANK_NAME
        if (name.length > MAX_NAME_LENGTH) errors += AccountValidationError.NAME_TOO_LONG
        if (!CURRENCY_CODE.matches(currency)) errors += AccountValidationError.INVALID_CURRENCY

        val existing = input.id?.let { accountRepository.getById(it) }
        if (input.id != null && existing == null) errors += AccountValidationError.NOT_FOUND
        if (existing != null && existing.currency != currency && transactionRepository.countByAccount(existing.id) > 0) {
            errors += AccountValidationError.CURRENCY_LOCKED
        }
        if (errors.isNotEmpty()) return SaveAccountResult.Invalid(errors)

        val account = Account(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name,
            type = input.type,
            openingBalance = Money(input.openingBalanceMinor, currency),
            institutionName = input.institutionName?.trim()?.takeIf(String::isNotEmpty),
            ibanEncrypted = existing?.ibanEncrypted,
            connectionId = existing?.connectionId,
            isActive = existing?.isActive ?: true,
            createdAt = existing?.createdAt ?: clock.instant(),
        )
        if (existing == null) accountRepository.create(account) else accountRepository.update(account)
        return SaveAccountResult.Saved(account)
    }

    companion object {
        const val MAX_NAME_LENGTH = 60
        private val CURRENCY_CODE = Regex("[A-Z]{3}")
    }
}
