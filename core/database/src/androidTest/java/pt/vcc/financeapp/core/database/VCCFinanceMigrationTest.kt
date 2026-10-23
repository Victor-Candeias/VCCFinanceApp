package pt.vcc.financeapp.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pt.vcc.financeapp.core.database.entity.ImportProfileEntity
import pt.vcc.financeapp.core.database.entity.PurchaseEntity
import pt.vcc.financeapp.core.model.PaymentMethod
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.core.model.PurchaseType

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
                INSERT INTO account_balance_snapshots (id, accountId, balanceMinor, currency, referenceDate, source, createdAt)
                VALUES ('snap-1', 'acc-1', 50000, 'EUR', 1760000000000, 'MANUAL', 0)
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
        // v2: tabela de perfis de importação criada vazia e utilizável.
        val profile = ImportProfileEntity(
            id = "p-1", name = "CGD", headerSignature = "data mov|descritivo", headerRowIndex = 0,
            dateColumn = 0, descriptionColumn = 1, amountColumn = 2, debitColumn = null, creditColumn = null,
            valueDateColumn = null, referenceColumn = null, balanceColumn = null, dateFormat = "dd/MM/yyyy",
            invertAmounts = false, createdAt = 0, updatedAt = 0,
        )
        database.importProfileDao().upsert(profile)
        assertEquals(profile, database.importProfileDao().findBySignature("data mov|descritivo"))
        // v3: saldos existentes ficam sem importação associada.
        val snapshot = database.accountBalanceSnapshotDao().observeLatest("acc-1").first()
        assertEquals(50000L, snapshot?.balanceMinor)
        assertNull(snapshot?.importBatchId)
        // v4: contas existentes não ficam marcadas como conta por omissão.
        assertEquals(false, database.accountDao().getById("acc-1")?.isDefault)
        // v5: tabelas das compras criadas vazias; uma compra concluída liga-se ao movimento existente.
        val purchaseDao = database.purchaseDao()
        assertEquals(emptyList<Any>(), purchaseDao.getAllPurchases())
        val purchase = PurchaseEntity(
            id = "pur-1", type = PurchaseType.QUICK, status = PurchaseStatus.COMPLETED, occurredAt = 1760000000000,
            currency = "EUR", totalMinor = 123456, merchantId = null, shoppingListId = null, budgetMinor = null,
            paymentMethod = PaymentMethod.MB, accountId = "acc-1", categoryId = null, transactionId = "tx-1",
            notes = null, createdAt = 0, updatedAt = 0, completedAt = 0,
        )
        purchaseDao.upsertPurchase(purchase)
        assertEquals(purchase, purchaseDao.getPurchaseByTransaction("tx-1"))
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
        const val FIRST_VERSION = 1
    }
}
