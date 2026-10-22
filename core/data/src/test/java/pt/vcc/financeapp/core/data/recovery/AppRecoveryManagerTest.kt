package pt.vcc.financeapp.core.data.recovery

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.financeapp.core.data.testing.FakeDatabaseStorage
import pt.vcc.financeapp.core.security.PinManager
import pt.vcc.financeapp.core.security.PinVerification
import pt.vcc.financeapp.core.security.SecurityPreferences
import pt.vcc.financeapp.core.security.SecurityReset
import pt.vcc.financeapp.core.security.testing.FakeBiometricAuthenticator
import pt.vcc.financeapp.core.security.testing.FakeDatabaseKeyProvider
import pt.vcc.financeapp.core.security.testing.FakeFieldCipher
import pt.vcc.financeapp.core.security.testing.FakePinPepper
import pt.vcc.financeapp.core.security.testing.InMemorySecurityStore
import pt.vcc.financeapp.core.security.testing.MutableClock

class AppRecoveryManagerTest {
    private val store = InMemorySecurityStore()
    private val pepper = FakePinPepper()
    private val biometric = FakeBiometricAuthenticator()
    private val fieldCipher = FakeFieldCipher()
    private val databaseKey = FakeDatabaseKeyProvider()
    private val databaseStorage = FakeDatabaseStorage()

    private fun TestScope.setUp(): Pair<PinManager, AppRecoveryManager> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val pinManager = PinManager(store, pepper, MutableClock(), dispatcher, iterations = 1_000)
        val reset = SecurityReset(store, pepper, biometric, fieldCipher, databaseKey)
        return pinManager to AppRecoveryManager(pinManager, databaseStorage, reset, dispatcher)
    }

    @Test
    fun healthyInstallDoesNotNeedRecovery() = runTest {
        val (pinManager, recovery) = setUp()
        assertFalse(recovery.needsRecovery())

        pinManager.configurePin("1397".toCharArray())

        assertFalse(recovery.needsRecovery())
    }

    @Test
    fun lostPinKeyOrUnreadableDatabaseNeedsRecovery() = runTest {
        val (pinManager, recovery) = setUp()
        pinManager.configurePin("1397".toCharArray())

        pepper.deleteKey()
        assertTrue(recovery.needsRecovery())

        pinManager.configurePin("1397".toCharArray())
        databaseStorage.readable = false
        assertTrue(recovery.needsRecovery())
    }

    @Test
    fun eraseAllDataRemovesDatabaseKeysAndSettings() = runTest {
        val (pinManager, recovery) = setUp()
        pinManager.configurePin("1397".toCharArray())
        store.update { it.copy(biometricEnabled = true, autoLockTimeoutSeconds = 30, failedAttempts = 3) }

        recovery.eraseAllData()

        assertTrue(databaseStorage.deleted)
        assertEquals(SecurityPreferences(), store.value)
        assertFalse(databaseKey.hasKey())
        assertTrue(biometric.disabled)
        assertTrue(fieldCipher.failDecrypt)
        assertFalse(recovery.needsRecovery())
        // O PIN antigo deixa de existir: o próximo arranque pede um PIN novo.
        assertEquals(PinVerification.Wrong(remainingAttempts = 0), pinManager.verifyPin("1397".toCharArray()))
    }
}
