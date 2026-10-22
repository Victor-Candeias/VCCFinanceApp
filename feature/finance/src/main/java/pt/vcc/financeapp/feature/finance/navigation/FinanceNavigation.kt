package pt.vcc.financeapp.feature.finance.navigation

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.accounts.AccountEditorScreen
import pt.vcc.financeapp.feature.finance.authentication.ChangePinScreen
import pt.vcc.financeapp.feature.finance.authentication.SecuritySettingsScreen
import pt.vcc.financeapp.feature.finance.accounts.AccountsScreen
import pt.vcc.financeapp.feature.finance.dashboard.DashboardScreen
import pt.vcc.financeapp.feature.finance.importexport.ImportScreen
import pt.vcc.financeapp.feature.finance.reports.ReportsScreen
import pt.vcc.financeapp.feature.finance.settings.SettingsEntry
import pt.vcc.financeapp.feature.finance.settings.SettingsScreen
import pt.vcc.financeapp.feature.finance.transactions.TransactionEditorScreen
import pt.vcc.financeapp.feature.finance.transactions.TransactionsScreen
import pt.vcc.financeapp.feature.finance.transactions.TransferEditorScreen

/**
 * Regista os destinos financeiros no grafo de navegação da app. A navegação entre separadores é
 * decidida pela app ([onNavigateToAccounts]); os ecrãs de detalhe usam [navController].
 */
fun NavGraphBuilder.financeGraph(
    navController: NavController,
    appVersion: String,
    onNavigateToAccounts: () -> Unit,
) {
    composable<DashboardRoute> {
        DashboardScreen(onAccountsClick = onNavigateToAccounts)
    }
    composable<AccountsRoute> {
        AccountsScreen(
            onAddAccount = { navController.navigate(AccountEditorRoute()) },
            onAccountClick = { navController.navigate(AccountEditorRoute(it)) },
        )
    }
    composable<AccountEditorRoute> {
        AccountEditorScreen(onBack = { navController.popBackStack() })
    }
    composable<TransactionsRoute> {
        TransactionsScreen(
            onAddTransaction = { navController.navigate(TransactionEditorRoute()) },
            onAddTransfer = { navController.navigate(TransferEditorRoute()) },
            onTransactionClick = { tx ->
                val groupId = tx.transferGroupId
                if (groupId != null) {
                    navController.navigate(TransferEditorRoute(groupId))
                } else {
                    navController.navigate(TransactionEditorRoute(tx.id))
                }
            },
        )
    }
    composable<TransactionEditorRoute> {
        TransactionEditorScreen(onBack = { navController.popBackStack() })
    }
    composable<TransferEditorRoute> {
        TransferEditorScreen(onBack = { navController.popBackStack() })
    }
    composable<ReportsRoute> { ReportsScreen() }
    composable<SecuritySettingsRoute> {
        SecuritySettingsScreen(
            onBack = { navController.popBackStack() },
            onChangePin = { navController.navigate(ChangePinRoute) },
        )
    }
    composable<ChangePinRoute> {
        ChangePinScreen(onBack = { navController.popBackStack() })
    }
    composable<ImportRoute> {
        ImportScreen(onBack = { navController.popBackStack() })
    }
    composable<SettingsRoute> {
        SettingsScreen(
            appVersion = appVersion,
            entries = listOf(
                SettingsEntry(
                    stringResource(R.string.settings_security),
                    stringResource(R.string.settings_security_description),
                    onClick = { navController.navigate(SecuritySettingsRoute) },
                ),
                SettingsEntry(
                    stringResource(R.string.settings_import),
                    stringResource(R.string.settings_import_description),
                    onClick = { navController.navigate(ImportRoute) },
                ),
                SettingsEntry(stringResource(R.string.settings_export), stringResource(R.string.settings_export_description)),
                SettingsEntry(stringResource(R.string.settings_backup), stringResource(R.string.settings_backup_description)),
            ),
        )
    }
}
