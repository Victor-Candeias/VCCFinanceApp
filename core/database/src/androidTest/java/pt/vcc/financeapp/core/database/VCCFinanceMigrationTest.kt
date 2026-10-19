package pt.vcc.financeapp.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cria a base de dados a partir do schema exportado mais antigo e abre-a com o Room atual e
 * [VCCFinanceMigrations.ALL]. Falha se faltar uma migration ou se o schema migrado divergir das entidades.
 */
@RunWith(AndroidJUnit4::class)
class VCCFinanceMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VCCFinanceDatabase::class.java,
    )

    @Test
    fun migrateAllFromFirstVersionPreservesData() = runTest {
        helper.createDatabase(TEST_DB, FIRST_VERSION).use { db ->
            db.execSQL(
                """
                INSERT INTO accounts (id, name, type, currency, openingBalanceMinor, isActive, createdAt)
                VALUES ('acc-1', 'Conta à ordem', 'BANK_ACCOUNT', 'EUR', 100000, 1, 0)
                """,
            )
            db.execSQL(
                """
                INSERT INTO transactions (id, accountId, type, amountMinor, currency, description,
                    bookingDate, status, source, externalId, createdAt, updatedAt)
                VALUES ('tx-1', 'acc-1', 'EXPENSE', 123456, 'EUR', 'Renda', 1760000000000,
                    'BOOKED', 'CSV_IMPORT', 'REF-1', 0, 0)
                """,
            )
        }

        val database = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            VCCFinanceDatabase::class.java,
            TEST_DB,
        ).addMigrations(*VCCFinanceMigrations.ALL).build()
        helper.closeWhenFinished(database)

        assertEquals(100000L, database.accountDao().getById("acc-1")?.openingBalanceMinor)
        assertEquals(123456L, database.transactionDao().getById("tx-1")?.amountMinor)
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
        const val FIRST_VERSION = 1
    }
}
