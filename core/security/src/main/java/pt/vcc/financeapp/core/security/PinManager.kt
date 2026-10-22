package pt.vcc.financeapp.core.security

import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Resultado de uma verificação do PIN. */
sealed interface PinVerification {
    data object Success : PinVerification

    /** PIN errado; [remainingAttempts] antes do próximo bloqueio (0 se o próximo erro bloquear). */
    data class Wrong(val remainingAttempts: Int) : PinVerification

    /** Demasiadas tentativas: não se verifica nenhum PIN até [until]. */
    data class LockedOut(val until: Instant) : PinVerification

    /** A chave do Keystore do verificador desapareceu: nenhum PIN pode ser verificado (README §10, recuperação). */
    data object KeyUnavailable : PinVerification
}

/**
 * Configura e verifica o PIN da app. O verificador guardado é `HMAC_keystore(PBKDF2(pin, salt))`.
 * As derivações são lentas por desenho e correm fora da thread principal.
 *
 * Limite de tentativas: as primeiras [MAX_ATTEMPTS] falhas são livres; a partir daí cada falha
 * bloqueia a verificação por 30 s, duplicando a cada nova falha até 1 h. O contador é persistido
 * **antes** da derivação, para que terminar a app a meio não dê tentativas grátis, e só volta a
 * zero com um PIN correto.
 */
@Singleton
class PinManager(
    private val store: SecurityStore,
    private val pepper: PinPepper,
    private val clock: Clock,
    private val dispatcher: CoroutineDispatcher,
    private val iterations: Int,
) {
    @Inject
    constructor(store: SecurityStore, pepper: PinPepper, clock: Clock) :
        this(store, pepper, clock, Dispatchers.Default, PinHasher.DEFAULT_ITERATIONS)

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
                failedAttempts = 0,
                lockedUntilMillis = 0,
            )
        }
    }

    /** Fim do bloqueio em curso, ou `null` se for possível tentar já. */
    suspend fun lockoutEnd(): Instant? {
        val until = store.current().lockedUntilMillis
        return if (until > clock.millis()) Instant.ofEpochMilli(until) else null
    }

    /** `false` se houver PIN mas a chave do Keystore usada no verificador já não existir. */
    suspend fun isPinKeyAvailable(): Boolean =
        !store.current().isPinConfigured || withContext(dispatcher) { pepper.apply(ByteArray(1)) != null }

    suspend fun verifyPin(pin: CharArray): PinVerification {
        lockoutEnd()?.let { return PinVerification.LockedOut(it) }
        val prefs = store.current()
        if (!prefs.isPinConfigured) return PinVerification.Wrong(remainingAttempts = 0)
        // Sem chave não há tentativa possível; não conta como falha.
        if (!isPinKeyAvailable()) return PinVerification.KeyUnavailable

        store.update { it.copy(failedAttempts = it.failedAttempts + 1) }
        val salt = decoder.decode(prefs.pinSalt)
        val expected = decoder.decode(prefs.pinVerifier)
        val actual = withContext(dispatcher) { computeVerifier(pin, salt, prefs.pinIterations) }

        if (actual != null && MessageDigest.isEqual(expected, actual)) {
            store.update { it.copy(failedAttempts = 0, lockedUntilMillis = 0) }
            return PinVerification.Success
        }

        val failures = store.current().failedAttempts
        if (failures < MAX_ATTEMPTS) return PinVerification.Wrong(remainingAttempts = MAX_ATTEMPTS - failures)
        val until = clock.millis() + lockoutDurationMillis(failures)
        store.update { it.copy(lockedUntilMillis = until) }
        return PinVerification.LockedOut(Instant.ofEpochMilli(until))
    }

    /** Troca o PIN depois de verificar o atual (sujeito ao mesmo limite de tentativas). */
    suspend fun changePin(currentPin: CharArray, newPin: CharArray): PinVerification {
        PinPolicy.validate(newPin)?.let { throw IllegalArgumentException("PIN inválido: $it") }
        val result = verifyPin(currentPin)
        if (result == PinVerification.Success) configurePin(newPin)
        return result
    }

    private fun computeVerifier(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray? {
        val derived = PinHasher.derive(pin, salt, iterations)
        return try {
            pepper.apply(derived)
        } finally {
            derived.fill(0)
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        private const val BASE_LOCKOUT_MILLIS = 30_000L
        private const val MAX_LOCKOUT_MILLIS = 60 * 60_000L

        /** Duração do bloqueio depois da falha número [failures] (≥ [MAX_ATTEMPTS]). */
        fun lockoutDurationMillis(failures: Int): Long {
            val doublings = (failures - MAX_ATTEMPTS).coerceIn(0, 20)
            return (BASE_LOCKOUT_MILLIS shl doublings).coerceAtMost(MAX_LOCKOUT_MILLIS)
        }

        private val encoder: Base64.Encoder = Base64.getEncoder()
        private val decoder: Base64.Decoder = Base64.getDecoder()
    }
}
