package pt.vcc.financeapp.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import pt.vcc.financeapp.core.database.converters.FinanceConverters
import pt.vcc.financeapp.core.database.dao.AccountBalanceSnapshotDao
import pt.vcc.financeapp.core.database.dao.AccountDao
import pt.vcc.financeapp.core.database.dao.AttachmentDao
import pt.vcc.financeapp.core.database.dao.BudgetDao
import pt.vcc.financeapp.core.database.dao.CategoryDao
import pt.vcc.financeapp.core.database.dao.FinancialConnectionDao
import pt.vcc.financeapp.core.database.dao.ImportBatchDao
import pt.vcc.financeapp.core.database.dao.ImportProfileDao
import pt.vcc.financeapp.core.database.dao.MerchantDao
import pt.vcc.financeapp.core.database.dao.PurchaseDao
import pt.vcc.financeapp.core.database.dao.RecurringTransactionDao
import pt.vcc.financeapp.core.database.dao.SyncHistoryDao
import pt.vcc.financeapp.core.database.dao.TransactionDao
import pt.vcc.financeapp.core.database.dao.TransactionRuleDao
import pt.vcc.financeapp.core.database.entity.AccountBalanceSnapshotEntity
import pt.vcc.financeapp.core.database.entity.AccountEntity
import pt.vcc.financeapp.core.database.entity.AttachmentEntity
import pt.vcc.financeapp.core.database.entity.BudgetEntity
import pt.vcc.financeapp.core.database.entity.CategoryEntity
import pt.vcc.financeapp.core.database.entity.FinancialConnectionEntity
import pt.vcc.financeapp.core.database.entity.ImportBatchEntity
import pt.vcc.financeapp.core.database.entity.ImportProfileEntity
import pt.vcc.financeapp.core.database.entity.MerchantEntity
import pt.vcc.financeapp.core.database.entity.ProductCategoryEntity
import pt.vcc.financeapp.core.database.entity.ProductEntity
import pt.vcc.financeapp.core.database.entity.PurchaseEntity
import pt.vcc.financeapp.core.database.entity.PurchaseItemEntity
import pt.vcc.financeapp.core.database.entity.PurchaseSkippedItemEntity
import pt.vcc.financeapp.core.database.entity.ShoppingListEntity
import pt.vcc.financeapp.core.database.entity.ShoppingListItemEntity
import pt.vcc.financeapp.core.database.entity.RecurringTransactionEntity
import pt.vcc.financeapp.core.database.entity.SyncHistoryEntity
import pt.vcc.financeapp.core.database.entity.TransactionEntity
import pt.vcc.financeapp.core.database.entity.TransactionRuleEntity

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        CategoryEntity::class,
        MerchantEntity::class,
        BudgetEntity::class,
        RecurringTransactionEntity::class,
        FinancialConnectionEntity::class,
        SyncHistoryEntity::class,
        ImportBatchEntity::class,
        TransactionRuleEntity::class,
        AttachmentEntity::class,
        AccountBalanceSnapshotEntity::class,
        ImportProfileEntity::class,
        ProductCategoryEntity::class,
        ProductEntity::class,
        ShoppingListEntity::class,
        ShoppingListItemEntity::class,
        PurchaseEntity::class,
        PurchaseItemEntity::class,
        PurchaseSkippedItemEntity::class,
    ],
    version = VCCFinanceDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(FinanceConverters::class)
abstract class VCCFinanceDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantDao(): MerchantDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
    abstract fun financialConnectionDao(): FinancialConnectionDao
    abstract fun syncHistoryDao(): SyncHistoryDao
    abstract fun importBatchDao(): ImportBatchDao
    abstract fun transactionRuleDao(): TransactionRuleDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun accountBalanceSnapshotDao(): AccountBalanceSnapshotDao
    abstract fun importProfileDao(): ImportProfileDao
    abstract fun purchaseDao(): PurchaseDao

    companion object {
        const val VERSION = 5
        const val NAME = "vcc_finance.db"
    }
}
