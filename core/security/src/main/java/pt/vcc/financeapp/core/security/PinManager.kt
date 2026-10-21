package pt.vcc.financeapp.core.security

import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Configura e verifica o PIN da app. O verificador guardado é `HMAC_keystore(PBKDF2(pin, salt))`.
 * As derivações são lentas por desenho e correm fora da thread principal.
 */
@Singleton
class PinManager(
    private val store: SecurityStore,
    private val pepper: PinPepper,
    private val dispatcher: CoroutineDispatcher,
    private val iterations: Int,
) {
    @Inject
    constructor(store: SecurityStore, pepper: PinPepper) :
        this(store, pepper, Dispatchers.Default, PinHasher.DEFAULT_ITERATIONS)

    val isPinConfigured: Flow<Boolean> = store.preferences.map { it.isPinConfigured }

    /** Grava um PIN novo, substituindo o anterior. Lança [IllegalArgumentException] se o PIN for inválido. */
    suspend fun configurePin(pin: CharArray) {
        PinPolicy.validate(pin)?.let { throw IllegalArgumentException("PIN inválido: $it") }
        val salt = PinHasher.newSalt()
        val verifier = withContext(dispatcher) {
            pepper.deleteKey()
            pepper.createKey()
            computeVerifier(pin, salt, iterations)
        } ?: error("Chave do Keystore indisponível depois de criada")
        store.update {
            it.copy(
                pinVerifier = encoder.encodeToString(verifier),
                pinSalt = encoder.encodeToString(salt),
                pinIterations = iterations,
            )
        }
    }

    /** `true` se [pin] corresponder ao PIN configurado. */
    suspend fun verifyPin(pin: CharArray): Boolean {
        val prefs = store.current()
        if (!prefs.isPinConfigured) return false
        val salt = decoder.decode(prefs.pinSalt)
        val expected = decoder.decode(prefs.pinVerifier)
        val actual = withContext(dispatcher) { computeVerifier(pin, salt, prefs.pinIterations) } ?: return false
        return MessageDigest.isEqual(expected, actual)
    }

    private fun computeVerifier(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray? {
        val derived = PinHasher.derive(pin, salt, iterations)
        return try {
            pepper.apply(derived)
        } finally {
            derived.fill(0)
        }
    }

    private companion object {
        val encoder: Base64.Encoder = Base64.getEncoder()
        val decoder: Base64.Decoder = Base64.getDecoder()
    }
}
