package pt.vcc.financeapp.core.data.recovery

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pt.vcc.financeapp.core.database.DatabaseStorage
import pt.vcc.financeapp.core.security.PinManager
import pt.vcc.financeapp.core.security.SecurityReset

/**
 * Cenários de recuperação (README §10): deteta chaves perdidas (Keystore limpo, ficheiro da chave
 * da base de dados em falta) e apaga todos os dados locais quando o utilizador o confirma, por
 * exemplo depois de esquecer o PIN. Sem backup não há forma de recuperar os dados cifrados.
 */
@Singleton
class AppRecoveryManager(
    private val pinManager: PinManager,
    private val databaseStorage: DatabaseStorage,
    private val securityReset: SecurityReset,
    private val dispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(pinManager: PinManager, databaseStorage: DatabaseStorage, securityReset: SecurityReset) :
        this(pinManager, databaseStorage, securityReset, Dispatchers.IO)

    /** `true` se o PIN ou os dados já não puderem ser verificados/lidos com as chaves disponíveis. */
    suspend fun needsRecovery(): Boolean = withContext(dispatcher) {
        !pinManager.isPinKeyAvailable() || !databaseStorage.isReadable()
    }

    /**
     * Apaga a base de dados e só depois o estado de segurança, para que uma interrupção a meio
     * nunca deixe dados sem chave. A app deve ser reiniciada a seguir (a base de dados fica fechada).
     */
    suspend fun eraseAllData() = withContext(dispatcher) {
        databaseStorage.deleteDatabase()
        securityReset.eraseAll()
    }
}
