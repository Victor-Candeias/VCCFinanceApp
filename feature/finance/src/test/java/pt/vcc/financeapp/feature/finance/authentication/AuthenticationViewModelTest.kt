package pt.vcc.financeapp.feature.finance.authentication

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.vcc.financeapp.core.security.PinManager
import pt.vcc.financeapp.core.security.SessionManager
import pt.vcc.financeapp.core.security.SessionState
import pt.vcc.financeapp.core.security.testing.FakePinPepper
import pt.vcc.financeapp.core.security.testing.InMemorySecurityStore

@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticationViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val store = InMemorySecurityStore()
    private val pinManager = PinManager(store, FakePinPepper(), dispatcher, iterations = 1_000)
    private val sessionManager = SessionManager()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun SetupPinViewModel.type(pin: String) {
        pin.forEach(::onDigit)
        onConfirm()
    }

    private fun UnlockViewModel.type(pin: String) {
        pin.forEach(::onDigit)
        onConfirm()
    }

    @Test
    fun onboardingCreatesPinAndUnlocksSession() = runTest(dispatcher) {
        sessionManager.initialize(pinConfigured = false)
        val vm = SetupPinViewModel(pinManager, sessionManager)
        assertEquals(SetupStep.WELCOME, vm.uiState.value.step)

        vm.start()
        vm.type("2580")
        assertEquals(SetupStep.CONFIRM, vm.uiState.value.step)
        vm.type("2580")

        assertTrue(store.value.isPinConfigured)
        assertEquals(SessionState.UNLOCKED, sessionManager.state.value)
    }

    @Test
    fun onboardingRejectsWeakPinAndMismatchedConfirmation() = runTest(dispatcher) {
        val vm = SetupPinViewModel(pinManager, sessionManager)
        vm.start()

        vm.type("1111")
        assertEquals(SetupPinError.TOO_SIMPLE, vm.uiState.value.error)
        vm.type("258")
        assertEquals(SetupPinError.TOO_SHORT, vm.uiState.value.error)

        vm.type("2580")
        vm.type("2581")
        assertEquals(SetupStep.CREATE, vm.uiState.value.step)
        assertEquals(SetupPinError.MISMATCH, vm.uiState.value.error)
        assertFalse(store.value.isPinConfigured)
    }

    @Test
    fun unlockAcceptsOnlyTheConfiguredPin() = runTest(dispatcher) {
        pinManager.configurePin("2580".toCharArray())
        sessionManager.initialize(pinConfigured = true)
        val vm = UnlockViewModel(pinManager, sessionManager)

        vm.type("2581")
        assertTrue(vm.uiState.value.wrongPin)
        assertEquals(0, vm.uiState.value.enteredDigits)
        assertEquals(SessionState.LOCKED, sessionManager.state.value)

        vm.type("2580")
        assertEquals(SessionState.UNLOCKED, sessionManager.state.value)
    }

    @Test
    fun sessionViewModelStartsLockedWhenPinExists() = runTest(dispatcher) {
        pinManager.configurePin("2580".toCharArray())

        val vm = SessionViewModel(pinManager, sessionManager)

        assertEquals(SessionState.LOCKED, vm.sessionState.value)
    }
}
