package pt.vcc.financeapp.core.model

/**
 * Montante em unidades mínimas da moeda (ex.: 12,50 EUR = 1250). Nunca usar Float/Double em
 * cálculos financeiros nem somar moedas distintas sem conversão explícita (README §1-§2).
 */
data class Money(val amountMinor: Long, val currency: String) {
    init {
        require(CURRENCY_CODE.matches(currency)) { "Código de moeda ISO 4217 inválido: '$currency'" }
    }

    val isNegative: Boolean get() = amountMinor < 0

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.addExact(amountMinor, other.amountMinor), currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.subtractExact(amountMinor, other.amountMinor), currency)
    }

    operator fun unaryMinus(): Money = Money(Math.negateExact(amountMinor), currency)

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) { "Moedas diferentes: $currency e ${other.currency}" }
    }

    companion object {
        const val DEFAULT_CURRENCY = "EUR"
        private val CURRENCY_CODE = Regex("[A-Z]{3}")

        fun zero(currency: String = DEFAULT_CURRENCY): Money = Money(0L, currency)
    }
}
