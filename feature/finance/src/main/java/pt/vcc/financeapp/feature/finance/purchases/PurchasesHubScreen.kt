package pt.vcc.financeapp.feature.finance.purchases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.data.usecase.GetOrCreateActivePurchaseUseCase
import pt.vcc.financeapp.core.model.PurchaseType
import pt.vcc.financeapp.core.data.repository.MerchantRepository
import pt.vcc.financeapp.core.data.repository.PurchaseRepository
import pt.vcc.financeapp.core.data.repository.ShoppingListRepository
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.LoadingState
import pt.vcc.financeapp.feature.finance.ui.formatted

/** Compra em curso pronta a retomar no hub. */
data class ActivePurchaseItem(val purchase: Purchase, val merchantName: String?, val listName: String?) {
    val total: Money get() = Money(purchase.totalMinor, purchase.currency)
}

data class PurchasesHubUiState(
    val isLoading: Boolean = true,
    val active: List<ActivePurchaseItem> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class PurchasesHubViewModel @Inject constructor(
    purchaseRepository: PurchaseRepository,
    merchantRepository: MerchantRepository,
    shoppingListRepository: ShoppingListRepository,
    private val getOrCreateActive: GetOrCreateActivePurchaseUseCase,
) : ViewModel() {
    /** Retoma a compra em curso do [type] (ou cria uma) e devolve o id em [onReady]. */
    fun start(type: PurchaseType, onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(getOrCreateActive(type).id) }
    }

    val uiState: StateFlow<PurchasesHubUiState> = combine(
        purchaseRepository.observeAllActive(),
        merchantRepository.observeAll(),
        shoppingListRepository.observeLists(),
    ) { active, merchants, lists ->
        val merchantNames = merchants.associate { it.id to it.name }
        val listNames = lists.associate { it.id to it.name }
        PurchasesHubUiState(
            isLoading = false,
            active = active.map { ActivePurchaseItem(it, it.merchantId?.let(merchantNames::get), it.shoppingListId?.let(listNames::get)) },
        )
    }
        .catch { emit(PurchasesHubUiState(isLoading = false, error = it.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PurchasesHubUiState())
}

/** Ponto de entrada das Compras: três modos, compras em curso e gestão de dados base. */
@Composable
fun PurchasesHubScreen(
    onBack: () -> Unit,
    onOpenPurchase: (String) -> Unit,
    onShoppingLists: () -> Unit,
    onExpressPurchase: () -> Unit,
    onResume: (Purchase) -> Unit,
    onHistory: () -> Unit,
    onMerchants: () -> Unit,
    onCatalog: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PurchasesHubViewModel = hiltViewModel(),
) {
    val onQuickPurchase = { viewModel.start(PurchaseType.QUICK, onOpenPurchase) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PurchaseScaffold(stringResource(R.string.purchases_title), onBack, modifier) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@PurchaseScaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.active.isNotEmpty()) {
                Text(stringResource(R.string.purchases_in_progress), style = MaterialTheme.typography.titleMedium)
                state.active.forEach { item ->
                    Card(Modifier.fillMaxWidth().clickable { onResume(item.purchase) }) {
                        ListItem(
                            headlineContent = { Text(stringResource(item.purchase.type.labelRes)) },
                            supportingContent = {
                                Text(listOfNotNull(item.listName, item.merchantName).joinToString(" · ").ifEmpty { stringResource(R.string.purchases_resume) })
                            },
                            trailingContent = { Text(item.total.formatted(), style = MaterialTheme.typography.titleMedium) },
                        )
                    }
                }
            }
            Text(stringResource(R.string.purchases_start), style = MaterialTheme.typography.titleMedium)
            ModeCard(Icons.Filled.ShoppingCart, R.string.purchase_type_quick, R.string.purchase_type_quick_description, onQuickPurchase)
            ModeCard(Icons.AutoMirrored.Filled.List, R.string.purchase_type_planned, R.string.purchase_type_planned_description, onShoppingLists)
            ModeCard(Icons.Filled.Star, R.string.purchase_type_express, R.string.purchase_type_express_description, onExpressPurchase)
            HorizontalDivider()
            HubLink(Icons.Filled.DateRange, R.string.purchase_history_title, onHistory)
            HubLink(Icons.Filled.Place, R.string.merchants_title, onMerchants)
            HubLink(Icons.Filled.Edit, R.string.catalog_title, onCatalog)
        }
    }
}

@Composable
private fun ModeCard(icon: ImageVector, title: Int, description: Int, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ListItem(
            leadingContent = { Icon(icon, contentDescription = null) },
            headlineContent = { Text(stringResource(title)) },
            supportingContent = { Text(stringResource(description)) },
        )
    }
}

@Composable
private fun HubLink(icon: ImageVector, title: Int, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(stringResource(title)) },
    )
}
