package pt.vcc.financeapp.feature.finance.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pt.vcc.financeapp.feature.finance.accounts.AccountsScreen
import pt.vcc.financeapp.feature.finance.dashboard.DashboardScreen
import pt.vcc.financeapp.feature.finance.reports.ReportsScreen
import pt.vcc.financeapp.feature.finance.settings.SettingsScreen
import pt.vcc.financeapp.feature.finance.transactions.TransactionsScreen

/**
 * Regista os destinos financeiros principais no grafo de navegação da app.
 * A navegação entre separadores é decidida pela app ([onNavigateToAccounts]).
 */
fun NavGraphBuilder.financeGraph(onNavigateToAccounts: () -> Unit) {
    composable<DashboardRoute> {
        DashboardScreen(onAccountsClick = onNavigateToAccounts)
    }
    composable<AccountsRoute> { AccountsScreen() }
    composable<TransactionsRoute> { TransactionsScreen() }
    composable<ReportsRoute> { ReportsScreen() }
    composable<SettingsRoute> { SettingsScreen() }
}
