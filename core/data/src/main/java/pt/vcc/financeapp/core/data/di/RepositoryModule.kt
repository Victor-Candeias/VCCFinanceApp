package pt.vcc.financeapp.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pt.vcc.financeapp.core.data.repository.AccountRepository
import pt.vcc.financeapp.core.data.repository.CategoryRepository
import pt.vcc.financeapp.core.data.repository.LocalAccountRepository
import pt.vcc.financeapp.core.data.repository.LocalCategoryRepository
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
}
