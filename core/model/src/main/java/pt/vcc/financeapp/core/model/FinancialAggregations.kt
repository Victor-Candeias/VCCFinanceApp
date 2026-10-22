package pt.vcc.financeapp.core.model

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Despesas confirmadas de uma categoria (`null` = sem categoria) numa única moeda. */
data class CategoryTotal(
    val categoryId: String?,
    val total: Money,
)

/** Receitas e despesas de um mês numa única moeda. */
data class MonthTotals(
    val month: YearMonth,
    val totals: PeriodTotals,
)

/** Agregações usadas nos gráficos e relatórios; contam só movimentos `BOOKED` e excluem transferências internas. */
object FinancialAggregations {

    /**
     * Despesas por categoria na [currency], da maior para a menor. Com [rootOf] as subcategorias
     * somam na categoria principal.
     */
    fun expensesByCategory(
        transactions: Iterable<Transaction>,
        currency: String,
        rootOf: (String?) -> String? = { it },
    ): List<CategoryTotal> = totalsByCategory(transactions, TransactionType.EXPENSE, currency, rootOf)

    /** Totais por categoria do [type] (receita ou despesa) na [currency], do maior para o menor. */
    fun totalsByCategory(
        transactions: Iterable<Transaction>,
        type: TransactionType,
        currency: String,
        rootOf: (String?) -> String? = { it },
    ): List<CategoryTotal> =
        transactions
            .filter { it.countsAs(type, currency) }
            .groupBy { rootOf(it.categoryId) }
            .map { (categoryId, items) -> CategoryTotal(categoryId, items.sum(currency)) }
            .sortedWith(compareByDescending<CategoryTotal> { it.total.amountMinor }.thenBy { it.categoryId.orEmpty() })

    /** Totais por mês na [currency] para os [months] indicados, incluindo meses sem movimentos. */
    fun monthlyTotals(
        transactions: Iterable<Transaction>,
        currency: String,
        months: List<YearMonth>,
        zone: ZoneId,
    ): List<MonthTotals> {
        val byMonth = transactions
            .filter { it.amount.currency == currency && it.status == TransactionStatus.BOOKED && !it.isInternalTransfer }
            .groupBy { YearMonth.from(it.bookingDate.atZone(zone)) }
        return months.map { month ->
            val items = byMonth[month].orEmpty()
            MonthTotals(
                month,
                PeriodTotals(
                    income = items.filter { it.type == TransactionType.INCOME }.sum(currency),
                    expenses = items.filter { it.type == TransactionType.EXPENSE }.sum(currency),
                ),
            )
        }
    }

    /** Os [count] meses que terminam em [last], do mais antigo para o mais recente. */
    fun monthsEndingAt(last: YearMonth, count: Int): List<YearMonth> =
        (count - 1 downTo 0).map { last.minusMonths(it.toLong()) }

    /** Início do primeiro mês e início do mês seguinte ao último, no [zone]. */
    fun range(months: List<YearMonth>, zone: ZoneId): Pair<Instant, Instant> =
        months.first().atDay(1).atStartOfDay(zone).toInstant() to
            months.last().plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()

    /**
     * Moeda principal para gráficos sem conversão: a com mais movimentos de receita/despesa;
     * em empate, a primeira por ordem alfabética. `null` se não houver movimentos.
     */
    fun mainCurrency(transactions: Iterable<Transaction>): String? =
        transactions
            .filter { it.status == TransactionStatus.BOOKED && !it.isInternalTransfer }
            .groupingBy { it.amount.currency }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()
            ?.key

    private fun Transaction.countsAs(type: TransactionType, currency: String): Boolean =
        this.type == type && amount.currency == currency && status == TransactionStatus.BOOKED && !isInternalTransfer

    private fun List<Transaction>.sum(currency: String): Money =
        fold(Money.zero(currency)) { total, tx -> total + tx.amount }
}
