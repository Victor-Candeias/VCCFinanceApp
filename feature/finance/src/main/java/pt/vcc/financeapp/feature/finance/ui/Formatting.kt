package pt.vcc.financeapp.feature.finance.ui

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import pt.vcc.financeapp.core.model.Money

internal val PortugueseLocale: Locale = Locale.forLanguageTag("pt-PT")

private val DateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", PortugueseLocale)
private val MonthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", PortugueseLocale)

/** Formata em pt-PT (ex.: `1 234,56 €`) sem passar por Float/Double. */
internal fun Money.formatted(): String {
    val currencyInstance = runCatching { Currency.getInstance(currency) }.getOrNull()
    val digits = currencyInstance?.defaultFractionDigits?.coerceAtLeast(0) ?: 2
    val value = BigDecimal.valueOf(amountMinor, digits)
    if (currencyInstance == null) {
        return NumberFormat.getNumberInstance(PortugueseLocale).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }.format(value) + " $currency"
    }
    return NumberFormat.getCurrencyInstance(PortugueseLocale).apply {
        this.currency = currencyInstance
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(value)
}

internal fun Instant.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate = atZone(zone).toLocalDate()

internal fun LocalDate.formatted(): String = format(DateFormatter)

internal fun YearMonth.formatted(): String = format(MonthFormatter).replaceFirstChar { it.titlecase(PortugueseLocale) }

/** Intervalo `[início do mês, início do mês seguinte)` no fuso indicado. */
internal fun YearMonth.toInstantRange(zone: ZoneId): Pair<Instant, Instant> =
    atDay(1).atStartOfDay(zone).toInstant() to plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
