package pt.vcc.financeapp.core.security

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Deriva o verificador do PIN com PBKDF2-HMAC-SHA256 e salt aleatório (README §10). O PIN nunca é
 * guardado; só o resultado da derivação.
 */
object PinHasher {
    const val DEFAULT_ITERATIONS = 210_000
    const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    fun newSalt(random: SecureRandom = SecureRandom()): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    fun derive(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
