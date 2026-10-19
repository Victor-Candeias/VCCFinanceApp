package pt.vcc.financeapp.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.graphics.vector.ImageVector
import pt.vcc.financeapp.R
import pt.vcc.financeapp.feature.finance.navigation.AccountsRoute
import pt.vcc.financeapp.feature.finance.navigation.DashboardRoute
import pt.vcc.financeapp.feature.finance.navigation.ReportsRoute
import pt.vcc.financeapp.feature.finance.navigation.SettingsRoute
import pt.vcc.financeapp.feature.finance.navigation.TransactionsRoute

/** Separadores da barra de navegação principal (README §9). */
enum class TopLevelDestination(
    val route: Any,
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
) {
    DASHBOARD(DashboardRoute, Icons.Filled.Home, R.string.nav_dashboard),
    ACCOUNTS(AccountsRoute, Icons.Filled.AccountBox, R.string.nav_accounts),
    TRANSACTIONS(TransactionsRoute, Icons.AutoMirrored.Filled.List, R.string.nav_transactions),
    REPORTS(ReportsRoute, Icons.Filled.DateRange, R.string.nav_reports),
    MORE(SettingsRoute, Icons.Filled.Menu, R.string.nav_more),
}
