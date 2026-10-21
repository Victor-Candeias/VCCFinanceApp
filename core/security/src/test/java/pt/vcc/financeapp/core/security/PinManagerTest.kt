package pt.vcc.financeapp.core.security

import java.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.financeapp.core.security.testing.FakePinPepper
import pt.vcc.financeapp.core.security.testing.InMemorySecurityStore
import pt.vcc.financeapp.core.security.testing.MutableClock

class PinManagerTest {
    private val store = InMemorySecurityStore()
    private val pepper = FakePinPepper()
    private val clock = MutableClock()

    private fun manager(dispatcher: CoroutineDispatcher) = PinManager(store, pepper, clock, dispatcher, iterations = 1_000)

    @Test
    fun configuredPinVerifiesAndOtherPinsDoNot() = runTest {
        val manager = manager(StandardTestDispatcher(testScheduler))

        manager.configurePin("2580".toCharArray())

        assertEquals(PinVerification.Success, manager.verifyPin("2580".toCharArray()))
        assertNotSuccess(manager.verifyPin("2581".toCharArray()))
        assertFalse(store.value.pinVerifier!!.contains("2580"))
        assertEquals(1_000, store.value.pinIterations)
    }

    @Test
    fun reconfiguringReplacesSaltAndOldPin() = runTest {
        val manager = manager(StandardTestDispatcher(testScheduler))
        manager.configurePin("2580".toCharArray())
        val firstSalt = store.value.pinSalt

        manager.configurePin("1397".toCharArray())

        assertNotNull(store.value.pinSalt)
        assertFalse(firstSalt == store.value.pinSalt)
        assertEquals(PinVerification.Success, manager.verifyPin("1397".toCharArray()))
        assertNotSuccess(manager.verifyPin("2580".toCharArray()))
    }

    @Test
    fun verificationFailsWithoutPinOrWithoutKeystoreKey() = runTest {
        val manager = manager(StandardTestDispatcher(testScheduler))
        assertNotSuccess(manager.verifyPin("2580".toCharArray()))

        manager.configurePin("2580".toCharArray())
        pepper.deleteKey()

        assertNotSuccess(manager.verifyPin("2580".toCharArray()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTrivialPin() = runTest {
        manager(StandardTestDispatcher(testScheduler)).configurePin("1234".toCharArray())
    }

    private fun assertNotSuccess(result: PinVerification) {
        assertTrue("esperava falha, obteve $result", result != PinVerification.Success)
    }

    @Test
    fun wrongAttemptsCountDownThenLockProgressively() = runTest {
        val manager = manager(StandardTestDispatcher(testScheduler))
        manager.configurePin("2580".toCharArray())

        (4 downTo 1).forEach { remaining ->
            assertEquals(PinVerification.Wrong(remaining), manager.verifyPin("1397".toCharArray()))
        }
        val firstLock = manager.verifyPin("1397".toCharArray()) as PinVerification.LockedOut
        assertEquals(clock.instant().plusSeconds(30), firstLock.until)

        // Durante o bloqueio nem o PIN certo é verificado.
        assertEquals(firstLock, manager.verifyPin("2580".toCharArray()))

        clock.advance(Duration.ofSeconds(31))
        val secondLock = manager.verifyPin("1397".toCharArray()) as PinVerification.LockedOut
        assertEquals(clock.instant().plusSeconds(60), secondLock.until)

        clock.advance(Duration.ofSeconds(61))
        assertEquals(PinVerification.Success, manager.verifyPin("2580".toCharArray()))
        assertEquals(0, store.value.failedAttempts)
        assertEquals(PinVerification.Wrong(4), manager.verifyPin("1397".toCharArray()))
    }

    @Test
    fun lockoutDurationDoublesUpToOneHour() {
        assertEquals(30_000L, PinManager.lockoutDurationMillis(5))
        assertEquals(120_000L, PinManager.lockoutDurationMillis(7))
        assertEquals(3_600_000L, PinManager.lockoutDurationMillis(40))
    }

    @Test
    fun changePinRequiresTheCurrentPin() = runTest {
        val manager = manager(StandardTestDispatcher(testScheduler))
        manager.configurePin("2580".toCharArray())

        assertEquals(PinVerification.Wrong(4), manager.changePin("1111".toCharArray(), "1397".toCharArray()))
        assertEquals(PinVerification.Success, manager.changePin("2580".toCharArray(), "1397".toCharArray()))

        assertEquals(PinVerification.Success, manager.verifyPin("1397".toCharArray()))
        assertNotSuccess(manager.verifyPin("2580".toCharArray()))
    }

    @Test
    fun sessionStartsLockedOnlyWhenPinExists() {        val withPin = SessionManager().apply { initialize(pinConfigured = true) }
        val withoutPin = SessionManager().apply { initialize(pinConfigured = false) }
        assertEquals(SessionState.LOCKED, withPin.state.value)
        assertEquals(SessionState.SETUP_REQUIRED, withoutPin.state.value)

        withPin.unlock()
        withPin.initialize(pinConfigured = true)
        assertEquals(SessionState.UNLOCKED, withPin.state.value)
        withPin.lock()
        assertEquals(SessionState.LOCKED, withPin.state.value)
    }
}
