package pt.vcc.financeapp.feature.finance.authentication

import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
import pt.vcc.financeapp.core.security.PinVerification
import pt.vcc.financeapp.core.security.SessionManager
import pt.vcc.financeapp.core.security.SessionState
import pt.vcc.financeapp.core.security.testing.FakePinPepper
import pt.vcc.financeapp.core.security.testing.InMemorySecurityStore
import pt.vcc.financeapp.core.security.testing.MutableClock

@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticationViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val store = InMemorySecurityStore()
    private val clock = MutableClock()
    private val pinManager = PinManager(store, FakePinPepper(), clock, dispatcher, iterations = 1_000)
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

    private fun ChangePinViewModel.type(pin: String) {
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
        val vm = UnlockViewModel(pinManager, sessionManager, clock)

        vm.type("2581")
        assertEquals(4, vm.uiState.value.remainingAttempts)
        assertEquals(0, vm.uiState.value.enteredDigits)
        assertEquals(SessionState.LOCKED, sessionManager.state.value)

        vm.type("2580")
        assertEquals(SessionState.UNLOCKED, sessionManager.state.value)
    }

    @Test
    fun unlockShowsLockoutCountdownAndBlocksInput() = runTest(dispatcher) {
        pinManager.configurePin("2580".toCharArray())
        sessionManager.initialize(pinConfigured = true)
        val vm = UnlockViewModel(pinManager, sessionManager, clock)

        repeat(PinManager.MAX_ATTEMPTS) { vm.type("1397") }
        assertEquals(30L, vm.uiState.value.lockoutSeconds)
        assertFalse(vm.uiState.value.canEnterPin)
        vm.onDigit('2')
        assertEquals(0, vm.uiState.value.enteredDigits)

        // Um ViewModel novo (app reiniciada) continua bloqueado.
        assertEquals(30L, UnlockViewModel(pinManager, sessionManager, clock).uiState.value.lockoutSeconds)

        advanceTimeBy(30_001)
        clock.advance(Duration.ofSeconds(31))
        assertTrue(vm.uiState.value.canEnterPin)
        vm.type("2580")
        assertEquals(SessionState.UNLOCKED, sessionManager.state.value)
    }

    @Test
    fun changePinVerifiesCurrentAndStoresNewPin() = runTest(dispatcher) {
        pinManager.configurePin("2580".toCharArray())
        val vm = ChangePinViewModel(pinManager, sessionManager)

        vm.type("1111")
        assertEquals(ChangePinStep.CURRENT, vm.uiState.value.step)
        assertEquals(4, vm.uiState.value.remainingAttempts)

        vm.type("2580")
        assertEquals(ChangePinStep.NEW, vm.uiState.value.step)
        vm.type("1397")
        vm.type("1397")

        assertTrue(vm.uiState.value.isFinished)
        assertEquals(PinVerification.Success, pinManager.verifyPin("1397".toCharArray()))
    }

    @Test
    fun changePinLocksSessionWhenAttemptsRunOut() = runTest(dispatcher) {
        pinManager.configurePin("2580".toCharArray())
        sessionManager.initialize(pinConfigured = true)
        sessionManager.unlock()
        val vm = ChangePinViewModel(pinManager, sessionManager)

        repeat(PinManager.MAX_ATTEMPTS) { vm.type("1397") }

        assertEquals(SessionState.LOCKED, sessionManager.state.value)
    }

    @Test
    fun sessionViewModelStartsLockedWhenPinExists() = runTest(dispatcher) {        pinManager.configurePin("2580".toCharArray())

        val vm = SessionViewModel(pinManager, sessionManager)

        assertEquals(SessionState.LOCKED, vm.sessionState.value)
    }
}
