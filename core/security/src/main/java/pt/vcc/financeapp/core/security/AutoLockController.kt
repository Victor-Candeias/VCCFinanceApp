package pt.vcc.financeapp.core.security

import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Política de bloqueio automático (README §10), com o tempo de [SecurityPreferences.autoLockTimeoutSeconds]:
 * - em segundo plano, a sessão bloqueia ao voltar se tiver passado esse tempo (0 = sempre);
 * - em primeiro plano, bloqueia sem interação durante esse tempo, com um mínimo de
 *   [MIN_FOREGROUND_TIMEOUT_MILLIS] para "imediatamente" não bloquear enquanto se lê.
 *
 * Os métodos são síncronos para o bloqueio ser aplicado em `onStart`, antes de a app voltar a
 * desenhar dados; o tempo é mantido atualizado por [updateTimeout].
 */
@Singleton
class AutoLockController @Inject constructor(
    private val sessionManager: SessionManager,
    private val clock: Clock,
) {
    @Volatile
    private var timeoutMillis = SecurityPreferences.DEFAULT_AUTO_LOCK_SECONDS * 1_000L
    private var lastInteractionMillis = clock.millis()
    private var backgroundSinceMillis: Long? = null

    fun updateTimeout(seconds: Int) {
        timeoutMillis = seconds * 1_000L
    }

    fun onUserInteraction() {
        lastInteractionMillis = clock.millis()
    }

    fun onBackground() {
        backgroundSinceMillis = clock.millis()
        if (timeoutMillis == 0L) sessionManager.lock()
    }

    fun onForeground() {
        val since = backgroundSinceMillis ?: return
        backgroundSinceMillis = null
        if (clock.millis() - since >= timeoutMillis) sessionManager.lock()
        lastInteractionMillis = clock.millis()
    }

    fun checkInactivity() {
        // Enquanto está bloqueada não conta: ao desbloquear o tempo começa do zero.
        if (sessionManager.state.value != SessionState.UNLOCKED) {
            lastInteractionMillis = clock.millis()
            return
        }
        if (clock.millis() - lastInteractionMillis >= maxOf(timeoutMillis, MIN_FOREGROUND_TIMEOUT_MILLIS)) {
            sessionManager.lock()
        }
    }

    companion object {
        const val MIN_FOREGROUND_TIMEOUT_MILLIS = 30_000L
    }
}
