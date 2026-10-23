package pt.vcc.financeapp.feature.finance.purchases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.data.repository.MerchantRepository
import pt.vcc.financeapp.core.data.usecase.CatalogSaveResult
import pt.vcc.financeapp.core.data.usecase.CatalogValidationError
import pt.vcc.financeapp.core.data.usecase.SaveMerchantUseCase
import pt.vcc.financeapp.core.designsystem.components.EmptyState
import pt.vcc.financeapp.core.model.Merchant
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.LoadingState

/** Diálogo de edição; `merchant == null` cria um novo estabelecimento. */
data class MerchantDialogState(
    val merchant: Merchant? = null,
    val name: String = merchant?.name.orEmpty(),
    val isActive: Boolean = merchant?.isActive ?: true,
    val errors: Set<CatalogValidationError> = emptySet(),
)

data class MerchantsUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val merchants: List<Merchant> = emptyList(),
    val dialog: MerchantDialogState? = null,
    val error: String? = null,
)

@HiltViewModel
class MerchantsViewModel @Inject constructor(
    merchantRepository: MerchantRepository,
    private val saveMerchant: SaveMerchantUseCase,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val dialog = MutableStateFlow<MerchantDialogState?>(null)

    val uiState: StateFlow<MerchantsUiState> = combine(merchantRepository.observeAll(), query, dialog) { merchants, q, d ->
        val key = Merchant.normalize(q)
        MerchantsUiState(
            isLoading = false,
            query = q,
            merchants = if (key.isEmpty()) merchants else merchants.filter { key in it.normalizedName },
            dialog = d,
        )
    }
        .catch { emit(MerchantsUiState(isLoading = false, error = it.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MerchantsUiState())

    val dialogState: StateFlow<MerchantDialogState?> = dialog.asStateFlow()

    fun onQueryChange(value: String) = query.update { value }
    fun onAdd() = dialog.update { MerchantDialogState(name = query.value.trim()) }
    fun onEdit(merchant: Merchant) = dialog.update { MerchantDialogState(merchant) }
    fun onDismiss() = dialog.update { null }
    fun onNameChange(value: String) = dialog.update { it?.copy(name = value, errors = emptySet()) }
    fun onActiveChange(value: Boolean) = dialog.update { it?.copy(isActive = value) }

    fun onSave() {
        val current = dialog.value ?: return
        viewModelScope.launch {
            when (val result = saveMerchant(current.merchant?.id, current.name, current.isActive)) {
                is CatalogSaveResult.Saved -> dialog.update { null }
                is CatalogSaveResult.Invalid -> dialog.update { it?.copy(errors = result.errors) }
            }
        }
    }
}

@Composable
fun MerchantsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MerchantsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PurchaseScaffold(
        title = stringResource(R.string.merchants_title),
        onBack = onBack,
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.merchants_add))
            }
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@PurchaseScaffold
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                label = { Text(stringResource(R.string.action_search)) },
            )
            if (state.merchants.isEmpty()) {
                EmptyState(stringResource(R.string.merchants_title), stringResource(R.string.merchants_empty))
            } else {
                LazyColumn {
                    items(state.merchants, key = { it.id }) { merchant ->
                        ListItem(
                            modifier = Modifier.clickable { viewModel.onEdit(merchant) },
                            headlineContent = {
                                Text(
                                    merchant.name,
                                    color = if (merchant.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                )
                            },
                            supportingContent = if (merchant.isActive) null else {
                                { Text(stringResource(R.string.label_inactive)) }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    state.dialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = viewModel::onDismiss,
            title = { Text(stringResource(if (dialog.merchant == null) R.string.merchants_add else R.string.merchants_edit)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = dialog.name,
                        onValueChange = viewModel::onNameChange,
                        label = { Text(stringResource(R.string.label_name)) },
                        singleLine = true,
                        isError = dialog.errors.isNotEmpty(),
                        supportingText = dialog.errors.firstOrNull()?.let { error -> { Text(error.message()) } },
                    )
                    ActiveSwitch(dialog.isActive, viewModel::onActiveChange)
                }
            },
            confirmButton = { TextButton(onClick = viewModel::onSave) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = viewModel::onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
