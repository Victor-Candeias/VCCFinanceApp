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

    /** 4 → 5: catálogo, listas planeadas e compras pagas por MB ou Dinheiro (S8-01). */
    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `product_categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `icon` TEXT, `financeCategoryId` TEXT, `sortOrder` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`financeCategoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_product_categories_financeCategoryId` ON `product_categories` (`financeCategoryId`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `products` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `productCategoryId` TEXT, `barcode` TEXT, `brand` TEXT, `defaultPriceMinor` INTEGER, `unit` TEXT, `isFavorite` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `isTemporary` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`productCategoryId`) REFERENCES `product_categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_products_productCategoryId` ON `products` (`productCategoryId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_products_barcode` ON `products` (`barcode`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `shopping_lists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `budgetMinor` INTEGER, `isArchived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `shopping_list_items` (`id` TEXT NOT NULL, `listId` TEXT NOT NULL, `name` TEXT NOT NULL, `quantity` TEXT NOT NULL, `unit` TEXT, `estimatedPriceMinor` INTEGER, `productId` TEXT, `position` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`listId`) REFERENCES `shopping_lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_shopping_list_items_listId` ON `shopping_list_items` (`listId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_shopping_list_items_productId` ON `shopping_list_items` (`productId`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `purchases` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `status` TEXT NOT NULL, `occurredAt` INTEGER NOT NULL, `currency` TEXT NOT NULL, `totalMinor` INTEGER NOT NULL, `merchantId` TEXT, `shoppingListId` TEXT, `budgetMinor` INTEGER, `paymentMethod` TEXT, `accountId` TEXT, `categoryId` TEXT, `transactionId` TEXT, `notes` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `completedAt` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`merchantId`) REFERENCES `merchants`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`shoppingListId`) REFERENCES `shopping_lists`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_purchases_type_status` ON `purchases` (`type`, `status`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_purchases_occurredAt` ON `purchases` (`occurredAt`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_purchases_merchantId` ON `purchases` (`merchantId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_purchases_shoppingListId` ON `purchases` (`shoppingListId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_purchases_accountId` ON `purchases` (`accountId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_purchases_categoryId` ON `purchases` (`categoryId`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_purchases_transactionId` ON `purchases` (`transactionId`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `purchase_items` (`id` TEXT NOT NULL, `purchaseId` TEXT NOT NULL, `name` TEXT NOT NULL, `quantity` TEXT NOT NULL, `unitPriceMinor` INTEGER NOT NULL, `discountMinor` INTEGER NOT NULL, `totalMinor` INTEGER NOT NULL, `unit` TEXT, `productId` TEXT, `shoppingListItemId` TEXT, `categoryName` TEXT, `priceSource` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`purchaseId`) REFERENCES `purchases`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`shoppingListItemId`) REFERENCES `shopping_list_items`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_purchase_items_purchaseId` ON `purchase_items` (`purchaseId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_purchase_items_productId` ON `purchase_items` (`productId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_purchase_items_shoppingListItemId` ON `purchase_items` (`shoppingListItemId`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `purchase_skipped_items` (`purchaseId` TEXT NOT NULL, `shoppingListItemId` TEXT NOT NULL, PRIMARY KEY(`purchaseId`, `shoppingListItemId`), FOREIGN KEY(`purchaseId`) REFERENCES `purchases`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`shoppingListItemId`) REFERENCES `shopping_list_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_purchase_skipped_items_shoppingListItemId` ON `purchase_skipped_items` (`shoppingListItemId`)",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
