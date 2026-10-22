package pt.vcc.financeapp.core.data.importer

import java.time.LocalDate
import pt.vcc.financeapp.core.model.TransactionType

/** Saldo final de um extrato: o saldo indicado na linha do último movimento. */
data class StatementClosingBalance(val date: LocalDate, val balanceMinor: Long)

object StatementBalance {

    /**
     * Encontra o saldo final a partir da coluna de saldo. A ordem do ficheiro (crescente ou decrescente) é
     * deduzida pelo encadeamento saldo anterior + valor = saldo; se for inconclusivo, pelas datas.
     * Devolve `null` se nenhuma linha válida tiver saldo.
     */
    fun closing(rows: List<ParsedImportRow>): StatementClosingBalance? {
        val usable = rows.filter { it.isValid && it.balanceMinor != null && it.bookingDate != null && it.type != null }
        if (usable.isEmpty()) return null
        var ascendingLinks = 0
        var descendingLinks = 0
        for (i in 1 until usable.size) {
            val previous = usable[i - 1]
            val current = usable[i]
            if (previous.balanceMinor!! + current.signedAmount() == current.balanceMinor) ascendingLinks++
            if (current.balanceMinor!! + previous.signedAmount() == previous.balanceMinor) descendingLinks++
        }
        val ascending = when {
            ascendingLinks != descendingLinks -> ascendingLinks > descendingLinks
            else -> usable.first().bookingDate!! <= usable.last().bookingDate!!
        }
        val closing = if (ascending) usable.last() else usable.first()
        return StatementClosingBalance(closing.bookingDate!!, closing.balanceMinor!!)
    }

    private fun ParsedImportRow.signedAmount(): Long = if (type == TransactionType.INCOME) amountMinor else -amountMinor
}
