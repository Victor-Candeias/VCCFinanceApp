package pt.vcc.financeapp.core.model

/**
 * Converte texto introduzido pelo utilizador ou lido de extratos em unidades mínimas, sem
 * Float/Double (ex.: `1.234,56` → 123456).
 *
 * Regras: a vírgula é sempre o separador decimal e `.`/espaços são separadores de milhares; sem
 * vírgula, um único `.` seguido de 1–2 dígitos é decimal (`12.5`), caso contrário é separador de
 * milhares (`1.234.567`). Aceita sinal `-`/`+` e o símbolo `€`. Rejeita mais casas decimais do que a
 * moeda permite em vez de arredondar.
 */
object AmountParser {
    sealed interface Result {
        data class Success(val amountMinor: Long) : Result
        data class Failure(val reason: Reason) : Result
    }

    enum class Reason { EMPTY, INVALID_FORMAT, TOO_MANY_DECIMALS, OUT_OF_RANGE }

    fun parse(text: String, fractionDigits: Int = 2): Result {
        require(fractionDigits in 0..4) { "Casas decimais não suportadas: $fractionDigits" }
        var value = text.trim()
            .replace("€", "")
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .replace('\u2212', '-')
            .trim()
        if (value.isEmpty()) return Result.Failure(Reason.EMPTY)

        var negative = false
        when (value.first()) {
            '-' -> { negative = true; value = value.drop(1).trim() }
            '+' -> value = value.drop(1).trim()
        }
        if (value.endsWith("-")) {
            negative = !negative
            value = value.dropLast(1).trim()
        }
        if (value.isEmpty() || value.any { !it.isDigit() && it != ',' && it != '.' && it != ' ' }) {
            return Result.Failure(Reason.INVALID_FORMAT)
        }

        val (integerPart, decimalPart) = splitDecimal(value) ?: return Result.Failure(Reason.INVALID_FORMAT)
        if (!GROUPED_INTEGER.matches(integerPart)) return Result.Failure(Reason.INVALID_FORMAT)
        if (decimalPart.any { !it.isDigit() }) return Result.Failure(Reason.INVALID_FORMAT)
        if (decimalPart.length > fractionDigits) return Result.Failure(Reason.TOO_MANY_DECIMALS)

        val digits = integerPart.filter(Char::isDigit) + decimalPart.padEnd(fractionDigits, '0')
        val magnitude = digits.trimStart('0').ifEmpty { "0" }
        if (magnitude.length > 18) return Result.Failure(Reason.OUT_OF_RANGE)
        val amount = magnitude.toLong()
        return Result.Success(if (negative) -amount else amount)
    }

    /** Atalho que devolve `null` quando o texto não é válido. */
    fun parseOrNull(text: String, fractionDigits: Int = 2): Long? =
        (parse(text, fractionDigits) as? Result.Success)?.amountMinor

    private fun splitDecimal(value: String): Pair<String, String>? {
        val commas = value.count { it == ',' }
        if (commas > 1) return null
        if (commas == 1) {
            val integer = value.substringBefore(',')
            return integer.ifEmpty { "0" } to value.substringAfter(',')
        }
        val dots = value.count { it == '.' }
        if (dots == 1) {
            val decimals = value.substringAfter('.')
            if (decimals.length in 1..2 && decimals.all(Char::isDigit)) {
                return value.substringBefore('.').ifEmpty { "0" } to decimals
            }
        }
        return value to ""
    }

    // Dígitos simples ou agrupados de 3 em 3 com `.` ou espaço (ex.: 1234, 1.234, 1 234 567).
    private val GROUPED_INTEGER = Regex("""\d+|\d{1,3}([. ]\d{3})+""")
}
