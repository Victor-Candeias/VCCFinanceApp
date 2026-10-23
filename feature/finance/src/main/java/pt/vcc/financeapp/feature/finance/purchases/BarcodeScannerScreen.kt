package pt.vcc.financeapp.feature.finance.purchases

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.UseCase
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.data.ocr.PriceKind
import pt.vcc.financeapp.core.data.usecase.PurchaseItemError
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.core.model.Money
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
    val scope = rememberCoroutineScope()
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    val cameraUseCases = remember { listOf<UseCase>(imageCapture) }
    val textReader = remember { LabelTextReader() }
    DisposableEffect(textReader) { onDispose { textReader.close() } }
    val readPrice: () -> Unit = {
        viewModel.onPriceCaptureStarted()
        scope.launch {
            runCatching { textReader.read(imageCapture, ContextCompat.getMainExecutor(context)) }
                .onSuccess(viewModel::onPriceTextRead)
                .onFailure { viewModel.onPriceCaptureFailed() }
        }
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
                        useCases = cameraUseCases,
                    )
                }
                val reading = state.priceReading
                if (reading != null && reading.candidates == null) {
                    PriceCaptureControls(reading, onCapture = readPrice, onCancel = viewModel::onCancelPriceReading)
                } else {
                    Text(stringResource(R.string.scanner_hint), style = MaterialTheme.typography.bodyMedium)
                }
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

    val draft = state.draft
    val reading = state.priceReading
    when {
        draft == null -> Unit
        reading == null -> ScanDraftDialog(draft, state, viewModel, canReadPrice = hasCamera)
        reading.candidates != null -> PriceChoiceDialog(reading, draft, state.total.currency, viewModel)
    }
}

@Composable
private fun PriceCaptureControls(reading: PriceReading, onCapture: () -> Unit, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.scanner_ocr_hint), style = MaterialTheme.typography.bodyMedium)
        if (reading.failed) Text(stringResource(R.string.scanner_ocr_failed), color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCapture, enabled = !reading.capturing) {
                Text(stringResource(if (reading.capturing) R.string.scanner_ocr_reading else R.string.scanner_ocr_capture))
            }
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

/** Candidatos lidos da etiqueta: o utilizador escolhe explicitamente o preço (e, num produto novo, o nome). */
@Composable
private fun PriceChoiceDialog(reading: PriceReading, draft: ScanDraft, currency: String, viewModel: BarcodeScannerViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::onCancelPriceReading,
        title = { Text(stringResource(R.string.scanner_ocr_choose_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val candidates = reading.candidates.orEmpty().take(MAX_PRICE_CANDIDATES)
                if (candidates.isEmpty()) Text(stringResource(R.string.scanner_ocr_no_price))
                candidates.forEach { candidate ->
                    val label = "${Money(candidate.cents, currency).formatted()} · ${stringResource(candidate.kind.label())}" +
                        if (candidate == reading.suggested) " · ${stringResource(R.string.scanner_ocr_suggested)}" else ""
                    val choose = { viewModel.onPriceCandidateChosen(candidate) }
                    if (candidate == reading.suggested) {
                        Button(onClick = choose, modifier = Modifier.fillMaxWidth()) { Text(label) }
                    } else {
                        OutlinedButton(onClick = choose, modifier = Modifier.fillMaxWidth()) { Text(label) }
                    }
                }
                if (draft.isNewProduct && reading.names.isNotEmpty()) {
                    Text(stringResource(R.string.scanner_ocr_names), style = MaterialTheme.typography.labelMedium)
                    reading.names.forEach { name ->
                        TextButton(onClick = { viewModel.onNameCandidateChosen(name) }) { Text(name) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::onRetryPriceReading) { Text(stringResource(R.string.scanner_ocr_retry)) } },
        dismissButton = { TextButton(onClick = viewModel::onCancelPriceReading) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val MAX_PRICE_CANDIDATES = 6

private fun PriceKind.label(): Int = when (this) {
    PriceKind.SALE -> R.string.price_kind_sale
    PriceKind.OLD -> R.string.price_kind_old
    PriceKind.PER_KG -> R.string.price_kind_per_kg
    PriceKind.PER_L -> R.string.price_kind_per_l
    PriceKind.DISCOUNT -> R.string.price_kind_discount
    PriceKind.UNKNOWN -> R.string.price_kind_unknown
}

@Composable
private fun ScanDraftDialog(draft: ScanDraft, state: BarcodeScannerUiState, viewModel: BarcodeScannerViewModel, canReadPrice: Boolean) {
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
                if (canReadPrice) {
                    TextButton(onClick = viewModel::onReadPrice) { Text(stringResource(R.string.scanner_read_price)) }
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
