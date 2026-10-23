package pt.vcc.financeapp.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Registo único das migrations manuais. Ao subir [VCCFinanceDatabase.VERSION]:
 * 1. compilar para exportar `schemas/.../<nova versão>.json` e versioná-lo;
 * 2. acrescentar aqui a `Migration(antiga, nova)`;
 * 3. acrescentar a `VCCFinanceMigrationTest` um caso que valide os dados migrados.
 */
object VCCFinanceMigrations {
    /** 1 → 2: perfis de importação por banco (S5-02). */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `import_profiles` (`id` TEXT NOT NULL, `name` TEXT NOT NULL,
                    `headerSignature` TEXT NOT NULL, `headerRowIndex` INTEGER NOT NULL, `dateColumn` INTEGER,
                    `descriptionColumn` INTEGER, `amountColumn` INTEGER, `debitColumn` INTEGER, `creditColumn` INTEGER,
                    `valueDateColumn` INTEGER, `referenceColumn` INTEGER, `balanceColumn` INTEGER,
                    `dateFormat` TEXT NOT NULL, `invertAmounts` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_import_profiles_headerSignature` ON `import_profiles` (`headerSignature`)",
            )
        }
    }

    /** 2 → 3: saldo final do extrato ligado à importação, para ser removido ao reverter (S5-04). */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `account_balance_snapshots` ADD COLUMN `importBatchId` TEXT")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_account_balance_snapshots_importBatchId` " +
                    "ON `account_balance_snapshots` (`importBatchId`)",
            )
        }
    }

    /** 3 → 4: conta por omissão nas transações (S3-06). */
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `accounts` ADD COLUMN `isDefault` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
