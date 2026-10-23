package pt.vcc.financeapp.feature.finance.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.data.repository.AccountRepository
import pt.vcc.financeapp.core.data.repository.TransactionRepository
import pt.vcc.financeapp.core.data.usecase.AccountInput
import pt.vcc.financeapp.core.data.usecase.AccountValidationError
import pt.vcc.financeapp.core.data.usecase.SaveAccountResult
import pt.vcc.financeapp.core.data.usecase.SaveAccountUseCase
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.AmountParser
import pt.vcc.financeapp.core.model.currencyFractionDigits
import pt.vcc.financeapp.core.security.FieldCipher
import pt.vcc.financeapp.feature.finance.ui.formatAmountInput

enum class AccountEditorError {
    BLANK_NAME, NAME_TOO_LONG, INVALID_CURRENCY, CURRENCY_LOCKED, INVALID_AMOUNT, INVALID_IBAN, NOT_FOUND
}

data class AccountEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val type: AccountType = AccountType.BANK_ACCOUNT,
    val institutionName: String = "",
    val currency: String = DEFAULT_CURRENCY,
    val openingBalance: String = "",
    val iban: String = "",
    /** O IBAN guardado aparece mascarado até o utilizador pedir para o ver. */
    val isIbanRevealed: Boolean = true,
    val isIbanChanged: Boolean = false,
    /** Existe um IBAN guardado que não foi possível decifrar; mantém-se se o campo não for alterado. */
    val isIbanUnreadable: Boolean = false,
    val isActive: Boolean = true,
    /** Conta pré-selecionada ao criar receitas e despesas. */
    val isDefault: Boolean = false,
    /** A moeda fica bloqueada quando a conta já tem movimentos. */
    val isCurrencyLocked: Boolean = false,
    val errors: Set<AccountEditorError> = emptySet(),
    val isSaving: Boolean = false,
    val isFinished: Boolean = false,
) {
    companion object {
        const val DEFAULT_CURRENCY = "EUR"
    }
}

@HiltViewModel
class AccountEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val saveAccount: SaveAccountUseCase,
    private val fieldCipher: FieldCipher,
) : ViewModel() {
    private val accountId: String? = savedStateHandle.get<String>(ACCOUNT_ID_ARG)

    private val _uiState = MutableStateFlow(AccountEditorUiState(isLoading = accountId != null, isNew = accountId == null))
    val uiState: StateFlow<AccountEditorUiState> = _uiState.asStateFlow()

    init {
        if (accountId != null) load(accountId)
    }

    private fun load(id: String) = viewModelScope.launch {
        val account = accountRepository.getById(id)
        if (account == null) {
            _uiState.update { it.copy(isLoading = false, errors = setOf(AccountEditorError.NOT_FOUND)) }
            return@launch
        }
        val hasTransactions = transactionRepository.countByAccount(id) > 0
        val iban = account.ibanEncrypted?.let(fieldCipher::decrypt)
        _uiState.value = AccountEditorUiState(
            isLoading = false,
            isNew = false,
            name = account.name,
            type = account.type,
            institutionName = account.institutionName.orEmpty(),
            currency = account.currency,
            openingBalance = formatAmountInput(account.openingBalance.amountMinor, currencyFractionDigits(account.currency)),
            iban = iban.orEmpty(),
            isIbanRevealed = iban == null,
            isIbanUnreadable = account.ibanEncrypted != null && iban == null,
            isActive = account.isActive,
            isDefault = account.isDefault,
            isCurrencyLocked = hasTransactions,
        )
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, errors = it.errors - NAME_ERRORS) }

    fun onTypeChange(value: AccountType) = _uiState.update { it.copy(type = value) }

    fun onInstitutionChange(value: String) = _uiState.update { it.copy(institutionName = value) }

    fun onCurrencyChange(value: String) = _uiState.update {
        if (it.isCurrencyLocked) it
        else it.copy(currency = value.uppercase().take(3), errors = it.errors - AccountEditorError.INVALID_CURRENCY)
    }

    fun onOpeningBalanceChange(value: String) =
        _uiState.update { it.copy(openingBalance = value, errors = it.errors - AccountEditorError.INVALID_AMOUNT) }

    fun onIbanChange(value: String) = _uiState.update {
        if (!it.isIbanRevealed) it
        else it.copy(
            iban = value.uppercase().filter { c -> c.isLetterOrDigit() || c == ' ' }.take(MAX_IBAN_INPUT),
            isIbanChanged = true,
            errors = it.errors - AccountEditorError.INVALID_IBAN,
        )
    }

    fun revealIban() = _uiState.update { it.copy(isIbanRevealed = true) }

    fun onDefaultChange(value: Boolean) = _uiState.update { if (it.isActive) it.copy(isDefault = value) else it }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading) return
        val amount = if (state.openingBalance.isBlank()) 0L
        else AmountParser.parseOrNull(state.openingBalance, currencyFractionDigits(state.currency.trim().uppercase()))
        if (amount == null) {
            _uiState.update { it.copy(errors = it.errors + AccountEditorError.INVALID_AMOUNT) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = saveAccount(
                AccountInput(
                    id = accountId,
                    name = state.name,
                    type = state.type,
                    institutionName = state.institutionName,
                    currency = state.currency,
                    openingBalanceMinor = amount,
                    iban = state.iban.takeIf { state.isIbanChanged },
                    isDefault = state.isDefault,
                ),
            )
            _uiState.update {
                when (result) {
                    is SaveAccountResult.Saved -> it.copy(isSaving = false, isFinished = true)
                    is SaveAccountResult.Invalid -> it.copy(isSaving = false, errors = result.errors.map(::toEditorError).toSet())
                }
            }
        }
    }

    /** Desativa ou reativa a conta; os movimentos são sempre preservados. */
    fun toggleActive() {
        val id = accountId ?: return
        val active = _uiState.value.isActive
        viewModelScope.launch {
            if (active) accountRepository.deactivate(id) else accountRepository.reactivate(id)
            _uiState.update { it.copy(isActive = !active, isFinished = true) }
        }
    }

    private fun toEditorError(error: AccountValidationError) = when (error) {
        AccountValidationError.BLANK_NAME -> AccountEditorError.BLANK_NAME
        AccountValidationError.NAME_TOO_LONG -> AccountEditorError.NAME_TOO_LONG
        AccountValidationError.INVALID_CURRENCY -> AccountEditorError.INVALID_CURRENCY
        AccountValidationError.CURRENCY_LOCKED -> AccountEditorError.CURRENCY_LOCKED
        AccountValidationError.INVALID_IBAN -> AccountEditorError.INVALID_IBAN
        AccountValidationError.NOT_FOUND -> AccountEditorError.NOT_FOUND
    }

    companion object {
        const val ACCOUNT_ID_ARG = "accountId"
        private const val MAX_IBAN_INPUT = 42
        private val NAME_ERRORS = setOf(AccountEditorError.BLANK_NAME, AccountEditorError.NAME_TOO_LONG)
    }
}
