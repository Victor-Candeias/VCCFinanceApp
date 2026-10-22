package pt.vcc.financeapp.core.data.backup

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.financeapp.core.data.exporter.DocumentWriter
import pt.vcc.financeapp.core.data.importer.DocumentReader
import pt.vcc.financeapp.core.data.importer.ImportDocument
import pt.vcc.financeapp.core.database.backup.BackupTable
import pt.vcc.financeapp.core.database.backup.DatabaseBackupStore
import pt.vcc.financeapp.core.database.backup.DatabaseSnapshot

class BackupTest {
    private val password = "correto-cavalo".toCharArray()

    private fun expectError(expected: BackupError, block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("esperava $expected, obteve $error", error is BackupException && error.error == expected)
    }

    @Test
    fun cryptoRoundTripsAndRejectsWrongPasswordOrTampering() {
        val plain = "dados financeiros".toByteArray()
        val blob = BackupCrypto.encrypt(plain, password, ITERATIONS)

        assertArrayEquals(plain, BackupCrypto.decrypt(blob, password))
        expectError(BackupError.WRONG_PASSWORD) { BackupCrypto.decrypt(blob, "outra-palavra".toCharArray()) }
        val tamperedBody = blob.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        expectError(BackupError.WRONG_PASSWORD) { BackupCrypto.decrypt(tamperedBody, password) }
        // O salt faz parte do cabeçalho autenticado: alterá-lo também invalida o ficheiro.
        val tamperedHeader = blob.copyOf().also { it[12] = (it[12] + 1).toByte() }
        expectError(BackupError.WRONG_PASSWORD) { BackupCrypto.decrypt(tamperedHeader, password) }
        expectError(BackupError.NOT_A_BACKUP) { BackupCrypto.decrypt("Data;Valor\n".toByteArray(), password) }
        val newerFormat = blob.copyOf().also { it[6] = 9 }
        expectError(BackupError.UNSUPPORTED_VERSION) { BackupCrypto.decrypt(newerFormat, password) }
    }

    @Test
    fun createOpenAndRestoreKeepAllTypedValues() = runTest {
        val original = DatabaseSnapshot(
            schemaVersion = 3,
            tables = listOf(
                BackupTable("accounts", listOf("id", "name", "iban"), listOf(listOf("a1", "Conta \"à ordem\"", "PT50000201231234567890154"))),
                BackupTable(
                    "transactions",
                    listOf("id", "amountMinor", "rate", "note", "blob"),
                    listOf(
                        listOf("t1", -12_345L, 1.5, null, byteArrayOf(1, 2, 3)),
                        listOf("t2", Long.MAX_VALUE, 2.0, "café; 1,5", null),
                    ),
                ),
                BackupTable("categories", listOf("id"), emptyList()),
            ),
        )
        val store = FakeStore(original)
        val files = FakeFiles()
        val manager = BackupManager(store, files, files, CLOCK, Dispatchers.Unconfined, ITERATIONS)

        val created = manager.create("content://backup", password) as BackupResult.Created
        assertEquals(BackupSummary(CLOCK.instant(), 1, 2, 0), created.summary)

        assertEquals(BackupResult.Failed(BackupError.WRONG_PASSWORD), manager.open("content://backup", "errada!!".toCharArray()))
        assertEquals(null, store.restored)

        val opened = manager.open("content://backup", password) as BackupResult.Opened
        assertEquals(created.summary, opened.backup.summary)
        assertEquals(BackupResult.Restored(created.summary), manager.restore(opened.backup))

        val restored = requireNotNull(store.restored)
        assertEquals(3, restored.schemaVersion)
        assertEquals(original.tables.map { it.name to it.columns }, restored.tables.map { it.name to it.columns })
        val rows = restored.tables[1].rows
        assertEquals(listOf("t1", -12_345L, 1.5, null), rows[0].take(4))
        assertArrayEquals(byteArrayOf(1, 2, 3), rows[0][4] as ByteArray)
        assertEquals(listOf("t2", Long.MAX_VALUE, 2.0, "café; 1,5", null), rows[1])
        assertEquals(original.tables[0].rows, restored.tables[0].rows)
    }

    @Test
    fun rejectsBackupsFromNewerSchemas() = runTest {
        val files = FakeFiles()
        val newer = BackupManager(FakeStore(DatabaseSnapshot(99, emptyList())), files, files, CLOCK, Dispatchers.Unconfined, ITERATIONS)
        newer.create("content://backup", password)

        assertEquals(BackupResult.Failed(BackupError.INCOMPATIBLE), newer.open("content://backup", password))
    }

    private class FakeStore(private val snapshot: DatabaseSnapshot) : DatabaseBackupStore {
        var restored: DatabaseSnapshot? = null

        override suspend fun snapshot() = snapshot

        override suspend fun restore(snapshot: DatabaseSnapshot) {
            restored = snapshot
        }
    }

    private class FakeFiles : DocumentReader, DocumentWriter {
        private val files = mutableMapOf<String, ByteArray>()

        override suspend fun read(uri: String) = ImportDocument("backup.vccbackup", files.getValue(uri))

        override suspend fun write(uri: String, block: (OutputStream) -> Unit) {
            files[uri] = ByteArrayOutputStream().also(block).toByteArray()
        }
    }

    private companion object {
        const val ITERATIONS = 100_000
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-22T10:15:30Z"), ZoneOffset.UTC)
    }
}
