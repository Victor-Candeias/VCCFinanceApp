package pt.vcc.financeapp.core.model

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

enum class ReportPeriodType { MONTH, YEAR }

/** Mês ou ano civil de um relatório; num ano, [month] é sempre janeiro. */
data class ReportPeriod private constructor(val type: ReportPeriodType, val month: YearMonth) {
    val year: Int get() = month.year

    /** Meses abrangidos, do primeiro para o último. */
    val months: List<YearMonth>
        get() = when (type) {
            ReportPeriodType.MONTH -> listOf(month)
            ReportPeriodType.YEAR -> (1..12).map { YearMonth.of(year, it) }
        }

    fun previous(): ReportPeriod = shift(-1)

    fun next(): ReportPeriod = shift(1)

    /** Muda o tipo mantendo a data de referência (mês → ano desse mês; ano → janeiro ou [current] se for o mesmo ano). */
    fun withType(type: ReportPeriodType, current: YearMonth): ReportPeriod = when (type) {
        ReportPeriodType.MONTH -> month(if (current.year == year) current else month)
        ReportPeriodType.YEAR -> year(year)
    }

    /** `[início, fim)` no [zone]. */
    fun range(zone: ZoneId): Pair<Instant, Instant> = FinancialAggregations.range(months, zone)

    private fun shift(step: Long): ReportPeriod = when (type) {
        ReportPeriodType.MONTH -> month(month.plusMonths(step))
        ReportPeriodType.YEAR -> year(year + step.toInt())
    }

    companion object {
        fun month(month: YearMonth): ReportPeriod = ReportPeriod(ReportPeriodType.MONTH, month)

        fun year(year: Int): ReportPeriod = ReportPeriod(ReportPeriodType.YEAR, YearMonth.of(year, 1))
    }
}

/** Receitas e despesas de uma conta num relatório. */
data class AccountTotals(
    val accountId: String,
    val totals: PeriodTotals,
)

/** Relatório de um período numa única moeda (sem conversões). */
data class FinancialReport(
    val period: ReportPeriod,
    val currency: String,
    val totals: PeriodTotals,
    /** Por categoria principal (subcategorias somadas), do maior para o menor. */
    val expensesByCategory: List<CategoryTotal>,
    val incomeByCategory: List<CategoryTotal>,
    /** Por conta, da maior despesa para a menor (em empate, maior receita). */
    val byAccount: List<AccountTotals>,
    /** Um elemento por mês do período, incluindo meses sem movimentos. */
    val monthly: List<MonthTotals>,
    /** Movimentos considerados: confirmados, sem transferências internas, mais recentes primeiro. */
    val transactions: List<Transaction>,
) {
    companion object {
        /**
         * Um relatório por moeda com movimentos no período, ordenados pelo código da moeda. [rootOf]
         * devolve a categoria principal de uma categoria.
         */
        fun build(
            period: ReportPeriod,
            transactions: Iterable<Transaction>,
            zone: ZoneId,
            rootOf: (String?) -> String? = { it },
        ): List<FinancialReport> {
            val (from, to) = period.range(zone)
            val counted = transactions.filter {
                it.status == TransactionStatus.BOOKED && !it.isInternalTransfer && it.bookingDate >= from && it.bookingDate < to
            }
            return counted.groupBy { it.amount.currency }.toSortedMap().map { (currency, items) ->
                val totals = PeriodTotals.byCurrency(items).single()
                FinancialReport(
                    period = period,
                    currency = currency,
                    totals = totals,
                    expensesByCategory = FinancialAggregations.totalsByCategory(items, TransactionType.EXPENSE, currency, rootOf),
                    incomeByCategory = FinancialAggregations.totalsByCategory(items, TransactionType.INCOME, currency, rootOf),
                    byAccount = items.groupBy { it.accountId }
                        .map { (accountId, accountItems) -> AccountTotals(accountId, PeriodTotals.byCurrency(accountItems).single()) }
                        .sortedWith(
                            compareByDescending<AccountTotals> { it.totals.expenses.amountMinor }
                                .thenByDescending { it.totals.income.amountMinor }
                                .thenBy { it.accountId },
                        ),
                    monthly = FinancialAggregations.monthlyTotals(items, currency, period.months, zone),
                    transactions = items.sortedWith(compareByDescending<Transaction> { it.bookingDate }.thenBy { it.id }),
                )
            }
        }
    }
}
