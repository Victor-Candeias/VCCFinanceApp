package pt.vcc.financeapp.core.database

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pt.vcc.financeapp.core.security.testing.FakeDatabaseKeyProvider

class DatabaseAccessTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun database(content: ByteArray?): File =
        File(folder.root, "test.db").also { file -> content?.let(file::writeBytes) }

    private val plaintext = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(84)
    private val encrypted = ByteArray(100) { (it * 37 + 11).toByte() }

    @Test
    fun missingOrPlaintextDatabaseIsReadableWithoutKey() {
        val noKey = FakeDatabaseKeyProvider(hasKey = false)

        assertTrue(DatabaseAccess.isReadable(database(null), noKey))
        assertFalse(DatabaseAccess.isOrphaned(database(null), noKey))
        assertTrue(DatabaseAccess.isReadable(database(plaintext), noKey))
        assertFalse(DatabaseAccess.isOrphaned(database(plaintext), noKey))
    }

    @Test
    fun encryptedDatabaseNeedsAUsableKey() {
        val file = database(encrypted)

        assertTrue(DatabaseAccess.isReadable(file, FakeDatabaseKeyProvider()))
        assertFalse(DatabaseAccess.isReadable(file, FakeDatabaseKeyProvider(usable = false)))
        assertFalse(DatabaseAccess.isOrphaned(file, FakeDatabaseKeyProvider(usable = false)))
    }

    @Test
    fun encryptedDatabaseWithoutKeyFileIsOrphaned() {
        val file = database(encrypted)
        val noKey = FakeDatabaseKeyProvider(hasKey = false)

        assertFalse(DatabaseAccess.isReadable(file, noKey))
        assertTrue(DatabaseAccess.isOrphaned(file, noKey))
    }
}
