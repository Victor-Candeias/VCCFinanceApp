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

@Serializable
data object ReportsRoute

@Serializable
data object SettingsRoute
