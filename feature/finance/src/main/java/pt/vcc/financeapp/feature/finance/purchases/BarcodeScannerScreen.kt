package pt.vcc.financeapp.feature.finance.purchases

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import pt.vcc.financeapp.core.data.usecase.PurchaseItemError
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.formatted

/** Lê códigos de barras com a câmara (ou escritos à mão) e adiciona os produtos à compra depois de confirmados. */
@Composable
fun BarcodeScannerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BarcodeScannerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
        denied = !granted
    }
    LaunchedEffect(state.message) {
        if (state.message == ScanMessage.ADDED) {
            delay(2_500)
            viewModel.onMessageShown()
        }
    }

    PurchaseScaffold(
        title = stringResource(R.string.scanner_title),
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
            BottomAppBar {
                Text(
                    stringResource(R.string.scanner_summary, state.itemCount, state.total.formatted()),
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                Button(onClick = onBack, modifier = Modifier.padding(end = 16.dp)) { Text(stringResource(R.string.scanner_done)) }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (hasCamera) {
                Box(Modifier.fillMaxWidth().height(280.dp).clip(MaterialTheme.shapes.medium)) {
                    BarcodeCameraPreview(
                        scanning = state.draft == null,
                        onCode = viewModel::onCodeDetected,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(stringResource(R.string.scanner_hint), style = MaterialTheme.typography.bodyMedium)
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.scanner_permission_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(if (denied) R.string.scanner_permission_denied else R.string.scanner_permission_message),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) {
                            Text(stringResource(R.string.scanner_permission_allow))
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.manualCode,
                    onValueChange = viewModel::onManualCodeChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.scanner_manual_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.onSubmitManualCode() }),
                )
                OutlinedButton(onClick = viewModel::onSubmitManualCode, enabled = state.manualCode.isNotEmpty()) {
                    Text(stringResource(R.string.scanner_lookup))
                }
            }
            when (state.message) {
                ScanMessage.INVALID_CODE -> Text(stringResource(R.string.scanner_invalid_code), color = MaterialTheme.colorScheme.error)
                ScanMessage.PURCHASE_NOT_FOUND -> Text(stringResource(R.string.purchase_not_found), color = MaterialTheme.colorScheme.error)
                ScanMessage.ADDED -> Text(
                    stringResource(R.string.scanner_added, state.lastAddedName.orEmpty()),
                    color = MaterialTheme.colorScheme.primary,
                )
                null -> Unit
            }
        }
    }

    state.draft?.let { ScanDraftDialog(it, state, viewModel) }
}

@Composable
private fun ScanDraftDialog(draft: ScanDraft, state: BarcodeScannerUiState, viewModel: BarcodeScannerViewModel) {
    fun error(vararg codes: PurchaseItemError) = codes.firstOrNull { it in draft.itemErrors }
    AlertDialog(
        onDismissRequest = viewModel::onDismissDraft,
        title = { Text(stringResource(if (draft.isNewProduct) R.string.scanner_new_product else R.string.scanner_confirm_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.scanner_code, draft.code), style = MaterialTheme.typography.bodySmall)
                if (draft.isNewProduct) {
                    val nameError = error(PurchaseItemError.BLANK_NAME, PurchaseItemError.NAME_TOO_LONG)
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { value -> viewModel.updateDraft { it.copy(name = value) } },
                        label = { Text(stringResource(R.string.purchase_item_name)) },
                        singleLine = true,
                        isError = nameError != null,
                        supportingText = nameError?.let { { Text(it.message()) } },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                } else {
                    Text(draft.name, style = MaterialTheme.typography.titleMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val priceError = error(PurchaseItemError.INVALID_PRICE)
                    OutlinedTextField(
                        value = draft.price,
                        onValueChange = viewModel::onPriceChange,
                        modifier = Modifier.weight(1.3f),
                        label = { Text(stringResource(R.string.purchase_item_price)) },
                        singleLine = true,
                        isError = priceError != null,
                        supportingText = priceError?.let { { Text(it.message()) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    val quantityError = error(PurchaseItemError.INVALID_QUANTITY)
                    OutlinedTextField(
                        value = draft.quantity,
                        onValueChange = { value -> viewModel.updateDraft { it.copy(quantity = value) } },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.purchase_item_quantity)) },
                        singleLine = true,
                        isError = quantityError != null,
                        supportingText = quantityError?.let { { Text(it.message()) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                if (draft.pendingListItems.isNotEmpty()) {
                    OptionPicker(
                        label = stringResource(R.string.scanner_list_item),
                        selected = draft.pendingListItems.find { it.id == draft.listItemId }?.name ?: stringResource(R.string.scanner_list_item_none),
                        options = listOf(stringResource(R.string.scanner_list_item_none) to { viewModel.onListItemChosen(null) }) +
                            draft.pendingListItems.map { item -> item.name to { viewModel.onListItemChosen(item) } },
                    )
                }
                if (draft.isNewProduct) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = draft.saveToCatalog,
                            onCheckedChange = { checked -> viewModel.updateDraft { it.copy(saveToCatalog = checked) } },
                        )
                        Text(stringResource(R.string.scanner_save_to_catalog))
                    }
                    if (draft.saveToCatalog) {
                        OptionPicker(
                            label = stringResource(R.string.scanner_category),
                            selected = state.categories.find { it.id == draft.productCategoryId }?.name ?: stringResource(R.string.scanner_no_category),
                            options = listOf(stringResource(R.string.scanner_no_category) to { viewModel.updateDraft { it.copy(productCategoryId = null) } }) +
                                state.categories.map { category -> category.name to { viewModel.updateDraft { it.copy(productCategoryId = category.id) } } },
                        )
                    }
                }
                draft.catalogErrors.firstOrNull()?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
                error(PurchaseItemError.PURCHASE_CANCELLED, PurchaseItemError.PURCHASE_NOT_FOUND)?.let {
                    Text(it.message(), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::onConfirm, enabled = !draft.isSaving) { Text(stringResource(R.string.purchase_item_add_button)) }
        },
        dismissButton = { TextButton(onClick = viewModel::onDismissDraft) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun OptionPicker(label: String, selected: String, options: List<Pair<String, () -> Unit>>) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selected) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (text, action) ->
                    DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; action() })
                }
            }
        }
    }
}
