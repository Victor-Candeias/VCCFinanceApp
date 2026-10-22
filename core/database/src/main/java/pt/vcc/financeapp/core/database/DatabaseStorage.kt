package pt.vcc.financeapp.core.database

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import pt.vcc.financeapp.core.security.DatabaseKeyProvider

/** Estado e remoção dos ficheiros da base de dados, para os cenários de recuperação (README §10). */
interface DatabaseStorage {
    /** `false` se existir uma base de dados cifrada cuja chave já não se consegue obter. */
    fun isReadable(): Boolean

    /** Fecha a base de dados e apaga os ficheiros (incluindo WAL, SHM e conversões a meio). */
    fun deleteDatabase()
}

@Singleton
class AndroidDatabaseStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyProvider: DatabaseKeyProvider,
    private val database: dagger.Lazy<VCCFinanceDatabase>,
) : DatabaseStorage {
    private val file: File get() = context.getDatabasePath(VCCFinanceDatabase.NAME)

    override fun isReadable(): Boolean = DatabaseAccess.isReadable(file, keyProvider)

    override fun deleteDatabase() {
        runCatching { database.get().close() }
        context.deleteDatabase(VCCFinanceDatabase.NAME)
        File(file.path + PlaintextDatabaseEncrypter.TEMP_SUFFIX).delete()
    }
}

/** Regras para decidir se a base de dados pode ser aberta com a chave disponível. */
internal object DatabaseAccess {
    fun isReadable(file: File, keyProvider: DatabaseKeyProvider): Boolean =
        !exists(file) || PlaintextDatabaseEncrypter.isPlaintext(file) || keyProvider.isKeyUsable()

    /**
     * Base de dados cifrada sem o ficheiro da chave: gerar uma chave nova não a abriria e apagaria
     * a hipótese de recuperar a original, por isso a abertura falha em vez de criar outra chave.
     */
    fun isOrphaned(file: File, keyProvider: DatabaseKeyProvider): Boolean =
        exists(file) && !PlaintextDatabaseEncrypter.isPlaintext(file) && !keyProvider.hasKey()

    private fun exists(file: File) = file.isFile && file.length() > 0
}
