package pt.vcc.financeapp.feature.finance.backup

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.vcc.financeapp.core.data.backup.BackupError
import pt.vcc.financeapp.core.data.backup.BackupManager
import pt.vcc.financeapp.core.data.exporter.DocumentWriter
import pt.vcc.financeapp.core.data.importer.DocumentReader
import pt.vcc.financeapp.core.data.importer.ImportDocument
import pt.vcc.financeapp.core.database.backup.BackupTable
import pt.vcc.financeapp.core.database.backup.DatabaseBackupStore
import pt.vcc.financeapp.core.database.backup.DatabaseSnapshot

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneOffset.UTC)
    private val snapshot = DatabaseSnapshot(
        3,
        listOf(
            BackupTable("accounts", listOf("id"), listOf(listOf("a1"))),
            BackupTable("transactions", listOf("id"), listOf(listOf("t1"), listOf("t2"))),
        ),
    )
    private var restored: DatabaseSnapshot? = null
    private val store = object : DatabaseBackupStore {
        override suspend fun snapshot() = snapshot

        override suspend fun restore(snapshot: DatabaseSnapshot) {
            restored = snapshot
        }
    }
    private var file = ByteArray(0)
    private val files = object : DocumentReader, DocumentWriter {
        override suspend fun read(uri: String) = ImportDocument("backup.vccbackup", file)

        override suspend fun write(uri: String, block: (OutputStream) -> Unit) {
            file = ByteArrayOutputStream().also(block).toByteArray()
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun createsOnlyWithConfirmedPasswordAndRestoresAfterConfirmation() {
        val vm = BackupViewModel(BackupManager(store, files, files, clock, Dispatchers.Unconfined, 100_000), clock)
        assertEquals("vccfinance-backup-2026-10-22.vccbackup", vm.uiState.value.fileName)

        vm.updatePassword("curta")
        assertEquals(BackupPasswordIssue.TOO_SHORT, vm.uiState.value.passwordIssue)
        vm.updatePassword("palavra-segura")
        vm.updateConfirmation("palavra-segur")
        assertEquals(BackupPasswordIssue.MISMATCH, vm.uiState.value.passwordIssue)
        vm.create("content://ignored")
        assertEquals(0, file.size)

        vm.updateConfirmation("palavra-segura")
        vm.create("content://backup")
        val created = vm.uiState.value.message as BackupMessage.Created
        assertEquals(2, created.summary.transactionCount)
        assertEquals("", vm.uiState.value.password)

        vm.selectRestoreFile("content://backup")
        vm.updateRestorePassword("errada")
        vm.open()
        assertEquals(BackupMessage.Failed(BackupError.WRONG_PASSWORD), vm.uiState.value.message)

        vm.updateRestorePassword("palavra-segura")
        vm.open()
        assertNotNull(vm.uiState.value.pending)
        vm.cancelRestore()
        assertNull(restored)

        vm.open()
        vm.confirmRestore()
        assertEquals(snapshot.tables.map { it.rows }, restored?.tables?.map { it.rows })
        assertTrue(vm.uiState.value.message is BackupMessage.Restored)
        assertNull(vm.uiState.value.restoreUri)
        assertFalse(vm.uiState.value.isBusy)
    }
}
