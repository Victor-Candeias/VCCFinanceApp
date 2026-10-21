package pt.vcc.financeapp.core.model

/** Receitas e despesas de um período numa única moeda. */
data class PeriodTotals(
    val income: Money,
    val expenses: Money,
) {
    init {
        require(income.currency == expenses.currency) { "Moedas diferentes: ${income.currency} e ${expenses.currency}" }
    }

    val currency: String get() = income.currency
    val net: Money get() = income - expenses

    companion object {
        /**
         * Totais por moeda (sem conversões), ordenados pelo código da moeda. Contam apenas movimentos
         * `BOOKED` de receita/despesa; as transferências internas ficam de fora (README §1 e §5).
         */
        fun byCurrency(transactions: Iterable<Transaction>): List<PeriodTotals> =
            transactions
                .filter { it.status == TransactionStatus.BOOKED && !it.isInternalTransfer }
                .groupBy { it.amount.currency }
                .toSortedMap()
                .map { (currency, items) ->
                    PeriodTotals(
                        income = items.sumOf(currency, TransactionType.INCOME),
                        expenses = items.sumOf(currency, TransactionType.EXPENSE),
                    )
                }

        private fun List<Transaction>.sumOf(currency: String, type: TransactionType): Money =
            filter { it.type == type }.fold(Money.zero(currency)) { total, tx -> total + tx.amount }
    }
}
