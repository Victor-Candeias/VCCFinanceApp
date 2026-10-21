package pt.vcc.financeapp.feature.finance.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.data.repository.AccountRepository
import pt.vcc.financeapp.core.data.repository.CategoryRepository
import pt.vcc.financeapp.core.data.repository.TransactionRepository
import pt.vcc.financeapp.core.data.usecase.SaveTransactionResult
import pt.vcc.financeapp.core.data.usecase.SaveTransactionUseCase
import pt.vcc.financeapp.core.data.usecase.TransactionInput
import pt.vcc.financeapp.core.data.usecase.TransactionValidationError
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AmountParser
import pt.vcc.financeapp.core.model.Category
import pt.vcc.financeapp.core.model.Transaction
import pt.vcc.financeapp.core.model.TransactionStatus
import pt.vcc.financeapp.core.model.TransactionType
import pt.vcc.financeapp.core.model.currencyFractionDigits
import pt.vcc.financeapp.feature.finance.ui.formatAmountInput

enum class TransactionEditorError {
    NO_ACCOUNT,
    ACCOUNT_INACTIVE,
    INVALID_AMOUNT,
    BLANK_DESCRIPTION,
    DESCRIPTION_TOO_LONG,
    CATEGORY_MISMATCH,
    NOT_FOUND,
}

/** Categoria pronta a mostrar; subcategorias levam o nome da categoria-mãe (ex.: `Alimentação › Café`). */
data class CategoryOption(val id: String, val label: String, val type: TransactionType)

data class TransactionEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    /** Pernas de transferência só podem ser alteradas em conjunto (S3-04). */
    val isTransfer: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val categories: List<CategoryOption> = emptyList(),
    val accountId: String? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: String = "",
    val description: String = "",
    val date: LocalDate = LocalDate.of(1970, 1, 1),
    val categoryId: String? = null,
    val status: TransactionStatus = TransactionStatus.BOOKED,
    val notes: String = "",
    val errors: Set<TransactionEditorError> = emptySet(),
    val isSaving: Boolean = false,
    val isFinished: Boolean = false,
) {
    val selectedAccount: Account? get() = accounts.find { it.id == accountId }
    val categoriesForType: List<CategoryOption> get() = categories.filter { it.type == type }
}

@HiltViewModel
class TransactionEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val saveTransaction: SaveTransactionUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val transactionId: String? = savedStateHandle.get<String>(TRANSACTION_ID_ARG)
    private var existing: Transaction? = null

    private val _uiState = MutableStateFlow(TransactionEditorUiState(isNew = transactionId == null))
    val uiState: StateFlow<TransactionEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val transaction = transactionId?.let { transactionRepository.getById(it) }
        existing = transaction
        if (transactionId != null && transaction == null) {
            _uiState.update { it.copy(isLoading = false, errors = setOf(TransactionEditorError.NOT_FOUND)) }
            return
        }
        // Contas desativadas só aparecem se já forem a conta do movimento.
        val accounts = accountRepository.observeAllAccounts().first()
            .filter { it.isActive || it.id == transaction?.accountId }
        val categories = categoryRepository.observeAllCategories().first()
        val options = categories.toOptions(keepId = transaction?.categoryId)

        _uiState.value = if (transaction == null) {
            TransactionEditorUiState(
                isLoading = false,
                accounts = accounts,
                categories = options,
                accountId = accounts.firstOrNull()?.id,
                date = LocalDate.now(clock),
            )
        } else {
            TransactionEditorUiState(
                isLoading = false,
                isNew = false,
                isTransfer = transaction.transferGroupId != null,
                accounts = accounts,
                categories = options,
                accountId = transaction.accountId,
                type = transaction.type,
                amount = formatAmountInput(transaction.amount.amountMinor, currencyFractionDigits(transaction.amount.currency)),
                description = transaction.description,
                date = transaction.bookingDate.atZone(clock.zone).toLocalDate(),
                categoryId = transaction.categoryId,
                status = transaction.status,
                notes = transaction.notes.orEmpty(),
            )
        }
    }

    fun onAccountChange(accountId: String) =
        _uiState.update { it.copy(accountId = accountId, errors = it.errors - ACCOUNT_ERRORS) }

    fun onTypeChange(type: TransactionType) = _uiState.update { state ->
        val keepCategory = state.categories.any { it.id == state.categoryId && it.type == type }
        state.copy(
            type = type,
            categoryId = state.categoryId.takeIf { keepCategory },
            errors = state.errors - TransactionEditorError.CATEGORY_MISMATCH,
        )
    }

    fun onAmountChange(value: String) =
        _uiState.update { it.copy(amount = value, errors = it.errors - TransactionEditorError.INVALID_AMOUNT) }

    fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value, errors = it.errors - DESCRIPTION_ERRORS) }

    fun onDateChange(value: LocalDate) = _uiState.update { it.copy(date = value) }

    fun onCategoryChange(categoryId: String?) =
        _uiState.update { it.copy(categoryId = categoryId, errors = it.errors - TransactionEditorError.CATEGORY_MISMATCH) }

    fun onPendingChange(pending: Boolean) =
        _uiState.update { it.copy(status = if (pending) TransactionStatus.PENDING else TransactionStatus.BOOKED) }

    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value) }

    fun save() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving || state.isTransfer) return
        val account = state.selectedAccount
        if (account == null) {
            _uiState.update { it.copy(errors = it.errors + TransactionEditorError.NO_ACCOUNT) }
            return
        }
        val amount = AmountParser.parseOrNull(state.amount, currencyFractionDigits(account.currency))
        if (amount == null || amount <= 0) {
            _uiState.update { it.copy(errors = it.errors + TransactionEditorError.INVALID_AMOUNT) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = saveTransaction(
                TransactionInput(
                    id = transactionId,
                    accountId = account.id,
                    type = state.type,
                    amountMinor = amount,
                    description = state.description,
                    bookingDate = state.date,
                    categoryId = state.categoryId,
                    status = state.status,
                    notes = state.notes,
                ),
            )
            _uiState.update {
                when (result) {
                    is SaveTransactionResult.Saved -> it.copy(isSaving = false, isFinished = true)
                    is SaveTransactionResult.Invalid ->
                        it.copy(isSaving = false, errors = result.errors.mapNotNull(::toEditorError).toSet())
                }
            }
        }
    }

    fun delete() {
        val transaction = existing ?: return
        if (transaction.transferGroupId != null) return
        viewModelScope.launch {
            transactionRepository.delete(transaction)
            _uiState.update { it.copy(isFinished = true) }
        }
    }

    private fun toEditorError(error: TransactionValidationError): TransactionEditorError? = when (error) {
        TransactionValidationError.ACCOUNT_NOT_FOUND -> TransactionEditorError.NO_ACCOUNT
        TransactionValidationError.ACCOUNT_INACTIVE -> TransactionEditorError.ACCOUNT_INACTIVE
        TransactionValidationError.AMOUNT_NOT_POSITIVE -> TransactionEditorError.INVALID_AMOUNT
        TransactionValidationError.BLANK_DESCRIPTION -> TransactionEditorError.BLANK_DESCRIPTION
        TransactionValidationError.DESCRIPTION_TOO_LONG -> TransactionEditorError.DESCRIPTION_TOO_LONG
        TransactionValidationError.CATEGORY_MISMATCH -> TransactionEditorError.CATEGORY_MISMATCH
        TransactionValidationError.NOT_FOUND -> TransactionEditorError.NOT_FOUND
        // O formulário só oferece receitas/despesas e bloqueia transferências antes de guardar.
        TransactionValidationError.UNSUPPORTED_TYPE, TransactionValidationError.TRANSFER_LEG -> null
    }

    companion object {
        const val TRANSACTION_ID_ARG = "transactionId"
        private val ACCOUNT_ERRORS = setOf(TransactionEditorError.NO_ACCOUNT, TransactionEditorError.ACCOUNT_INACTIVE)
        private val DESCRIPTION_ERRORS = setOf(TransactionEditorError.BLANK_DESCRIPTION, TransactionEditorError.DESCRIPTION_TOO_LONG)
    }
}

/** Categorias ativas (e a atual do movimento, mesmo que desativada), com as filhas logo após a mãe. */
internal fun List<Category>.toOptions(keepId: String? = null): List<CategoryOption> {
    val visible = filter { it.isActive || it.id == keepId }
    val byId = associateBy { it.id }
    fun label(category: Category): String =
        category.parentCategoryId?.let(byId::get)?.let { "${it.name} › ${category.name}" } ?: category.name
    val roots = visible.filter { it.parentCategoryId == null || it.parentCategoryId !in byId }
    val children = visible.filter { it !in roots }.groupBy { it.parentCategoryId }
    return roots.sortedBy { it.name }.flatMap { root ->
        listOf(root) + children[root.id].orEmpty().sortedBy { it.name }
    }.plus(children.filterKeys { key -> roots.none { it.id == key } }.values.flatten())
        .map { CategoryOption(it.id, label(it), it.type) }
}
