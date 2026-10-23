package pt.vcc.financeapp.feature.finance.purchases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.data.repository.AccountRepository
import pt.vcc.financeapp.core.data.repository.CategoryRepository
import pt.vcc.financeapp.core.data.repository.MerchantRepository
import pt.vcc.financeapp.core.data.repository.PurchaseRepository
import pt.vcc.financeapp.core.data.usecase.DeletePurchaseUseCase
import pt.vcc.financeapp.core.data.usecase.RepeatPurchaseResult
import pt.vcc.financeapp.core.data.usecase.RepeatPurchaseUseCase
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.core.model.PurchaseItem
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.LoadingState
import pt.vcc.financeapp.feature.finance.ui.PortugueseLocale
import pt.vcc.financeapp.feature.finance.ui.formatted

enum class PurchaseDetailDialog { DELETE, REPEAT_CONFLICT }

/** Navegação pedida pelo detalhe (consumida pelo ecrã). */
sealed interface PurchaseDetailEvent {
    data object Deleted : PurchaseDetailEvent
    data class OpenPurchase(val purchase: Purchase) : PurchaseDetailEvent
}

data class PurchaseDetailUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val purchase: Purchase? = null,
    val items: List<PurchaseItem> = emptyList(),
    val occurredAt: LocalDateTime? = null,
    val merchantName: String? = null,
    val accountName: String? = null,
    val categoryName: String? = null,
    val dialog: PurchaseDetailDialog? = null,
    /** Compra em curso que impede a repetição (só pode haver uma por tipo). */
    val conflict: Purchase? = null,
    val event: PurchaseDetailEvent? = null,
) {
    val total: Money get() = Money(purchase?.totalMinor ?: 0, purchase?.currency ?: Money.DEFAULT_CURRENCY)
}

private data class DetailLocal(
    val dialog: PurchaseDetailDialog? = null,
    val conflict: Purchase? = null,
    val event: PurchaseDetailEvent? = null,
)

/** Detalhe de uma compra concluída: itens, pagamento e ações (editar, eliminar, repetir, ver movimento). */
@HiltViewModel
class PurchaseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    purchaseRepository: PurchaseRepository,
    merchantRepository: MerchantRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val deletePurchase: DeletePurchaseUseCase,
    private val repeatPurchase: RepeatPurchaseUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val purchaseId: String = checkNotNull(savedStateHandle[PurchaseViewModel.PURCHASE_ID_ARG]) { "purchaseId em falta" }
    private val local = MutableStateFlow(DetailLocal())
    private val _uiState = MutableStateFlow(PurchaseDetailUiState())
    val uiState: StateFlow<PurchaseDetailUiState> = _uiState.asStateFlow()

    init {
        val names = combine(
            merchantRepository.observeAll(),
            accountRepository.observeAllAccounts(),
            categoryRepository.observeAllCategories(),
        ) { merchants, accounts, categories ->
            Triple(merchants.associate { it.id to it.name }, accounts.associate { it.id to it.name }, categories.associate { it.id to it.name })
        }
        viewModelScope.launch {
            combine(
                purchaseRepository.observePurchase(purchaseId),
                purchaseRepository.observeItems(purchaseId),
                names,
                local,
            ) { purchase, items, (merchants, accounts, categories), state ->
                PurchaseDetailUiState(
                    isLoading = false,
                    notFound = purchase == null && state.event == null,
                    purchase = purchase,
                    items = items.sortedBy { it.position },
                    occurredAt = purchase?.occurredAt?.atZone(clock.zone)?.toLocalDateTime(),
                    merchantName = purchase?.merchantId?.let(merchants::get),
                    accountName = purchase?.accountId?.let(accounts::get),
                    categoryName = purchase?.categoryId?.let(categories::get),
                    dialog = state.dialog,
                    conflict = state.conflict,
                    event = state.event,
                )
            }
                .catch { _uiState.update { it.copy(isLoading = false, notFound = true) } }
                .collect { _uiState.value = it }
        }
    }

    fun onShowDialog(dialog: PurchaseDetailDialog?) = local.update { it.copy(dialog = dialog) }

    /** Elimina a compra e o movimento associado (atómico no repositório). */
    fun onConfirmDelete() {
        viewModelScope.launch {
            deletePurchase(purchaseId)
            local.update { it.copy(dialog = null, event = PurchaseDetailEvent.Deleted) }
        }
    }

    fun onRepeat() {
        viewModelScope.launch {
            when (val result = repeatPurchase(purchaseId)) {
                is RepeatPurchaseResult.Started -> local.update { it.copy(event = PurchaseDetailEvent.OpenPurchase(result.purchase)) }
                is RepeatPurchaseResult.Conflict ->
                    local.update { it.copy(dialog = PurchaseDetailDialog.REPEAT_CONFLICT, conflict = result.activePurchase) }
                RepeatPurchaseResult.NotFound -> Unit
            }
        }
    }

    /** Abre a compra em curso que impediu a repetição. */
    fun onOpenConflict() = local.update { state ->
        state.copy(dialog = null, event = state.conflict?.let(PurchaseDetailEvent::OpenPurchase))
    }

    fun onEventHandled() = local.update { it.copy(event = null) }
}

private val DateTimeText = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", PortugueseLocale)

@Composable
fun PurchaseDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenPurchase: (Purchase) -> Unit,
    onOpenTransaction: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PurchaseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.event) {
        when (val event = state.event) {
            PurchaseDetailEvent.Deleted -> onBack()
            is PurchaseDetailEvent.OpenPurchase -> onOpenPurchase(event.purchase)
            null -> return@LaunchedEffect
        }
        viewModel.onEventHandled()
    }
    val purchase = state.purchase
    PurchaseScaffold(
        title = stringResource(R.string.purchase_detail_title),
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (purchase?.status == PurchaseStatus.COMPLETED) {
                IconButton(onClick = { onEdit(purchase.id) }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.purchase_detail_edit))
                }
                IconButton(onClick = { viewModel.onShowDialog(PurchaseDetailDialog.DELETE) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            purchase == null -> Text(stringResource(R.string.purchase_detail_not_found), Modifier.padding(padding).padding(16.dp))
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                item {
                    ListItem(
                        overlineContent = { Text(stringResource(purchase.type.labelRes)) },
                        headlineContent = { Text(state.merchantName ?: stringResource(R.string.purchase_no_merchant)) },
                        supportingContent = {
                            val method = purchase.paymentMethod?.let { stringResource(it.labelRes) }
                            Text(
                                listOfNotNull(
                                    state.occurredAt?.format(DateTimeText),
                                    listOfNotNull(method, state.accountName).joinToString(" · ").ifEmpty { null },
                                    state.categoryName,
                                    purchase.notes?.takeIf { it.isNotBlank() },
                                ).joinToString("\n"),
                            )
                        },
                        trailingContent = {
                            Text(state.total.formatted(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        },
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = viewModel::onRepeat, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.purchase_detail_repeat))
                        }
                        val transactionId = purchase.transactionId
                        if (transactionId != null) {
                            OutlinedButton(onClick = { onOpenTransaction(transactionId) }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.purchase_detail_transaction))
                            }
                        }
                    }
                    HorizontalDivider()
                }
                items(state.items, key = { it.id }) { item ->
                    val unit = item.unit?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
                    val price = Money(item.unitPriceMinor, purchase.currency).formatted()
                    val discount = if (item.discountMinor > 0) {
                        " − " + Money(item.discountMinor, purchase.currency).formatted()
                    } else {
                        ""
                    }
                    ListItem(
                        headlineContent = { Text(item.name) },
                        supportingContent = { Text("${item.quantity.formattedQuantity()}$unit × $price$discount") },
                        trailingContent = { Text(Money(item.totalMinor, purchase.currency).formatted()) },
                    )
                }
            }
        }
    }

    when (state.dialog) {
        PurchaseDetailDialog.DELETE -> AlertDialog(
            onDismissRequest = { viewModel.onShowDialog(null) },
            title = { Text(stringResource(R.string.purchase_detail_delete_title)) },
            text = { Text(stringResource(R.string.purchase_detail_delete_message)) },
            confirmButton = { TextButton(onClick = viewModel::onConfirmDelete) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { viewModel.onShowDialog(null) }) { Text(stringResource(R.string.action_cancel)) } },
        )
        PurchaseDetailDialog.REPEAT_CONFLICT -> AlertDialog(
            onDismissRequest = { viewModel.onShowDialog(null) },
            title = { Text(stringResource(R.string.purchase_detail_repeat)) },
            text = { Text(stringResource(R.string.purchase_detail_repeat_conflict)) },
            confirmButton = { TextButton(onClick = viewModel::onOpenConflict) { Text(stringResource(R.string.purchase_detail_open_active)) } },
            dismissButton = { TextButton(onClick = { viewModel.onShowDialog(null) }) { Text(stringResource(R.string.action_cancel)) } },
        )
        null -> Unit
    }
}
