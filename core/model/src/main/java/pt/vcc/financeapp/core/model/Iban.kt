package pt.vcc.financeapp.core.model

import java.math.BigInteger

/** Validação e apresentação de IBAN (ISO 13616). */
object Iban {
    private val PATTERN = Regex("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}")
    private val NINETY_SEVEN = BigInteger.valueOf(97)

    /** Remove espaços/hífenes e passa a maiúsculas. */
    fun normalize(input: String): String = input.filterNot { it.isWhitespace() || it == '-' }.uppercase()

    /** Formato e dígitos de controlo (mod 97); recebe o valor já normalizado ou não. */
    fun isValid(input: String): Boolean {
        val iban = normalize(input)
        if (!PATTERN.matches(iban)) return false
        val rearranged = iban.substring(4) + iban.substring(0, 4)
        val digits = rearranged.map { if (it.isDigit()) it.toString() else (it - 'A' + 10).toString() }.joinToString("")
        return BigInteger(digits).mod(NINETY_SEVEN) == BigInteger.ONE
    }

    /** Grupos de 4 caracteres: `PT50 0002 0123 …`. */
    fun format(input: String): String = normalize(input).chunked(4).joinToString(" ")

    /** Só o país, os dígitos de controlo e os últimos 4 caracteres ficam visíveis. */
    fun mask(input: String): String {
        val iban = normalize(input)
        if (iban.length <= 8) return iban
        return "${iban.take(4)} •••• •••• ${iban.takeLast(4)}"
    }
}
