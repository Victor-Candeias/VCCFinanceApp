package pt.vcc.financeapp.feature.finance.navigation

import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute

@Serializable
data object AccountsRoute

/** Editor de conta; [accountId] nulo cria uma conta nova. */
@Serializable
data class AccountEditorRoute(val accountId: String? = null)

@Serializable
data object TransactionsRoute

/** Editor de receita/despesa; [transactionId] nulo cria um movimento novo. */
@Serializable
data class TransactionEditorRoute(val transactionId: String? = null)

/**
 * Editor de transferência interna; [transferGroupId] nulo cria uma transferência nova.
 * [withdrawal] abre uma transferência nova em modo levantamento (destino: conta Dinheiro).
 */
@Serializable
data class TransferEditorRoute(val transferGroupId: String? = null, val withdrawal: Boolean = false)

@Serializable
data object ReportsRoute

@Serializable
data object SettingsRoute

@Serializable
data object SecuritySettingsRoute

@Serializable
data object ChangePinRoute

@Serializable
data object ImportRoute

@Serializable
data object ExportRoute

@Serializable
data object BackupRoute

@Serializable
data object BudgetsRoute

/** Editor de orçamento; [budgetId] nulo cria um orçamento novo. */
@Serializable
data class BudgetEditorRoute(val budgetId: String? = null)
@Serializable
data object PurchasesRoute

@Serializable
data object MerchantsRoute

@Serializable
data object CatalogRoute

@Serializable
data class PurchaseRoute(val purchaseId: String)

@Serializable
data object ShoppingListsRoute

@Serializable
data class ShoppingListRoute(val listId: String)

@Serializable
data class ExpressPurchaseRoute(val purchaseId: String)

@Serializable
data object PurchaseHistoryRoute

@Serializable
data class PurchaseDetailRoute(val purchaseId: String)
