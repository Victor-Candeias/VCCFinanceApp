package pt.vcc.financeapp.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pt.vcc.financeapp.core.database.dao.AccountBalanceSnapshotDao
import pt.vcc.financeapp.core.database.dao.AccountDao
import pt.vcc.financeapp.core.database.dao.AttachmentDao
import pt.vcc.financeapp.core.database.dao.BudgetDao
import pt.vcc.financeapp.core.database.dao.CategoryDao
import pt.vcc.financeapp.core.database.dao.FinancialConnectionDao
import pt.vcc.financeapp.core.database.dao.ImportBatchDao
import pt.vcc.financeapp.core.database.dao.MerchantDao
import pt.vcc.financeapp.core.database.dao.RecurringTransactionDao
import pt.vcc.financeapp.core.database.dao.SyncHistoryDao
import pt.vcc.financeapp.core.database.dao.TransactionDao
import pt.vcc.financeapp.core.database.dao.TransactionRuleDao

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    // Sem fallbackToDestructiveMigration(): uma migration em falta deve falhar, nunca apagar dados.
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VCCFinanceDatabase =
        Room.databaseBuilder(context, VCCFinanceDatabase::class.java, VCCFinanceDatabase.NAME).build()

    @Provides fun accountDao(db: VCCFinanceDatabase): AccountDao = db.accountDao()
    @Provides fun transactionDao(db: VCCFinanceDatabase): TransactionDao = db.transactionDao()
    @Provides fun categoryDao(db: VCCFinanceDatabase): CategoryDao = db.categoryDao()
    @Provides fun merchantDao(db: VCCFinanceDatabase): MerchantDao = db.merchantDao()
    @Provides fun budgetDao(db: VCCFinanceDatabase): BudgetDao = db.budgetDao()

    @Provides
    fun recurringTransactionDao(db: VCCFinanceDatabase): RecurringTransactionDao = db.recurringTransactionDao()

    @Provides
    fun financialConnectionDao(db: VCCFinanceDatabase): FinancialConnectionDao = db.financialConnectionDao()

    @Provides fun syncHistoryDao(db: VCCFinanceDatabase): SyncHistoryDao = db.syncHistoryDao()
    @Provides fun importBatchDao(db: VCCFinanceDatabase): ImportBatchDao = db.importBatchDao()
    @Provides fun transactionRuleDao(db: VCCFinanceDatabase): TransactionRuleDao = db.transactionRuleDao()
    @Provides fun attachmentDao(db: VCCFinanceDatabase): AttachmentDao = db.attachmentDao()

    @Provides
    fun accountBalanceSnapshotDao(db: VCCFinanceDatabase): AccountBalanceSnapshotDao = db.accountBalanceSnapshotDao()
}
