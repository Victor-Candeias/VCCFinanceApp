package pt.vcc.financeapp.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pt.vcc.financeapp.core.database.dao.BalanceMovementRow
import pt.vcc.financeapp.core.database.entity.AccountBalanceSnapshotEntity
import pt.vcc.financeapp.core.database.entity.AccountEntity
import pt.vcc.financeapp.core.database.entity.CategoryEntity
import pt.vcc.financeapp.core.database.entity.ImportBatchEntity
import pt.vcc.financeapp.core.database.entity.TransactionEntity
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.BalanceSource
import pt.vcc.financeapp.core.model.ImportFormat
import pt.vcc.financeapp.core.model.ImportStatus
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.model.TransactionStatus
import pt.vcc.financeapp.core.model.TransactionType

@RunWith(AndroidJUnit4::class)
class VCCFinanceDatabaseTest {
    private lateinit var db: VCCFinanceDatabase

    private val account = AccountEntity(id = "acc-1", name = "Conta à ordem", type = AccountType.BANK_ACCOUNT)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VCCFinanceDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = db.close()

    private fun transaction(id: String, externalId: String?, source: TransactionSource = TransactionSource.CSV_IMPORT) =
        TransactionEntity(
            id = id,
            accountId = account.id,
            type = TransactionType.EXPENSE,
            amountMinor = 1250,
            description = "Supermercado",
            bookingDate = 1_760_000_000_000,
            source = source,
            externalId = externalId,
        )

    @Test
    fun importBatchIsSavedWithItsTransactionsAndRepeatedExternalIdsAreSkipped() = runTest {
        db.accountDao().insert(account)
        db.transactionDao().insert(transaction("old", externalId = "h:1"))
        val batch = ImportBatchEntity(
            id = "batch-1",
            accountId = account.id,
            format = ImportFormat.CSV,
            fileName = "extrato.csv",
            fileSha256 = "sha",
            status = ImportStatus.COMPLETED,
            totalRows = 2,
            importedRows = 2,
        )

        val inserted = db.importBatchDao().insertWithTransactions(
            batch,
            listOf(transaction("new-1", "h:1").copy(importBatchId = "batch-1"), transaction("new-2", "h:2").copy(importBatchId = "batch-1")),
        )

        assertEquals(1, inserted)
        val saved = db.importBatchDao().getById("batch-1")!!
        assertEquals(1, saved.importedRows)
        assertEquals(1, saved.duplicateRows)
        assertEquals(listOf(saved), db.importBatchDao().findCompletedByFile(account.id, "sha"))
        assertEquals(
            setOf("old", "new-2"),
            db.transactionDao().getByAccountInPeriod(account.id, 0, Long.MAX_VALUE).map { it.id }.toSet(),
        )
    }

    @Test
    fun importBatchIsRolledBackWhenATransactionFails() = runTest {
        db.accountDao().insert(account)
        val batch = ImportBatchEntity(
            id = "batch-2",
            accountId = account.id,
            format = ImportFormat.XLSX,
            fileName = "extrato.xlsx",
            fileSha256 = "sha2",
            status = ImportStatus.COMPLETED,
        )
        // Conta inexistente: a chave estrangeira falha e nada pode ficar gravado.
        val broken = transaction("bad", "h:3").copy(accountId = "missing", importBatchId = "batch-2")

        val error = runCatching { db.importBatchDao().insertWithTransactions(batch, listOf(transaction("ok", "h:4"), broken)) }.exceptionOrNull()

        assertTrue(error is SQLiteConstraintException)
        assertEquals(null, db.importBatchDao().getById("batch-2"))
        assertEquals(0, db.transactionDao().countByAccount(account.id))
    }

    @Test
    fun revertDeletesTheBatchTransactionsAndSnapshotOnce() = runTest {
        db.accountDao().insert(account)
        db.transactionDao().insert(transaction("manual", externalId = null, source = TransactionSource.MANUAL))
        val batch = ImportBatchEntity(
            id = "batch-3",
            accountId = account.id,
            format = ImportFormat.CSV,
            fileName = "extrato.csv",
            fileSha256 = "sha3",
            status = ImportStatus.COMPLETED,
        )
        val snapshot = AccountBalanceSnapshotEntity(
            accountId = account.id,
            balanceMinor = 5_000,
            referenceDate = 1_760_000_000_000,
            source = BalanceSource.IMPORT,
            importBatchId = "batch-3",
        )
        db.importBatchDao().insertWithTransactions(
            batch,
            listOf(transaction("i1", "h:5").copy(importBatchId = "batch-3"), transaction("i2", "h:6").copy(importBatchId = "batch-3")),
            snapshot,
        )
        assertEquals(snapshot, db.accountBalanceSnapshotDao().observeLatest(account.id).first())

        assertEquals(2, db.importBatchDao().revert("batch-3"))

        assertEquals(ImportStatus.REVERTED, db.importBatchDao().getById("batch-3")?.status)
        assertEquals(listOf("manual"), db.transactionDao().getByAccountInPeriod(account.id, 0, Long.MAX_VALUE).map { it.id })
        assertEquals(null, db.accountBalanceSnapshotDao().observeLatest(account.id).first())
        assertEquals(emptyList<ImportBatchEntity>(), db.importBatchDao().findCompletedByFile(account.id, "sha3"))
        assertEquals(null, db.importBatchDao().revert("batch-3"))
        assertEquals(null, db.importBatchDao().revert("missing"))
    }

    @Test
    fun foreignKeysAreEnforced() = runTest {
        val error = runCatching { db.transactionDao().insert(transaction("tx-1", externalId = null)) }.exceptionOrNull()

        assertTrue(error is SQLiteConstraintException)
    }

    @Test
    fun transferLegsAreInsertedAllOrNothing() = runTest {
        db.accountDao().insert(account)
        val outgoing = transaction("leg-out", externalId = null, source = TransactionSource.MANUAL).copy(transferGroupId = "grp-1")
        // A conta de destino não existe: a segunda perna viola a chave estrangeira.
        val incoming = outgoing.copy(id = "leg-in", accountId = "missing", type = TransactionType.INCOME)

        val error = runCatching { db.transactionDao().insertAtomically(listOf(outgoing, incoming)) }.exceptionOrNull()

        assertTrue(error is SQLiteConstraintException)
        assertEquals(emptyList<TransactionEntity>(), db.transactionDao().getByTransferGroup("grp-1"))
    }

    @Test
    fun balanceMovementsAreAggregatedByAccountTypeStatusAndCurrency() = runTest {
        db.accountDao().insert(account)
        val base = transaction("tx-1", externalId = null, source = TransactionSource.MANUAL)
        db.transactionDao().insertAtomically(
            listOf(
                base,
                base.copy(id = "tx-2", amountMinor = 750),
                base.copy(id = "tx-3", type = TransactionType.INCOME, amountMinor = 10_000),
                base.copy(id = "tx-4", status = TransactionStatus.PENDING, amountMinor = 300),
            ),
        )

        val rows = db.transactionDao().observeBalanceMovements().first().toSet()

        assertEquals(
            setOf(
                BalanceMovementRow("acc-1", TransactionType.EXPENSE, TransactionStatus.BOOKED, "EUR", 2_000),
                BalanceMovementRow("acc-1", TransactionType.INCOME, TransactionStatus.BOOKED, "EUR", 10_000),
                BalanceMovementRow("acc-1", TransactionType.EXPENSE, TransactionStatus.PENDING, "EUR", 300),
            ),
            rows,
        )
    }

    @Test
    fun insertAllIgnoresRepeatedExternalIdPerAccountAndSource() = runTest {
        db.accountDao().insert(account)

        val first = db.transactionDao().insertAll(listOf(transaction("tx-1", "REF-1"), transaction("tx-2", "REF-2")))
        val second = db.transactionDao().insertAll(
            listOf(
                transaction("tx-3", "REF-1"),
                transaction("tx-4", "REF-1", source = TransactionSource.BANK_SYNC),
                transaction("tx-5", externalId = null),
                transaction("tx-6", externalId = null),
            ),
        )

        assertEquals(2, first.count { it != -1L })
        assertEquals(listOf(-1L), second.take(1))
        assertEquals(3, second.count { it != -1L })
    }

    @Test
    fun categoryUpsertKeepsCategoryOnTransactions() = runTest {
        val category = CategoryEntity(
            id = "cat-food",
            name = "Alimentação",
            icon = "restaurant",
            color = "#4CAF50",
            type = TransactionType.EXPENSE,
        )
        db.accountDao().insert(account)
        db.categoryDao().upsert(category)
        db.transactionDao().insert(transaction("tx-1", externalId = null).copy(categoryId = category.id))

        db.categoryDao().upsert(category.copy(name = "Comida"))

        assertEquals("Comida", db.categoryDao().getById(category.id)?.name)
        assertEquals(category.id, db.transactionDao().getById("tx-1")?.categoryId)
    }

    @Test
    fun accountWithTransactionsCannotBeDeleted() = runTest {
        db.accountDao().insert(account)
        db.transactionDao().insert(transaction("tx-1", externalId = null))

        assertThrows(SQLiteConstraintException::class.java) {
            db.openHelper.writableDatabase.execSQL("DELETE FROM accounts WHERE id = 'acc-1'")
        }
    }
}
