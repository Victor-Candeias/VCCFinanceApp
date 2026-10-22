package pt.vcc.financeapp.core.data.backup

import android.database.SQLException
import java.io.IOException
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pt.vcc.financeapp.core.data.exporter.DocumentWriter
import pt.vcc.financeapp.core.data.importer.DocumentReader
import pt.vcc.financeapp.core.data.importer.ImportParseException
import pt.vcc.financeapp.core.database.VCCFinanceDatabase
import pt.vcc.financeapp.core.database.backup.DatabaseBackupStore
import pt.vcc.financeapp.core.database.backup.DatabaseSnapshot
import pt.vcc.financeapp.core.database.backup.IncompatibleBackupException

enum class BackupError {
    NOT_A_BACKUP,
    WRONG_PASSWORD,
    UNSUPPORTED_VERSION,
    INCOMPATIBLE,
    TOO_LARGE,
    READ_FAILED,
    WRITE_FAILED,
    RESTORE_FAILED,
}

class BackupException(val error: BackupError, cause: Throwable? = null) : Exception(error.name, cause)

data class BackupSummary(
    val createdAt: Instant,
    val accountCount: Int,
    val transactionCount: Int,
    val categoryCount: Int,
)

/** Backup já decifrado e validado, pronto a restaurar depois da confirmação do utilizador. */
class OpenedBackup internal constructor(val summary: BackupSummary, internal val snapshot: DatabaseSnapshot)

sealed interface BackupResult {
    data class Created(val summary: BackupSummary) : BackupResult
    data class Opened(val backup: OpenedBackup) : BackupResult
    data class Restored(val summary: BackupSummary) : BackupResult
    data class Failed(val error: BackupError) : BackupResult
}

/**
 * Cria e restaura backups integrais cifrados com palavra-passe (README §10). O restauro tem dois
 * passos — [open] decifra e valida sem tocar nos dados; [restore] substitui tudo numa transação.
 */
class BackupManager(
    private val store: DatabaseBackupStore,
    private val reader: DocumentReader,
    private val writer: DocumentWriter,
    private val clock: Clock,
    private val dispatcher: CoroutineDispatcher,
    private val iterations: Int,
) {
    @Inject
    constructor(store: DatabaseBackupStore, reader: DocumentReader, writer: DocumentWriter, clock: Clock) :
        this(store, reader, writer, clock, Dispatchers.Default, BackupCrypto.DEFAULT_ITERATIONS)

    suspend fun create(uri: String, password: CharArray): BackupResult = try {
        val payload = BackupPayload(clock.instant(), store.snapshot())
        val encrypted = withContext(dispatcher) { BackupCrypto.encrypt(BackupCodec.encode(payload), password, iterations) }
        writer.write(uri) { it.write(encrypted) }
        BackupResult.Created(summaryOf(payload))
    } catch (e: IOException) {
        BackupResult.Failed(BackupError.WRITE_FAILED)
    } catch (e: SecurityException) {
        BackupResult.Failed(BackupError.WRITE_FAILED)
    }

    suspend fun open(uri: String, password: CharArray): BackupResult = try {
        val document = reader.read(uri)
        withContext(dispatcher) {
            val payload = BackupCodec.decode(BackupCrypto.decrypt(document.bytes, password))
            if (payload.snapshot.schemaVersion > SUPPORTED_SCHEMA) throw BackupException(BackupError.INCOMPATIBLE)
            BackupResult.Opened(OpenedBackup(summaryOf(payload), payload.snapshot))
        }
    } catch (e: BackupException) {
        BackupResult.Failed(e.error)
    } catch (e: ImportParseException) {
        BackupResult.Failed(
            if (e.reason == ImportParseException.Reason.TOO_LARGE) BackupError.TOO_LARGE else BackupError.READ_FAILED,
        )
    }

    suspend fun restore(backup: OpenedBackup): BackupResult = try {
        store.restore(backup.snapshot)
        BackupResult.Restored(backup.summary)
    } catch (e: IncompatibleBackupException) {
        BackupResult.Failed(BackupError.INCOMPATIBLE)
    } catch (e: SQLException) {
        BackupResult.Failed(BackupError.RESTORE_FAILED)
    } catch (e: IllegalArgumentException) {
        BackupResult.Failed(BackupError.RESTORE_FAILED)
    }

    private fun summaryOf(payload: BackupPayload) = BackupSummary(
        createdAt = payload.createdAt,
        accountCount = payload.snapshot.rowCount("accounts"),
        transactionCount = payload.snapshot.rowCount("transactions"),
        categoryCount = payload.snapshot.rowCount("categories"),
    )

    private companion object {
        const val SUPPORTED_SCHEMA = VCCFinanceDatabase.VERSION
    }
}
