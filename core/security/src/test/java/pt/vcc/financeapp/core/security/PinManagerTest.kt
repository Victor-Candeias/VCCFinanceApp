package pt.vcc.financeapp.core.security

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

class PinManagerTest {
    private val store = InMemorySecurityStore()
    private val pepper = FakePinPepper()

    private fun manager(dispatcher: CoroutineDispatcher) = PinManager(store, pepper, dispatcher, iterations = 1_000)

    @Test
    fun configuredPinVerifiesAndOtherPinsDoNot() = runTest {
        val manager = manager(StandardTestDispatcher(testScheduler))

        manager.configurePin("2580".toCharArray())

        assertTrue(manager.verifyPin("2580".toCharArray()))
        assertFalse(manager.verifyPin("2581".toCharArray()))
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
        assertTrue(manager.verifyPin("1397".toCharArray()))
        assertFalse(manager.verifyPin("2580".toCharArray()))
    }

    @Test
    fun verificationFailsWithoutPinOrWithoutKeystoreKey() = runTest {
        val manager = manager(StandardTestDispatcher(testScheduler))
        assertFalse(manager.verifyPin("2580".toCharArray()))

        manager.configurePin("2580".toCharArray())
        pepper.deleteKey()

        assertFalse(manager.verifyPin("2580".toCharArray()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTrivialPin() = runTest {
        manager(StandardTestDispatcher(testScheduler)).configurePin("1234".toCharArray())
    }

    @Test
    fun sessionStartsLockedOnlyWhenPinExists() {
        val withPin = SessionManager().apply { initialize(pinConfigured = true) }
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
