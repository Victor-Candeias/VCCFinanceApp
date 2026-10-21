package pt.vcc.financeapp.core.security

enum class PinValidationError { TOO_SHORT, TOO_LONG, NOT_DIGITS, TOO_SIMPLE }

/**
 * Regras do PIN da app: 4 a 8 algarismos, sem padrões triviais (todos iguais ou sequências
 * crescentes/decrescentes como 1234 ou 9876).
 */
object PinPolicy {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    fun validate(pin: CharArray): PinValidationError? = when {
        pin.any { it !in '0'..'9' } -> PinValidationError.NOT_DIGITS
        pin.size < MIN_LENGTH -> PinValidationError.TOO_SHORT
        pin.size > MAX_LENGTH -> PinValidationError.TOO_LONG
        isTrivial(pin) -> PinValidationError.TOO_SIMPLE
        else -> null
    }

    private fun isTrivial(pin: CharArray): Boolean {
        val steps = (1 until pin.size).map { pin[it] - pin[it - 1] }.toSet()
        return steps == setOf(0) || steps == setOf(1) || steps == setOf(-1)
    }
}
