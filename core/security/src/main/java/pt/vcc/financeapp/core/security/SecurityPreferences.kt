package pt.vcc.financeapp.core.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pt.vcc.financeapp.core.security.di.SecurityDataStore

/**
 * Estado persistido da segurança. Guarda só o verificador do PIN (Base64), nunca o PIN.
 */
data class SecurityPreferences(
    val pinVerifier: String? = null,
    val pinSalt: String? = null,
    val pinIterations: Int = 0,
    /** Tentativas falhadas desde o último PIN correto. */
    val failedAttempts: Int = 0,
    /** Fim do bloqueio atual (epoch millis); 0 sem bloqueio. */
    val lockedUntilMillis: Long = 0,
    val biometricEnabled: Boolean = false,
    /** Tempo em segundo plano ou sem interação até a app bloquear (ver [AutoLockController]). */
    val autoLockTimeoutSeconds: Int = DEFAULT_AUTO_LOCK_SECONDS,
) {
    val isPinConfigured: Boolean get() = pinVerifier != null && pinSalt != null && pinIterations > 0

    companion object {
        const val DEFAULT_AUTO_LOCK_SECONDS = 60
        val AUTO_LOCK_OPTIONS_SECONDS = listOf(0, 30, 60, 300, 900)
    }
}

/** Acesso atómico ao [SecurityPreferences]. */
interface SecurityStore {
    val preferences: Flow<SecurityPreferences>

    suspend fun current(): SecurityPreferences = preferences.first()

    suspend fun update(transform: (SecurityPreferences) -> SecurityPreferences)
}

/** [SecurityStore] num DataStore de preferências próprio (`security.preferences_pb`). */
internal class DataStoreSecurityStore @Inject constructor(
    @SecurityDataStore private val dataStore: DataStore<Preferences>,
) : SecurityStore {
    override val preferences: Flow<SecurityPreferences> = dataStore.data.map { it.toSecurityPreferences() }

    override suspend fun update(transform: (SecurityPreferences) -> SecurityPreferences) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toSecurityPreferences())
            prefs.setOrRemove(PIN_VERIFIER, updated.pinVerifier)
            prefs.setOrRemove(PIN_SALT, updated.pinSalt)
            prefs[PIN_ITERATIONS] = updated.pinIterations
            prefs[FAILED_ATTEMPTS] = updated.failedAttempts
            prefs[LOCKED_UNTIL] = updated.lockedUntilMillis
            prefs[BIOMETRIC_ENABLED] = updated.biometricEnabled
            prefs[AUTO_LOCK_SECONDS] = updated.autoLockTimeoutSeconds
        }
    }

    private fun Preferences.toSecurityPreferences() = SecurityPreferences(
        pinVerifier = this[PIN_VERIFIER],
        pinSalt = this[PIN_SALT],
        pinIterations = this[PIN_ITERATIONS] ?: 0,
        failedAttempts = this[FAILED_ATTEMPTS] ?: 0,
        lockedUntilMillis = this[LOCKED_UNTIL] ?: 0,
        biometricEnabled = this[BIOMETRIC_ENABLED] ?: false,
        autoLockTimeoutSeconds = this[AUTO_LOCK_SECONDS] ?: SecurityPreferences.DEFAULT_AUTO_LOCK_SECONDS,
    )

    private fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }

    private companion object {
        val PIN_VERIFIER = stringPreferencesKey("pin_verifier")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_ITERATIONS = intPreferencesKey("pin_iterations")
        val FAILED_ATTEMPTS = intPreferencesKey("pin_failed_attempts")
        val LOCKED_UNTIL = longPreferencesKey("pin_locked_until")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val AUTO_LOCK_SECONDS = intPreferencesKey("auto_lock_seconds")
    }
}
