package pt.vcc.financeapp.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pt.vcc.financeapp.core.data.exporter.AndroidPdfExporter
import pt.vcc.financeapp.core.data.exporter.ContentResolverDocumentWriter
import pt.vcc.financeapp.core.data.exporter.DocumentWriter
import pt.vcc.financeapp.core.data.exporter.PdfExportWriter
import pt.vcc.financeapp.core.data.importer.ContentResolverDocumentReader
import pt.vcc.financeapp.core.data.importer.DocumentReader
import pt.vcc.financeapp.core.data.repository.AccountRepository
import pt.vcc.financeapp.core.data.repository.BudgetRepository
import pt.vcc.financeapp.core.data.repository.CategoryRepository
import pt.vcc.financeapp.core.data.repository.ImportBatchRepository
import pt.vcc.financeapp.core.data.repository.ImportProfileRepository
import pt.vcc.financeapp.core.data.repository.LocalAccountRepository
import pt.vcc.financeapp.core.data.repository.LocalBudgetRepository
import pt.vcc.financeapp.core.data.repository.LocalCategoryRepository
import pt.vcc.financeapp.core.data.repository.LocalImportBatchRepository
import pt.vcc.financeapp.core.data.repository.LocalImportProfileRepository
import pt.vcc.financeapp.core.data.repository.LocalTransactionRepository
import pt.vcc.financeapp.core.data.repository.TransactionRepository

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {
    @Binds
    abstract fun bindAccountRepository(implementation: LocalAccountRepository): AccountRepository

    @Binds
    abstract fun bindTransactionRepository(implementation: LocalTransactionRepository): TransactionRepository

    @Binds
    abstract fun bindCategoryRepository(implementation: LocalCategoryRepository): CategoryRepository

    @Binds
    abstract fun bindDocumentReader(implementation: ContentResolverDocumentReader): DocumentReader

    @Binds
    abstract fun bindDocumentWriter(implementation: ContentResolverDocumentWriter): DocumentWriter

    @Binds
    abstract fun bindPdfExportWriter(implementation: AndroidPdfExporter): PdfExportWriter

    @Binds
    abstract fun bindImportBatchRepository(implementation: LocalImportBatchRepository): ImportBatchRepository

    @Binds
    abstract fun bindImportProfileRepository(implementation: LocalImportProfileRepository): ImportProfileRepository

    @Binds
    abstract fun bindBudgetRepository(implementation: LocalBudgetRepository): BudgetRepository
}
