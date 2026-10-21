package pt.vcc.financeapp.core.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
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
) {
    val isPinConfigured: Boolean get() = pinVerifier != null && pinSalt != null && pinIterations > 0
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
        }
    }

    private fun Preferences.toSecurityPreferences() = SecurityPreferences(
        pinVerifier = this[PIN_VERIFIER],
        pinSalt = this[PIN_SALT],
        pinIterations = this[PIN_ITERATIONS] ?: 0,
        failedAttempts = this[FAILED_ATTEMPTS] ?: 0,
        lockedUntilMillis = this[LOCKED_UNTIL] ?: 0,
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
    }
}
