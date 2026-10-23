package pt.vcc.financeapp.feature.finance.purchases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import pt.vcc.financeapp.core.data.repository.AccountRepository
import pt.vcc.financeapp.core.data.repository.MerchantRepository
import pt.vcc.financeapp.core.data.repository.PurchaseRepository
import pt.vcc.financeapp.core.data.usecase.HistoryPeriod
import pt.vcc.financeapp.core.data.usecase.PurchaseHistoryFilter
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.PaymentMethod
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.core.model.PurchaseType
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.LoadingState
import pt.vcc.financeapp.feature.finance.ui.formatted

data class HistoryEntry(
    val purchase: Purchase,
    val date: LocalDate,
    val merchantName: String?,
    val accountName: String?,
) {
    val total: Money get() = Money(purchase.totalMinor, purchase.currency)
}

data class NamedOption(val id: String, val name: String)

data class PurchaseHistoryUiState(
    val isLoading: Boolean = true,
    val filter: PurchaseHistoryFilter = PurchaseHistoryFilter(),
    val entries: List<HistoryEntry> = emptyList(),
    val accounts: List<NamedOption> = emptyList(),
    val merchants: List<NamedOption> = emptyList(),
    val error: String? = null,
) {
    /** Soma por moeda dos resultados filtrados. */
    val totals: List<Money>
        get() = entries.groupBy { it.purchase.currency }.map { (currency, list) -> Money(list.sumOf { it.purchase.totalMinor }, currency) }
}

/** Histórico das compras concluídas (mais recentes primeiro) com filtros por tipo, pagamento, conta, estabelecimento e período. */
@HiltViewModel
class PurchaseHistoryViewModel @Inject constructor(
    purchaseRepository: PurchaseRepository,
    merchantRepository: MerchantRepository,
    accountRepository: AccountRepository,
    private val clock: Clock,
) : ViewModel() {
    private val filter = MutableStateFlow(PurchaseHistoryFilter())

    val uiState: StateFlow<PurchaseHistoryUiState> = combine(
        purchaseRepository.observeCompleted(),
        merchantRepository.observeAll(),
        accountRepository.observeAllAccounts(),
        filter,
    ) { purchases, merchants, accounts, filter ->
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val merchantNames = merchants.associate { it.id to it.name }
        val accountNames = accounts.associate { it.id to it.name }
        PurchaseHistoryUiState(
            isLoading = false,
            filter = filter,
            entries = purchases
                .filter { filter.matches(it, today, zone) }
                .sortedByDescending { it.occurredAt }
                .map { HistoryEntry(it, it.occurredAt.atZone(zone).toLocalDate(), it.merchantId?.let(merchantNames::get), it.accountId?.let(accountNames::get)) },
            accounts = purchases.mapNotNull { it.accountId }.distinct()
                .map { NamedOption(it, accountNames[it] ?: it) }.sortedBy { it.name.lowercase() },
            merchants = purchases.mapNotNull { it.merchantId }.distinct()
                .map { NamedOption(it, merchantNames[it] ?: it) }.sortedBy { it.name.lowercase() },
        )
    }
        .catch { emit(PurchaseHistoryUiState(isLoading = false, error = it.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PurchaseHistoryUiState())

    fun onFilterChange(transform: (PurchaseHistoryFilter) -> PurchaseHistoryFilter) = filter.update(transform)

    fun onClearFilters() = filter.update { PurchaseHistoryFilter() }
}

@Composable
fun PurchaseHistoryScreen(
    onBack: () -> Unit,
    onOpenPurchase: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PurchaseHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PurchaseScaffold(
        title = stringResource(R.string.purchase_history_title),
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (state.filter.isActive) TextButton(onClick = viewModel::onClearFilters) { Text(stringResource(R.string.action_clear)) }
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@PurchaseScaffold
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            HistoryFilters(state, viewModel::onFilterChange)
            if (state.entries.isNotEmpty()) {
                Text(
                    stringResource(R.string.purchase_history_summary, state.entries.size, state.totals.joinToString(" + ") { it.formatted() }),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            HorizontalDivider()
            if (state.entries.isEmpty()) {
                Text(
                    stringResource(if (state.filter.isActive) R.string.purchase_history_no_results else R.string.purchase_history_empty),
                    Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.entries, key = { it.purchase.id }) { entry ->
                        ListItem(
                            modifier = Modifier.clickable { onOpenPurchase(entry.purchase.id) },
                            overlineContent = { Text("${entry.date.formatted()} · ${stringResource(entry.purchase.type.labelRes)}") },
                            headlineContent = { Text(entry.merchantName ?: stringResource(R.string.purchase_no_merchant)) },
                            supportingContent = {
                                val method = entry.purchase.paymentMethod?.let { stringResource(it.labelRes) }
                                Text(listOfNotNull(method, entry.accountName).joinToString(" · "))
                            },
                            trailingContent = { Text(entry.total.formatted(), style = MaterialTheme.typography.titleMedium) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryFilters(state: PurchaseHistoryUiState, onChange: ((PurchaseHistoryFilter) -> PurchaseHistoryFilter) -> Unit) {
    val filter = state.filter
    val all = stringResource(R.string.purchase_history_all)
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterMenu(
            label = stringResource(R.string.purchase_history_period) + ": " + stringResource(filter.period.labelRes),
            selected = filter.period != HistoryPeriod.ALL,
            options = HistoryPeriod.entries.map { stringResource(it.labelRes) to { onChange { f -> f.copy(period = it) } } },
        )
        FilterMenu(
            label = filter.type?.let { stringResource(it.labelRes) } ?: stringResource(R.string.purchase_history_type),
            selected = filter.type != null,
            options = listOf(all to { onChange { it.copy(type = null) } }) +
                PurchaseType.entries.map { stringResource(it.labelRes) to { onChange { f -> f.copy(type = it) } } },
        )
        FilterMenu(
            label = filter.method?.let { stringResource(it.labelRes) } ?: stringResource(R.string.purchase_history_method),
            selected = filter.method != null,
            options = listOf(all to { onChange { it.copy(method = null) } }) +
                PaymentMethod.entries.map { stringResource(it.labelRes) to { onChange { f -> f.copy(method = it) } } },
        )
        FilterMenu(
            label = state.accounts.find { it.id == filter.accountId }?.name ?: stringResource(R.string.purchase_history_account),
            selected = filter.accountId != null,
            options = listOf(all to { onChange { it.copy(accountId = null) } }) +
                state.accounts.map { option -> option.name to { onChange { it.copy(accountId = option.id) } } },
        )
        FilterMenu(
            label = state.merchants.find { it.id == filter.merchantId }?.name ?: stringResource(R.string.purchase_history_merchant),
            selected = filter.merchantId != null,
            options = listOf(all to { onChange { it.copy(merchantId = null) } }) +
                state.merchants.map { option -> option.name to { onChange { it.copy(merchantId = option.id) } } },
        )
    }
}

@Composable
private fun FilterMenu(label: String, selected: Boolean, options: List<Pair<String, () -> Unit>>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = selected, onClick = { expanded = true }, label = { Text(label) })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (text, action) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; action() })
            }
        }
    }
}

private val HistoryPeriod.labelRes: Int
    get() = when (this) {
        HistoryPeriod.ALL -> R.string.purchase_history_period_all
        HistoryPeriod.THIS_MONTH -> R.string.purchase_history_period_this_month
        HistoryPeriod.LAST_MONTH -> R.string.purchase_history_period_last_month
        HistoryPeriod.LAST_90_DAYS -> R.string.purchase_history_period_90_days
        HistoryPeriod.THIS_YEAR -> R.string.purchase_history_period_this_year
    }
