package pt.vcc.financeapp.feature.finance.purchases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.data.repository.CatalogRepository
import pt.vcc.financeapp.core.data.repository.CategoryRepository
import pt.vcc.financeapp.core.data.usecase.CatalogSaveResult
import pt.vcc.financeapp.core.data.usecase.CatalogValidationError
import pt.vcc.financeapp.core.data.usecase.ProductCategoryInput
import pt.vcc.financeapp.core.data.usecase.ProductInput
import pt.vcc.financeapp.core.data.usecase.SaveProductCategoryUseCase
import pt.vcc.financeapp.core.data.usecase.SaveProductUseCase
import pt.vcc.financeapp.core.designsystem.components.EmptyState
import pt.vcc.financeapp.core.model.Merchant
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.Product
import pt.vcc.financeapp.core.model.ProductCategory
import pt.vcc.financeapp.core.model.TransactionType
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.transactions.CategoryOption
import pt.vcc.financeapp.feature.finance.transactions.toOptions
import pt.vcc.financeapp.feature.finance.ui.DropdownField
import pt.vcc.financeapp.feature.finance.ui.LoadingState
import pt.vcc.financeapp.feature.finance.ui.formatAmountInput
import pt.vcc.financeapp.feature.finance.ui.formatted

enum class CatalogTab { CATEGORIES, PRODUCTS }

data class CategoryDialogState(
    val id: String? = null,
    val name: String = "",
    val financeCategoryId: String? = null,
    val isActive: Boolean = true,
    val errors: Set<CatalogValidationError> = emptySet(),
)

data class ProductDialogState(
    val id: String? = null,
    val name: String = "",
    val categoryId: String? = null,
    val price: String = "",
    val unit: String = "",
    val barcode: String = "",
    val isFavorite: Boolean = false,
    val isActive: Boolean = true,
    val errors: Set<CatalogValidationError> = emptySet(),
)

data class CatalogUiState(
    val isLoading: Boolean = true,
    val tab: CatalogTab = CatalogTab.PRODUCTS,
    val query: String = "",
    val categories: List<ProductCategory> = emptyList(),
    val products: List<Product> = emptyList(),
    val financeCategories: List<CategoryOption> = emptyList(),
    val categoryDialog: CategoryDialogState? = null,
    val productDialog: ProductDialogState? = null,
    val error: String? = null,
) {
    val categoryNames: Map<String, String> get() = categories.associate { it.id to it.name }
}

private data class CatalogDialogs(val category: CategoryDialogState? = null, val product: ProductDialogState? = null)
private data class CatalogFilter(val tab: CatalogTab = CatalogTab.PRODUCTS, val query: String = "")

@HiltViewModel
class CatalogViewModel @Inject constructor(
    catalogRepository: CatalogRepository,
    categoryRepository: CategoryRepository,
    private val saveCategory: SaveProductCategoryUseCase,
    private val saveProduct: SaveProductUseCase,
) : ViewModel() {
    private val filter = MutableStateFlow(CatalogFilter())
    private val dialogs = MutableStateFlow(CatalogDialogs())

    val uiState: StateFlow<CatalogUiState> = combine(
        catalogRepository.observeCategories(),
        catalogRepository.observeProducts(),
        categoryRepository.observeAllCategories(),
        filter,
        dialogs,
    ) { categories, products, financeCategories, f, d ->
        val key = Merchant.normalize(f.query)
        CatalogUiState(
            isLoading = false,
            tab = f.tab,
            query = f.query,
            categories = categories.filter { key.isEmpty() || key in Merchant.normalize(it.name) },
            products = products.filter { key.isEmpty() || key in Merchant.normalize(it.name) || it.barcode == f.query.trim() },
            financeCategories = financeCategories.filter { it.type == TransactionType.EXPENSE }
                .toOptions(d.category?.financeCategoryId),
            categoryDialog = d.category,
            productDialog = d.product,
        )
    }
        .catch { emit(CatalogUiState(isLoading = false, error = it.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState())

    fun onTabChange(tab: CatalogTab) = filter.update { it.copy(tab = tab) }
    fun onQueryChange(value: String) = filter.update { it.copy(query = value) }
    fun onDismiss() = dialogs.update { CatalogDialogs() }

    fun onAdd() = dialogs.update {
        if (filter.value.tab == CatalogTab.CATEGORIES) CatalogDialogs(category = CategoryDialogState())
        else CatalogDialogs(product = ProductDialogState())
    }

    fun onEditCategory(category: ProductCategory) = dialogs.update {
        CatalogDialogs(category = CategoryDialogState(category.id, category.name, category.financeCategoryId, category.isActive))
    }

    fun onEditProduct(product: Product) = dialogs.update {
        CatalogDialogs(
            product = ProductDialogState(
                id = product.id,
                name = product.name,
                categoryId = product.productCategoryId,
                price = product.defaultPriceMinor?.let { formatAmountInput(it, 2) }.orEmpty(),
                unit = product.unit.orEmpty(),
                barcode = product.barcode.orEmpty(),
                isFavorite = product.isFavorite,
                isActive = product.isActive,
            ),
        )
    }

    fun updateCategory(transform: (CategoryDialogState) -> CategoryDialogState) =
        dialogs.update { d -> d.copy(category = d.category?.let { transform(it).copy(errors = emptySet()) }) }

    fun updateProduct(transform: (ProductDialogState) -> ProductDialogState) =
        dialogs.update { d -> d.copy(product = d.product?.let { transform(it).copy(errors = emptySet()) }) }

    fun onSaveCategory() {
        val current = dialogs.value.category ?: return
        viewModelScope.launch {
            val result = saveCategory(ProductCategoryInput(current.id, current.name, current.financeCategoryId, current.isActive))
            when (result) {
                is CatalogSaveResult.Saved -> onDismiss()
                is CatalogSaveResult.Invalid -> dialogs.update { it.copy(category = it.category?.copy(errors = result.errors)) }
            }
        }
    }

    fun onSaveProduct() {
        val current = dialogs.value.product ?: return
        val price = parseOptionalPrice(current.price).getOrElse {
            dialogs.update { it.copy(product = it.product?.copy(errors = setOf(CatalogValidationError.NEGATIVE_PRICE))) }
            return
        }
        viewModelScope.launch {
            val result = saveProduct(
                ProductInput(
                    id = current.id,
                    name = current.name,
                    productCategoryId = current.categoryId,
                    defaultPriceMinor = price,
                    unit = current.unit,
                    barcode = current.barcode,
                    isFavorite = current.isFavorite,
                    isActive = current.isActive,
                ),
            )
            when (result) {
                is CatalogSaveResult.Saved -> onDismiss()
                is CatalogSaveResult.Invalid -> dialogs.update { it.copy(product = it.product?.copy(errors = result.errors)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PurchaseScaffold(
        title = stringResource(R.string.catalog_title),
        onBack = onBack,
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onAdd) {
                val label = if (state.tab == CatalogTab.CATEGORIES) R.string.catalog_add_category else R.string.catalog_add_product
                Icon(Icons.Filled.Add, contentDescription = stringResource(label))
            }
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@PurchaseScaffold
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                CatalogTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = state.tab == tab,
                        onClick = { viewModel.onTabChange(tab) },
                        shape = SegmentedButtonDefaults.itemShape(index, CatalogTab.entries.size),
                    ) {
                        Text(stringResource(if (tab == CatalogTab.CATEGORIES) R.string.catalog_tab_categories else R.string.catalog_tab_products))
                    }
                }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                label = { Text(stringResource(R.string.action_search)) },
            )
            when (state.tab) {
                CatalogTab.CATEGORIES -> CategoryList(state, viewModel::onEditCategory)
                CatalogTab.PRODUCTS -> ProductList(state, viewModel::onEditProduct)
            }
        }
    }
    state.categoryDialog?.let { CategoryDialog(it, state.financeCategories, viewModel) }
    state.productDialog?.let { ProductDialog(it, state.categories, viewModel) }
}

@Composable
private fun CategoryList(state: CatalogUiState, onClick: (ProductCategory) -> Unit) {
    if (state.categories.isEmpty()) {
        EmptyState(stringResource(R.string.catalog_tab_categories), stringResource(R.string.catalog_empty))
        return
    }
    val financeNames = state.financeCategories.associate { it.id to it.label }
    LazyColumn {
        items(state.categories, key = { it.id }) { category ->
            ListItem(
                modifier = Modifier.clickable { onClick(category) },
                headlineContent = { Text(category.name, color = activeColor(category.isActive)) },
                supportingContent = {
                    Text(
                        listOfNotNull(
                            category.financeCategoryId?.let(financeNames::get),
                            stringResource(R.string.label_inactive).takeUnless { category.isActive },
                        ).joinToString(" · ").ifEmpty { stringResource(R.string.catalog_no_finance_category) },
                    )
                },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ProductList(state: CatalogUiState, onClick: (Product) -> Unit) {
    if (state.products.isEmpty()) {
        EmptyState(stringResource(R.string.catalog_tab_products), stringResource(R.string.catalog_empty))
        return
    }
    val names = state.categoryNames
    LazyColumn {
        items(state.products, key = { it.id }) { product ->
            ListItem(
                modifier = Modifier.clickable { onClick(product) },
                leadingContent = if (product.isFavorite) {
                    { Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.catalog_favorite)) }
                } else null,
                headlineContent = { Text(product.name, color = activeColor(product.isActive)) },
                supportingContent = {
                    Text(
                        listOfNotNull(
                            product.productCategoryId?.let(names::get),
                            product.unit,
                            stringResource(R.string.label_inactive).takeUnless { product.isActive },
                        ).joinToString(" · "),
                    )
                },
                trailingContent = product.defaultPriceMinor?.let { price ->
                    { Text(Money(price, Money.DEFAULT_CURRENCY).formatted()) }
                },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun activeColor(isActive: Boolean) =
    if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline

@Composable
private fun CategoryDialog(dialog: CategoryDialogState, financeCategories: List<CategoryOption>, viewModel: CatalogViewModel) {
    val none = stringResource(R.string.catalog_no_finance_category)
    AlertDialog(
        onDismissRequest = viewModel::onDismiss,
        title = { Text(stringResource(if (dialog.id == null) R.string.catalog_add_category else R.string.catalog_edit_category)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dialog.name,
                    onValueChange = { value -> viewModel.updateCategory { it.copy(name = value) } },
                    label = { Text(stringResource(R.string.label_name)) },
                    singleLine = true,
                    isError = dialog.errors.any { it != CatalogValidationError.FINANCE_CATEGORY_MISMATCH },
                    supportingText = dialog.errors.firstOrNull { it != CatalogValidationError.FINANCE_CATEGORY_MISMATCH }
                        ?.let { error -> { Text(error.message()) } },
                )
                DropdownField(
                    label = stringResource(R.string.catalog_finance_category),
                    selectedText = financeCategories.find { it.id == dialog.financeCategoryId }?.label ?: none,
                    options = listOf<Pair<String?, String>>(null to none) + financeCategories.map { it.id to it.label },
                    onSelect = { id -> viewModel.updateCategory { it.copy(financeCategoryId = id) } },
                    supportingText = stringResource(R.string.catalog_finance_category_help),
                    isError = CatalogValidationError.FINANCE_CATEGORY_MISMATCH in dialog.errors,
                )
                ActiveSwitch(dialog.isActive) { value -> viewModel.updateCategory { it.copy(isActive = value) } }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::onSaveCategory) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = viewModel::onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ProductDialog(dialog: ProductDialogState, categories: List<ProductCategory>, viewModel: CatalogViewModel) {
    val none = stringResource(R.string.catalog_no_category)
    val nameErrors = dialog.errors - setOf(CatalogValidationError.NEGATIVE_PRICE, CatalogValidationError.CATEGORY_NOT_FOUND)
    AlertDialog(
        onDismissRequest = viewModel::onDismiss,
        title = { Text(stringResource(if (dialog.id == null) R.string.catalog_add_product else R.string.catalog_edit_product)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dialog.name,
                    onValueChange = { value -> viewModel.updateProduct { it.copy(name = value) } },
                    label = { Text(stringResource(R.string.label_name)) },
                    singleLine = true,
                    isError = nameErrors.isNotEmpty(),
                    supportingText = nameErrors.firstOrNull()?.let { error -> { Text(error.message()) } },
                )
                DropdownField(
                    label = stringResource(R.string.catalog_product_category),
                    selectedText = categories.find { it.id == dialog.categoryId }?.name ?: none,
                    options = listOf<Pair<String?, String>>(null to none) +
                        categories.filter { it.isActive || it.id == dialog.categoryId }.map { it.id to it.name },
                    onSelect = { id -> viewModel.updateProduct { it.copy(categoryId = id) } },
                    isError = CatalogValidationError.CATEGORY_NOT_FOUND in dialog.errors,
                )
                OutlinedTextField(
                    value = dialog.price,
                    onValueChange = { value -> viewModel.updateProduct { it.copy(price = value) } },
                    label = { Text(stringResource(R.string.catalog_default_price)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = CatalogValidationError.NEGATIVE_PRICE in dialog.errors,
                    supportingText = if (CatalogValidationError.NEGATIVE_PRICE in dialog.errors) {
                        { Text(stringResource(R.string.catalog_error_price)) }
                    } else null,
                )
                OutlinedTextField(
                    value = dialog.unit,
                    onValueChange = { value -> viewModel.updateProduct { it.copy(unit = value) } },
                    label = { Text(stringResource(R.string.purchase_item_unit)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = dialog.barcode,
                    onValueChange = { value -> viewModel.updateProduct { it.copy(barcode = value) } },
                    label = { Text(stringResource(R.string.catalog_barcode)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.catalog_favorite), Modifier.weight(1f))
                    Switch(checked = dialog.isFavorite, onCheckedChange = { value -> viewModel.updateProduct { it.copy(isFavorite = value) } })
                }
                ActiveSwitch(dialog.isActive) { value -> viewModel.updateProduct { it.copy(isActive = value) } }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::onSaveProduct) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = viewModel::onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun ActiveSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.label_active), Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
