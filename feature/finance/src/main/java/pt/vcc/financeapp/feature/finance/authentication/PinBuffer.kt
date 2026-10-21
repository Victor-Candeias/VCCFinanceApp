package pt.vcc.financeapp.feature.finance.authentication

import pt.vcc.financeapp.core.security.PinPolicy

/**
 * Dígitos do PIN em edição, num [CharArray] que é limpo depois de usado. A UI só vê [length].
 */
internal class PinBuffer {
    private val digits = CharArray(PinPolicy.MAX_LENGTH)
    var length: Int = 0
        private set

    fun append(digit: Char): Boolean {
        if (digit !in '0'..'9' || length == digits.size) return false
        digits[length++] = digit
        return true
    }

    fun removeLast() {
        if (length > 0) digits[--length] = '\u0000'
    }

    /** Cópia dos dígitos; quem a recebe deve limpá-la com `fill`. */
    fun copy(): CharArray = digits.copyOf(length)

    fun clear() {
        digits.fill('\u0000')
        length = 0
    }
}
