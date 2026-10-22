package pt.vcc.financeapp.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class BudgetAlertLevel { OK, WARNING, EXCEEDED }

/** Gasto de um orçamento no período civil atual. */
data class BudgetProgress(
    val budget: Budget,
    val periodStart: Instant,
    /** Exclusivo. */
    val periodEnd: Instant,
    val spent: Money,
) {
    val remaining: Money get() = budget.limit - spent

    /** Percentagem usada, arredondada às unidades (pode passar de 100). */
    val percentUsed: Int
        get() = if (budget.limit.amountMinor <= 0) 0 else ((spent.amountMinor * 100 + budget.limit.amountMinor / 2) / budget.limit.amountMinor).toInt()

    val alertLevel: BudgetAlertLevel
        get() = when {
            spent.amountMinor > budget.limit.amountMinor -> BudgetAlertLevel.EXCEEDED
            spent.amountMinor * 100 >= budget.limit.amountMinor * budget.alertThresholdPercent -> BudgetAlertLevel.WARNING
            else -> BudgetAlertLevel.OK
        }

    companion object {
        /**
         * Progresso de [budget] no período que contém [now], ou `null` se o orçamento estiver inativo ou fora
         * da validade. Contam despesas confirmadas na moeda do limite, sem transferências internas, da
         * categoria do orçamento e das subcategorias ([categories]); sem categoria contam todas as despesas.
         */
        fun calculate(
            budget: Budget,
            transactions: Iterable<Transaction>,
            categories: Iterable<Category>,
            now: Instant,
            zone: ZoneId,
        ): BudgetProgress? {
            if (!budget.isActive || now < budget.startDate || budget.endDate?.let { now >= it } == true) return null
            val (from, to) = BudgetPeriods.range(budget.period, now, zone)
            val included = budget.categoryId?.let { root -> descendantsOf(root, categories) + root }
            val spent = transactions
                .filter {
                    it.type == TransactionType.EXPENSE &&
                        it.status == TransactionStatus.BOOKED &&
                        !it.isInternalTransfer &&
                        it.amount.currency == budget.limit.currency &&
                        it.bookingDate >= from && it.bookingDate < to &&
                        (included == null || it.categoryId in included)
                }
                .fold(Money.zero(budget.limit.currency)) { total, tx -> total + tx.amount }
            return BudgetProgress(budget, from, to, spent)
        }

        private fun descendantsOf(root: String, categories: Iterable<Category>): Set<String> {
            val children = categories.groupBy { it.parentCategoryId }
            val result = mutableSetOf<String>()
            val pending = ArrayDeque(listOf(root))
            while (pending.isNotEmpty()) {
                children[pending.removeFirst()].orEmpty().forEach { if (result.add(it.id)) pending += it.id }
            }
            return result
        }
    }
}

object BudgetPeriods {
    /** Período civil `[início, fim)` que contém [now]; as semanas começam à segunda-feira. */
    fun range(period: BudgetPeriod, now: Instant, zone: ZoneId): Pair<Instant, Instant> {
        val today = now.atZone(zone).toLocalDate()
        val start: LocalDate
        val end: LocalDate
        when (period) {
            BudgetPeriod.WEEKLY -> {
                start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                end = start.plusWeeks(1)
            }
            BudgetPeriod.MONTHLY -> {
                start = today.withDayOfMonth(1)
                end = start.plusMonths(1)
            }
            BudgetPeriod.QUARTERLY -> {
                start = LocalDate.of(today.year, (today.monthValue - 1) / 3 * 3 + 1, 1)
                end = start.plusMonths(3)
            }
            BudgetPeriod.YEARLY -> {
                start = LocalDate.of(today.year, 1, 1)
                end = start.plusYears(1)
            }
        }
        return start.atStartOfDay(zone).toInstant() to end.atStartOfDay(zone).toInstant()
    }
}
