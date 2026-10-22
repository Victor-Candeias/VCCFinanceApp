package pt.vcc.financeapp.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pt.vcc.financeapp.core.database.backup.BackupTable
import pt.vcc.financeapp.core.database.backup.DatabaseSnapshot
import pt.vcc.financeapp.core.database.backup.IncompatibleBackupException
import pt.vcc.financeapp.core.database.backup.RoomDatabaseBackupStore
import pt.vcc.financeapp.core.database.entity.AccountEntity
import pt.vcc.financeapp.core.database.entity.BudgetEntity
import pt.vcc.financeapp.core.database.entity.CategoryEntity
import pt.vcc.financeapp.core.database.entity.TransactionEntity
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.BudgetPeriod
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.model.TransactionType
import pt.vcc.financeapp.core.security.testing.FakeFieldCipher

@RunWith(AndroidJUnit4::class)
class DatabaseBackupStoreTest {
    private lateinit var db: VCCFinanceDatabase
    private val cipher = FakeFieldCipher()
    private lateinit var store: RoomDatabaseBackupStore

    private val iban = "PT50000201231234567890154"
    private val account = AccountEntity(id = "acc-1", name = "Conta", type = AccountType.BANK_ACCOUNT, ibanEncrypted = cipher.encrypt(iban))
    private val parent = CategoryEntity("cat-p", "Casa", "home", "#000000", TransactionType.EXPENSE)
    private val child = CategoryEntity("cat-c", "Renda", "key", "#111111", TransactionType.EXPENSE, parentCategoryId = "cat-p")
    private val transaction = TransactionEntity(
        id = "tx-1",
        accountId = "acc-1",
        type = TransactionType.EXPENSE,
        amountMinor = 65_000,
        description = "Renda",
        categoryId = "cat-c",
        bookingDate = 1_790_000_000_000,
        source = TransactionSource.MANUAL,
    )
    private val budget = BudgetEntity(id = "b-1", name = "Casa", categoryId = "cat-p", amountMinor = 80_000, period = BudgetPeriod.MONTHLY, startDate = 0)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), VCCFinanceDatabase::class.java).build()
        store = RoomDatabaseBackupStore(db, cipher)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed() {
        db.accountDao().insert(account)
        // Filha antes da mãe: o restauro tem de tolerar a ordem das linhas na autorreferência.
        db.categoryDao().upsert(child.copy(parentCategoryId = null))
        db.categoryDao().upsert(parent)
        db.categoryDao().upsert(child)
        db.transactionDao().insert(transaction)
        db.budgetDao().upsert(budget)
    }

    @Test
    fun snapshotCarriesPlainIbanAndRestoreReplacesEverything() = runTest {
        seed()
        val snapshot = store.snapshot()
        val accounts = snapshot.tables.single { it.name == "accounts" }
        assertTrue("iban" in accounts.columns && "ibanEncrypted" !in accounts.columns)
        assertEquals(iban, accounts.rows.single()[accounts.columns.indexOf("iban")])
        assertTrue(snapshot.tables.indexOfFirst { it.name == "accounts" } < snapshot.tables.indexOfFirst { it.name == "transactions" })

        db.transactionDao().delete(transaction)
        db.accountDao().insert(AccountEntity(id = "acc-2", name = "Nova", type = AccountType.CASH))
        store.restore(snapshot)

        assertEquals(listOf("acc-1"), db.accountDao().observeAll().first().map { it.id })
        assertEquals(iban, cipher.decrypt(requireNotNull(db.accountDao().getById("acc-1")?.ibanEncrypted)))
        assertEquals(transaction, db.transactionDao().getById("tx-1"))
        assertEquals(setOf(parent, child), db.categoryDao().observeAll().first().toSet())
        assertEquals(budget, db.budgetDao().getById("b-1"))
    }

    @Test
    fun invalidBackupLeavesCurrentDataUntouched() = runTest {
        seed()
        val snapshot = store.snapshot()
        val orphan = snapshot.copy(
            tables = snapshot.tables.map { table ->
                if (table.name != "transactions") table else table.copy(
                    rows = table.rows.map { row -> row.toMutableList().also { it[table.columns.indexOf("accountId")] = "missing" } },
                )
            },
        )

        assertTrue(runCatching { store.restore(orphan) }.isFailure)
        assertEquals(transaction, db.transactionDao().getById("tx-1"))

        val unknown = snapshot.copy(tables = snapshot.tables + BackupTable("future_table", listOf("id"), emptyList()))
        assertTrue(runCatching { store.restore(unknown) }.exceptionOrNull() is IncompatibleBackupException)
        val newer = DatabaseSnapshot(VCCFinanceDatabase.VERSION + 1, emptyList())
        assertTrue(runCatching { store.restore(newer) }.exceptionOrNull() is IncompatibleBackupException)
        assertEquals(account, db.accountDao().getById("acc-1"))
    }
}
