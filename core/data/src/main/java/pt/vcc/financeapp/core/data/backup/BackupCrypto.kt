package pt.vcc.financeapp.core.data.backup

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import pt.vcc.financeapp.core.security.PinHasher

/**
 * Cifra o ficheiro de backup com uma palavra-passe escolhida pelo utilizador (independente do
 * dispositivo, para poder restaurar noutro telemóvel): chave PBKDF2-HMAC-SHA256 e AES-256-GCM.
 *
 * Formato: `VCCBKP` · versão (1 byte) · iterações (int) · salt (16) · IV (12) · texto cifrado + tag.
 * O cabeçalho é autenticado (AAD), por isso qualquer alteração ao ficheiro é detetada.
 */
object BackupCrypto {
    const val FORMAT_VERSION: Byte = 1
    const val DEFAULT_ITERATIONS = 600_000
    private val MAGIC = "VCCBKP".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val HEADER_BYTES = MAGIC.size + 1 + Int.SIZE_BYTES + SALT_BYTES + IV_BYTES

    // Limites às iterações lidas do ficheiro: nem demasiado fracas nem um bloqueio da app.
    private val ACCEPTED_ITERATIONS = 100_000..10_000_000

    fun encrypt(
        plain: ByteArray,
        password: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC).put(FORMAT_VERSION).putInt(iterations).put(salt).put(iv)
            .array()
        val cipher = cipher(Cipher.ENCRYPT_MODE, password, salt, iterations, iv, header)
        return header + cipher.doFinal(plain)
    }

    fun decrypt(blob: ByteArray, password: CharArray): ByteArray {
        if (blob.size < HEADER_BYTES || !blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw BackupException(BackupError.NOT_A_BACKUP)
        }
        val buffer = ByteBuffer.wrap(blob, MAGIC.size, HEADER_BYTES - MAGIC.size)
        if (buffer.get() != FORMAT_VERSION) throw BackupException(BackupError.UNSUPPORTED_VERSION)
        val iterations = buffer.getInt()
        if (iterations !in ACCEPTED_ITERATIONS) throw BackupException(BackupError.NOT_A_BACKUP)
        val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
        val iv = ByteArray(IV_BYTES).also { buffer.get(it) }
        val header = blob.copyOfRange(0, HEADER_BYTES)
        return try {
            cipher(Cipher.DECRYPT_MODE, password, salt, iterations, iv, header)
                .doFinal(blob, HEADER_BYTES, blob.size - HEADER_BYTES)
        } catch (e: AEADBadTagException) {
            // A GCM não distingue palavra-passe errada de ficheiro adulterado.
            throw BackupException(BackupError.WRONG_PASSWORD, e)
        } catch (e: GeneralSecurityException) {
            throw BackupException(BackupError.NOT_A_BACKUP, e)
        }
    }

    private fun cipher(mode: Int, password: CharArray, salt: ByteArray, iterations: Int, iv: ByteArray, aad: ByteArray): Cipher {
        val key = PinHasher.derive(password, salt, iterations)
        try {
            return Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
                updateAAD(aad)
            }
        } finally {
            key.fill(0)
        }
    }
}
