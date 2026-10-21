package pt.vcc.financeapp.core.data.testing

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import pt.vcc.financeapp.core.data.repository.AccountRepository
import pt.vcc.financeapp.core.data.repository.CategoryRepository
import pt.vcc.financeapp.core.data.repository.TransactionRepository
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.Category
import pt.vcc.financeapp.core.model.BalanceMovement
import pt.vcc.financeapp.core.model.Transaction
import pt.vcc.financeapp.core.model.asBalanceMovement

class FakeAccountRepository(initial: List<Account> = emptyList()) : AccountRepository {
    val accounts = MutableStateFlow(initial)

    override fun observeActiveAccounts(): Flow<List<Account>> = accounts.map { list -> list.filter { it.isActive } }
    override fun observeAllAccounts(): Flow<List<Account>> = accounts
    override fun observeAccount(id: String): Flow<Account?> = accounts.map { list -> list.find { it.id == id } }
    override suspend fun getById(id: String): Account? = accounts.value.find { it.id == id }
    override suspend fun create(account: Account) = accounts.update { it + account }
    override suspend fun update(account: Account) = accounts.update { list -> list.map { if (it.id == account.id) account else it } }
    override suspend fun deactivate(id: String) = accounts.update { list -> list.map { if (it.id == id) it.copy(isActive = false) else it } }
    override suspend fun reactivate(id: String) = accounts.update { list -> list.map { if (it.id == id) it.copy(isActive = true) else it } }
}

class FakeCategoryRepository(initial: List<Category> = emptyList()) : CategoryRepository {
    val categories = MutableStateFlow(initial)

    override fun observeActiveCategories(): Flow<List<Category>> = categories.map { list -> list.filter { it.isActive } }
    override fun observeAllCategories(): Flow<List<Category>> = categories
    override suspend fun getById(id: String): Category? = categories.value.find { it.id == id }
    override suspend fun save(category: Category) = categories.update { list -> list.filterNot { it.id == category.id } + category }
    override suspend fun deactivate(id: String) = categories.update { list -> list.map { if (it.id == id) it.copy(isActive = false) else it } }
    override suspend fun insertIfAbsent(categories: List<Category>): Int {
        val missing = categories.filter { candidate -> this.categories.value.none { it.id == candidate.id } }
        this.categories.update { it + missing }
        return missing.size
    }
}

class FakeTransactionRepository(initial: List<Transaction> = emptyList()) : TransactionRepository {
    val transactions = MutableStateFlow(initial)
    val requestedPeriods = mutableListOf<Pair<Instant, Instant>>()

    override fun observeByAccount(accountId: String): Flow<List<Transaction>> =
        transactions.map { list -> list.filter { it.accountId == accountId } }

    override fun observeInPeriod(fromInclusive: Instant, toExclusive: Instant): Flow<List<Transaction>> {
        requestedPeriods += fromInclusive to toExclusive
        return transactions.map { list -> list.filter { it.bookingDate >= fromInclusive && it.bookingDate < toExclusive } }
    }

    override fun observeBalanceMovements(): Flow<List<BalanceMovement>> =
        transactions.map { list -> list.map(Transaction::asBalanceMovement) }

    override suspend fun getById(id: String): Transaction? = transactions.value.find { it.id == id }
    override suspend fun countByAccount(accountId: String): Int = transactions.value.count { it.accountId == accountId }
    override suspend fun create(transaction: Transaction) = transactions.update { it + transaction }
    override suspend fun update(transaction: Transaction) =
        transactions.update { list -> list.map { if (it.id == transaction.id) transaction else it } }
    override suspend fun delete(transaction: Transaction) = transactions.update { list -> list.filterNot { it.id == transaction.id } }
    override suspend fun getTransferLegs(transferGroupId: String): List<Transaction> =
        transactions.value.filter { it.transferGroupId == transferGroupId }
    override suspend fun createTransfer(legs: List<Transaction>) = transactions.update { it + legs }
    override suspend fun updateTransfer(legs: List<Transaction>) = transactions.update { list ->
        list.map { current -> legs.find { it.id == current.id } ?: current }
    }
    override suspend fun deleteTransfer(transferGroupId: String) =
        transactions.update { list -> list.filterNot { it.transferGroupId == transferGroupId } }
    override suspend fun insertIgnoringDuplicates(transactions: List<Transaction>): Int {
        this.transactions.update { it + transactions }
        return transactions.size
    }
}
