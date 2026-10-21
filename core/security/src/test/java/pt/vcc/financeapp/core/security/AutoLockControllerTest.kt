package pt.vcc.financeapp.core.security

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test
import pt.vcc.financeapp.core.security.testing.MutableClock

class AutoLockControllerTest {
    private val clock = MutableClock()
    private val session = SessionManager().apply {
        initialize(pinConfigured = true)
        unlock()
    }
    private val controller = AutoLockController(session, clock)

    @Test
    fun locksOnReturnOnlyAfterTimeoutInBackground() {
        controller.updateTimeout(60)

        controller.onBackground()
        clock.advance(Duration.ofSeconds(59))
        controller.onForeground()
        assertEquals(SessionState.UNLOCKED, session.state.value)

        controller.onBackground()
        clock.advance(Duration.ofSeconds(60))
        controller.onForeground()
        assertEquals(SessionState.LOCKED, session.state.value)
    }

    @Test
    fun immediateTimeoutLocksWhenGoingToBackground() {
        controller.updateTimeout(0)

        controller.onBackground()

        assertEquals(SessionState.LOCKED, session.state.value)
    }

    @Test
    fun locksAfterInactivityInForegroundWithMinimumTimeout() {
        controller.updateTimeout(0)
        clock.advance(Duration.ofSeconds(20))
        controller.checkInactivity()
        assertEquals(SessionState.UNLOCKED, session.state.value)

        controller.onUserInteraction()
        clock.advance(Duration.ofSeconds(29))
        controller.checkInactivity()
        assertEquals(SessionState.UNLOCKED, session.state.value)

        clock.advance(Duration.ofSeconds(1))
        controller.checkInactivity()
        assertEquals(SessionState.LOCKED, session.state.value)
    }

    @Test
    fun inactivityTimerRestartsAfterUnlock() {
        session.lock()
        clock.advance(Duration.ofMinutes(10))
        controller.checkInactivity()
        session.unlock()

        clock.advance(Duration.ofSeconds(59))
        controller.checkInactivity()

        assertEquals(SessionState.UNLOCKED, session.state.value)
    }
}
