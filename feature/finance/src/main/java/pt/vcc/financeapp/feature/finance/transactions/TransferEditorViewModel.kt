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
import pt.vcc.financeapp.core.data.repository.TransactionRepository
import pt.vcc.financeapp.core.data.usecase.SaveTransferResult
import pt.vcc.financeapp.core.data.usecase.SaveTransferUseCase
import pt.vcc.financeapp.core.data.usecase.TransferInput
import pt.vcc.financeapp.core.data.usecase.TransferLegs
import pt.vcc.financeapp.core.data.usecase.TransferValidationError
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.AmountParser
import pt.vcc.financeapp.core.model.TransactionStatus
import pt.vcc.financeapp.core.model.currencyFractionDigits
import pt.vcc.financeapp.feature.finance.ui.formatAmountInput

enum class TransferEditorError {
    NO_FROM_ACCOUNT,
    NO_TO_ACCOUNT,
    ACCOUNT_INACTIVE,
    SAME_ACCOUNT,
    CURRENCY_MISMATCH,
    INVALID_AMOUNT,
    BLANK_DESCRIPTION,
    DESCRIPTION_TOO_LONG,
    NOT_FOUND,
}

data class TransferEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    /** Levantamento: transferência de uma conta para uma conta do tipo Dinheiro. */
    val isWithdrawal: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val fromAccountId: String? = null,
    val toAccountId: String? = null,
    val amount: String = "",
    val description: String = "",
    val date: LocalDate = LocalDate.of(1970, 1, 1),
    val status: TransactionStatus = TransactionStatus.BOOKED,
    val notes: String = "",
    val errors: Set<TransferEditorError> = emptySet(),
    val isSaving: Boolean = false,
    val isFinished: Boolean = false,
) {
    val fromAccount: Account? get() = accounts.find { it.id == fromAccountId }
    val toAccount: Account? get() = accounts.find { it.id == toAccountId }

    /** Contas que podem ser origem/destino; num levantamento o destino é sempre uma conta Dinheiro. */
    val fromOptions: List<Account> get() = if (isWithdrawal) accounts.filter { it.type != AccountType.CASH } else accounts
    val toOptions: List<Account> get() = if (isWithdrawal) accounts.filter { it.type == AccountType.CASH } else accounts
}

@HiltViewModel
class TransferEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val saveTransfer: SaveTransferUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val transferGroupId: String? = savedStateHandle.get<String>(TRANSFER_GROUP_ID_ARG)
    private val isWithdrawal: Boolean = transferGroupId == null && savedStateHandle.get<Boolean>(WITHDRAWAL_ARG) == true
    private var isDescriptionPrefilled = false

    private val _uiState = MutableStateFlow(TransferEditorUiState(isNew = transferGroupId == null, isWithdrawal = isWithdrawal))
    val uiState: StateFlow<TransferEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val legs = transferGroupId?.let { TransferLegs.from(transactionRepository.getTransferLegs(it)) }
        if (transferGroupId != null && legs == null) {
            _uiState.update { it.copy(isLoading = false, errors = setOf(TransferEditorError.NOT_FOUND)) }
            return
        }
        val involved = setOfNotNull(legs?.outgoing?.accountId, legs?.incoming?.accountId)
        val accounts = accountRepository.observeAllAccounts().first().filter { it.isActive || it.id in involved }

        _uiState.value = if (legs == null && isWithdrawal) {
            val source = accounts.firstOrNull { it.isDefault && it.type != AccountType.CASH }
                ?: accounts.firstOrNull { it.type != AccountType.CASH }
            TransferEditorUiState(
                isLoading = false,
                isWithdrawal = true,
                accounts = accounts,
                fromAccountId = source?.id,
                toAccountId = accounts.firstOrNull { it.type == AccountType.CASH }?.id,
                date = LocalDate.now(clock),
            )
        } else if (legs == null) {
            TransferEditorUiState(
                isLoading = false,
                accounts = accounts,
                fromAccountId = accounts.getOrNull(0)?.id,
                toAccountId = accounts.getOrNull(1)?.id,
                date = LocalDate.now(clock),
            )
        } else {
            val out = legs.outgoing
            TransferEditorUiState(
                isLoading = false,
                isNew = false,
                accounts = accounts,
                fromAccountId = out.accountId,
                toAccountId = legs.incoming.accountId,
                amount = formatAmountInput(out.amount.amountMinor, currencyFractionDigits(out.amount.currency)),
                description = out.description,
                date = out.bookingDate.atZone(clock.zone).toLocalDate(),
                status = out.status,
                notes = out.notes.orEmpty(),
            )
        }
    }

    fun onFromAccountChange(id: String) = _uiState.update { it.copy(fromAccountId = id, errors = it.errors - ACCOUNT_ERRORS) }

    fun onToAccountChange(id: String) = _uiState.update { it.copy(toAccountId = id, errors = it.errors - ACCOUNT_ERRORS) }

    fun swapAccounts() = _uiState.update {
        it.copy(fromAccountId = it.toAccountId, toAccountId = it.fromAccountId, errors = it.errors - ACCOUNT_ERRORS)
    }

    fun onAmountChange(value: String) =
        _uiState.update { it.copy(amount = value, errors = it.errors - TransferEditorError.INVALID_AMOUNT) }

    fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value, errors = it.errors - DESCRIPTION_ERRORS) }

    /** Preenche a descrição sugerida de um levantamento novo uma única vez, sem apagar o que o utilizador escreveu. */
    fun prefillWithdrawalDescription(value: String) {
        if (!isWithdrawal || isDescriptionPrefilled || _uiState.value.isLoading) return
        isDescriptionPrefilled = true
        _uiState.update { if (it.description.isBlank()) it.copy(description = value) else it }
    }

    fun onDateChange(value: LocalDate) = _uiState.update { it.copy(date = value) }

    fun onPendingChange(pending: Boolean) =
        _uiState.update { it.copy(status = if (pending) TransactionStatus.PENDING else TransactionStatus.BOOKED) }

    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value) }

    fun save() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving) return
        val from = state.fromAccount
        val to = state.toAccount
        val localErrors = buildSet {
            if (from == null) add(TransferEditorError.NO_FROM_ACCOUNT)
            if (to == null) add(TransferEditorError.NO_TO_ACCOUNT)
        }
        val amount = from?.let { AmountParser.parseOrNull(state.amount, currencyFractionDigits(it.currency)) }
        val errors = if (from != null && (amount == null || amount <= 0)) localErrors + TransferEditorError.INVALID_AMOUNT else localErrors
        if (errors.isNotEmpty() || from == null || to == null || amount == null) {
            _uiState.update { it.copy(errors = it.errors + errors) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = saveTransfer(
                TransferInput(
                    transferGroupId = transferGroupId,
                    fromAccountId = from.id,
                    toAccountId = to.id,
                    amountMinor = amount,
                    description = state.description,
                    bookingDate = state.date,
                    status = state.status,
                    notes = state.notes,
                ),
            )
            _uiState.update {
                when (result) {
                    is SaveTransferResult.Saved -> it.copy(isSaving = false, isFinished = true)
                    is SaveTransferResult.Invalid -> it.copy(isSaving = false, errors = result.errors.map(::toEditorError).toSet())
                }
            }
        }
    }

    /** Apaga as duas pernas de uma só vez. */
    fun delete() {
        val id = transferGroupId ?: return
        viewModelScope.launch {
            transactionRepository.deleteTransfer(id)
            _uiState.update { it.copy(isFinished = true) }
        }
    }

    private fun toEditorError(error: TransferValidationError) = when (error) {
        TransferValidationError.FROM_ACCOUNT_NOT_FOUND -> TransferEditorError.NO_FROM_ACCOUNT
        TransferValidationError.TO_ACCOUNT_NOT_FOUND -> TransferEditorError.NO_TO_ACCOUNT
        TransferValidationError.ACCOUNT_INACTIVE -> TransferEditorError.ACCOUNT_INACTIVE
        TransferValidationError.SAME_ACCOUNT -> TransferEditorError.SAME_ACCOUNT
        TransferValidationError.CURRENCY_MISMATCH -> TransferEditorError.CURRENCY_MISMATCH
        TransferValidationError.AMOUNT_NOT_POSITIVE -> TransferEditorError.INVALID_AMOUNT
        TransferValidationError.BLANK_DESCRIPTION -> TransferEditorError.BLANK_DESCRIPTION
        TransferValidationError.DESCRIPTION_TOO_LONG -> TransferEditorError.DESCRIPTION_TOO_LONG
        TransferValidationError.NOT_FOUND -> TransferEditorError.NOT_FOUND
    }

    companion object {
        const val TRANSFER_GROUP_ID_ARG = "transferGroupId"
        const val WITHDRAWAL_ARG = "withdrawal"
        private val ACCOUNT_ERRORS = setOf(
            TransferEditorError.NO_FROM_ACCOUNT,
            TransferEditorError.NO_TO_ACCOUNT,
            TransferEditorError.ACCOUNT_INACTIVE,
            TransferEditorError.SAME_ACCOUNT,
            TransferEditorError.CURRENCY_MISMATCH,
        )
        private val DESCRIPTION_ERRORS = setOf(TransferEditorError.BLANK_DESCRIPTION, TransferEditorError.DESCRIPTION_TOO_LONG)
    }
}
