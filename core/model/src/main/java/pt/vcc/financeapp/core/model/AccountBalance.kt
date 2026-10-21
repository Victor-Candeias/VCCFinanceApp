package pt.vcc.financeapp.core.model

/**
 * Soma de movimentos de uma conta para uma combinação tipo/estado/moeda. Pode representar um único
 * movimento ([Transaction.asBalanceMovement]) ou um total agregado pela base de dados.
 */
data class BalanceMovement(
    val accountId: String,
    val type: TransactionType,
    val status: TransactionStatus,
    val amount: Money,
)

fun Transaction.asBalanceMovement() = BalanceMovement(accountId, type, status, amount)

/**
 * Saldo calculado de uma conta, na moeda da conta.
 *
 * @property booked saldo inicial + receitas − despesas confirmadas (`BOOKED`), incluindo pernas de
 *   transferências internas.
 * @property pending efeito líquido dos movimentos pendentes, ainda não incluído em [booked].
 * @property ignoredForeignMovements movimentos noutra moeda, que não são somados nem convertidos.
 */
data class AccountBalance(
    val accountId: String,
    val booked: Money,
    val pending: Money,
    val ignoredForeignMovements: Int = 0,
) {
    val currency: String get() = booked.currency

    /** Saldo previsto quando os pendentes forem confirmados. */
    val projected: Money get() = booked + pending
}

/**
 * Cálculo do saldo de cada conta (README §6). Cancelados e o tipo legado `TRANSFER` (sem direção)
 * não alteram o saldo; o saldo comunicado pela instituição fica à parte (`account_balance_snapshots`).
 */
object AccountBalanceCalculator {
    fun calculate(account: Account, movements: Iterable<BalanceMovement>): AccountBalance {
        val currency = account.currency
        var booked = account.openingBalance.amountMinor
        var pending = 0L
        var ignored = 0
        for (movement in movements) {
            if (movement.accountId != account.id) continue
            if (movement.amount.currency != currency) {
                ignored++
                continue
            }
            val signed = when (movement.type) {
                TransactionType.INCOME -> movement.amount.amountMinor
                TransactionType.EXPENSE -> -movement.amount.amountMinor
                TransactionType.TRANSFER -> 0L
            }
            when (movement.status) {
                TransactionStatus.BOOKED -> booked = Math.addExact(booked, signed)
                TransactionStatus.PENDING -> pending = Math.addExact(pending, signed)
                TransactionStatus.CANCELLED -> Unit
            }
        }
        return AccountBalance(account.id, Money(booked, currency), Money(pending, currency), ignored)
    }

    /** Saldos de várias contas, indexados pelo id da conta. */
    fun calculateAll(accounts: Iterable<Account>, movements: Iterable<BalanceMovement>): Map<String, AccountBalance> {
        val byAccount = movements.groupBy { it.accountId }
        return accounts.associate { it.id to calculate(it, byAccount[it.id].orEmpty()) }
    }

    /** Soma dos saldos confirmados por moeda (sem conversões), ordenada pelo código da moeda. */
    fun totalsByCurrency(balances: Iterable<AccountBalance>): List<Money> =
        balances.groupBy { it.currency }
            .toSortedMap()
            .map { (currency, items) -> items.fold(Money.zero(currency)) { total, b -> total + b.booked } }
}
