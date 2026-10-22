package pt.vcc.financeapp.core.security

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** Chave da base de dados cifrada (SQLCipher). */
interface DatabaseKeyProvider {
    /** `true` se já existir uma chave guardada (a base de dados pode estar cifrada com ela). */
    fun hasKey(): Boolean

    /**
     * Chave de 32 bytes, criada na primeira chamada. Lança [DatabaseKeyUnavailableException] se a
     * chave guardada não puder ser decifrada (por exemplo, chave do Keystore apagada).
     */
    fun getOrCreateKey(): ByteArray
}

class DatabaseKeyUnavailableException(message: String) : IllegalStateException(message)

/**
 * Chave aleatória guardada em `no_backup/` embrulhada (AES-GCM) por uma chave do Keystore, para
 * nunca ficar em claro no armazenamento nem ser incluída em backups do sistema.
 */
@Singleton
internal class KeystoreDatabaseKeyProvider @Inject constructor(
    @ApplicationContext context: Context,
) : DatabaseKeyProvider {
    private val file = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))
    private val wrapper = KeystoreAesGcm(ALIAS)

    override fun hasKey(): Boolean = file.baseFile.exists()

    @Synchronized
    override fun getOrCreateKey(): ByteArray {
        if (hasKey()) {
            return wrapper.decrypt(file.readFully())
                ?: throw DatabaseKeyUnavailableException("Não foi possível decifrar a chave da base de dados")
        }
        val key = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        val wrapped = wrapper.encrypt(key)
        val out = file.startWrite()
        try {
            out.write(wrapped)
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
        return key
    }

    private companion object {
        const val FILE_NAME = "database.key"
        const val ALIAS = "vccfinance_database_key"
        const val KEY_BYTES = 32
    }
}
