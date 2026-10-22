package pt.vcc.financeapp.core.security.testing

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import androidx.fragment.app.FragmentActivity
import pt.vcc.financeapp.core.security.BiometricAuthenticator
import pt.vcc.financeapp.core.security.BiometricAvailability
import pt.vcc.financeapp.core.security.BiometricPromptTexts
import pt.vcc.financeapp.core.security.BiometricResult
import pt.vcc.financeapp.core.security.DatabaseKeyProvider
import pt.vcc.financeapp.core.security.DatabaseKeyUnavailableException
import pt.vcc.financeapp.core.security.FieldCipher
import pt.vcc.financeapp.core.security.PinPepper
import pt.vcc.financeapp.core.security.SecurityPreferences
import pt.vcc.financeapp.core.security.SecurityStore

class InMemorySecurityStore(initial: SecurityPreferences = SecurityPreferences()) : SecurityStore {
    private val state = MutableStateFlow(initial)
    override val preferences: Flow<SecurityPreferences> = state

    val value: SecurityPreferences get() = state.value

    override suspend fun update(transform: (SecurityPreferences) -> SecurityPreferences) {
        state.update(transform)
    }
}

/** Pepper determinístico (XOR com um byte por chave gerada); `null` enquanto não houver chave. */
class FakePinPepper : PinPepper {
    private var key: Byte? = null
    private var generation: Byte = 0

    override fun createKey() {
        generation++
        key = generation
    }

    override fun apply(input: ByteArray): ByteArray? {
        val k = key ?: return null
        return ByteArray(input.size) { (input[it].toInt() xor k.toInt()).toByte() }
    }

    override fun deleteKey() {
        key = null
    }
}

/** Relógio controlado pelos testes. */
class MutableClock(var instant: Instant = Instant.parse("2026-10-21T10:00:00Z")) : Clock() {
    fun advance(duration: Duration) {
        instant = instant.plus(duration)
    }

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = instant
}
class FakeBiometricAuthenticator(
    var availability: BiometricAvailability = BiometricAvailability.AVAILABLE,
    var nextResult: BiometricResult = BiometricResult.Success,
) : BiometricAuthenticator {
    var disabled = false
        private set

    override fun availability(): BiometricAvailability = availability

    override fun enroll(activity: FragmentActivity, texts: BiometricPromptTexts, onResult: (BiometricResult) -> Unit) {
        disabled = false
        onResult(nextResult)
    }

    override fun authenticate(activity: FragmentActivity, texts: BiometricPromptTexts, onResult: (BiometricResult) -> Unit) {
        onResult(nextResult)
    }

    override fun disable() {
        disabled = true
    }
}

/** [FieldCipher] reversível e determinístico, só para testes. */
class FakeFieldCipher : FieldCipher {
    var failDecrypt = false

    override fun encrypt(plainText: String): String = "enc:" + plainText.reversed()

    override fun decrypt(cipherText: String): String? =
        if (failDecrypt || !cipherText.startsWith("enc:")) null else cipherText.removePrefix("enc:").reversed()

    override fun deleteKey() {
        failDecrypt = true
    }
}

/** [DatabaseKeyProvider] em memória; [usable] simula uma chave do Keystore perdida. */
class FakeDatabaseKeyProvider(var hasKey: Boolean = true, var usable: Boolean = true) : DatabaseKeyProvider {
    override fun hasKey(): Boolean = hasKey

    override fun getOrCreateKey(): ByteArray {
        if (hasKey && !usable) throw DatabaseKeyUnavailableException("fake")
        hasKey = true
        usable = true
        return ByteArray(32) { it.toByte() }
    }

    override fun isKeyUsable(): Boolean = hasKey && usable

    override fun deleteKey() {
        hasKey = false
        usable = true
    }
}