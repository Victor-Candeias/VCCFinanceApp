package pt.vcc.financeapp.core.security.testing

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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