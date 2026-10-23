package pt.vcc.financeapp.feature.finance.purchases

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.math.BigDecimal
import pt.vcc.financeapp.core.data.usecase.CatalogValidationError
import pt.vcc.financeapp.core.model.AmountParser
import pt.vcc.financeapp.core.model.PurchaseType
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.PortugueseLocale

/** Scaffold comum aos ecrãs de Compras (os insets do sistema são tratados pelo Scaffold da app). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PurchaseScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = actions,
            )
        },
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        content = content,
    )
}

internal val PurchaseType.labelRes: Int
    get() = when (this) {
        PurchaseType.QUICK -> R.string.purchase_type_quick
        PurchaseType.PLANNED -> R.string.purchase_type_planned
        PurchaseType.EXPRESS -> R.string.purchase_type_express
    }

@Composable
internal fun CatalogValidationError.message(): String = stringResource(
    when (this) {
        CatalogValidationError.BLANK_NAME -> R.string.catalog_error_blank_name
        CatalogValidationError.NAME_TOO_LONG -> R.string.catalog_error_name_too_long
        CatalogValidationError.DUPLICATE_NAME -> R.string.catalog_error_duplicate
        CatalogValidationError.NEGATIVE_PRICE -> R.string.catalog_error_price
        CatalogValidationError.CATEGORY_NOT_FOUND -> R.string.catalog_error_category
        CatalogValidationError.FINANCE_CATEGORY_MISMATCH -> R.string.catalog_error_category
        CatalogValidationError.NOT_FOUND -> R.string.catalog_error_not_found
    },
)

/** Preço opcional escrito pelo utilizador: vazio → `null`; inválido → [Result.failure]. */
internal fun parseOptionalPrice(text: String): Result<Long?> {
    if (text.isBlank()) return Result.success(null)
    val value = AmountParser.parseOrNull(text, 2) ?: return Result.failure(IllegalArgumentException(text))
    return if (value < 0) Result.failure(IllegalArgumentException(text)) else Result.success(value)
}

/** Quantidade para mostrar/editar (ex.: `1,5`). */
internal fun BigDecimal.formattedQuantity(): String =
    stripTrailingZeros().toPlainString().replace('.', ',')

internal fun String.capitalized(): String = replaceFirstChar { it.titlecase(PortugueseLocale) }
