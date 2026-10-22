package pt.vcc.financeapp.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportHelper
import pt.vcc.financeapp.core.security.DatabaseKeyProvider

/**
 * Abre a base de dados com SQLCipher (AES-256). A chave e a eventual conversão de uma base de
 * dados antiga em claro só acontecem na primeira abertura, que o Room faz fora da main thread.
 */
internal class EncryptedOpenHelperFactory(
    private val context: Context,
    private val keyProvider: DatabaseKeyProvider,
) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        DeferredOpenHelper(configuration) { writeAheadLogging ->
            loadNativeLibrary()
            val rawKey = SqlCipherKeys.rawKey(keyProvider.getOrCreateKey())
            configuration.name?.let { PlaintextDatabaseEncrypter.encryptIfNeeded(context.getDatabasePath(it), rawKey) }
            SupportHelper(configuration, rawKey, null, writeAheadLogging)
        }

    private companion object {
        @Volatile
        var libraryLoaded = false

        fun loadNativeLibrary() {
            if (!libraryLoaded) {
                System.loadLibrary("sqlcipher")
                libraryLoaded = true
            }
        }
    }
}

/** Formato de chave "raw" do SQLCipher (`x'…'`), que evita o PBKDF2 em cada abertura. */
internal object SqlCipherKeys {
    fun rawKey(key: ByteArray): ByteArray =
        ("x'" + key.joinToString("") { "%02x".format(it) } + "'").toByteArray(Charsets.US_ASCII)
}

/** Converte, uma única vez, a base de dados das versões anteriores (em claro) para SQLCipher. */
internal object PlaintextDatabaseEncrypter {
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun isPlaintext(file: File): Boolean {
        if (!file.isFile || file.length() < SQLITE_HEADER.size) return false
        val header = ByteArray(SQLITE_HEADER.size)
        file.inputStream().use { it.read(header) }
        return header.contentEquals(SQLITE_HEADER)
    }

    fun encryptIfNeeded(file: File, rawKey: ByteArray) {
        if (!isPlaintext(file)) return
        val encrypted = File(file.parentFile, "${file.name}.encrypting")
        encrypted.delete()
        val flags = SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY
        SQLiteDatabase.openDatabase(file.path, "", null, flags, null, null).use { db ->
            val version = db.version
            // Parâmetros ligados: a chave nunca faz parte do texto SQL (que o SQLite regista em erros).
            db.rawExecSQL("ATTACH DATABASE ? AS encrypted KEY ?", encrypted.path, String(rawKey, Charsets.US_ASCII))
            db.rawQuery("SELECT sqlcipher_export('encrypted')", emptyArray<String>()).use { it.moveToFirst() }
            db.rawExecSQL("PRAGMA encrypted.user_version = $version")
            db.rawExecSQL("DETACH DATABASE encrypted")
        }
        for (suffix in listOf("", "-wal", "-shm", "-journal")) File(file.path + suffix).delete()
        check(encrypted.renameTo(file)) { "Não foi possível substituir a base de dados pela versão cifrada" }
    }
}

/** Adia a criação do [SupportSQLiteOpenHelper] real até à primeira abertura da base de dados. */
private class DeferredOpenHelper(
    private val configuration: SupportSQLiteOpenHelper.Configuration,
    private val createDelegate: (writeAheadLogging: Boolean) -> SupportSQLiteOpenHelper,
) : SupportSQLiteOpenHelper {
    @Volatile
    private var delegate: SupportSQLiteOpenHelper? = null
    private var writeAheadLogging = false

    override val databaseName: String? get() = configuration.name

    @Synchronized
    override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
        writeAheadLogging = enabled
        delegate?.setWriteAheadLoggingEnabled(enabled)
    }

    override val writableDatabase: SupportSQLiteDatabase get() = delegate().writableDatabase

    override val readableDatabase: SupportSQLiteDatabase get() = delegate().readableDatabase

    @Synchronized
    override fun close() {
        delegate?.close()
    }

    @Synchronized
    private fun delegate(): SupportSQLiteOpenHelper = delegate ?: createDelegate(writeAheadLogging).also { delegate = it }
}
