package pt.vcc.financeapp.core.security

import javax.inject.Inject

/**
 * Apaga todo o estado de segurança: PIN, tentativas, definições e as chaves do Keystore (pepper
 * do PIN, biometria, cifra de campos e chave da base de dados). Usado só no reset da app.
 */
class SecurityReset @Inject constructor(
    private val store: SecurityStore,
    private val pinPepper: PinPepper,
    private val biometricAuthenticator: BiometricAuthenticator,
    private val fieldCipher: FieldCipher,
    private val databaseKeyProvider: DatabaseKeyProvider,
) {
    suspend fun eraseAll() {
        store.update { SecurityPreferences() }
        pinPepper.deleteKey()
        biometricAuthenticator.disable()
        fieldCipher.deleteKey()
        databaseKeyProvider.deleteKey()
    }
}
