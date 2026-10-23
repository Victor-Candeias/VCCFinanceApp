package pt.vcc.financeapp.feature.finance.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pt.vcc.financeapp.feature.finance.purchases.CatalogScreen
import pt.vcc.financeapp.feature.finance.purchases.MerchantsScreen
import pt.vcc.financeapp.feature.finance.purchases.PurchasesHubScreen

/** Destinos das Compras (Sprint 8). */
internal fun NavGraphBuilder.purchasesGraph(navController: NavController) {
    val back: () -> Unit = { navController.popBackStack() }
    composable<PurchasesRoute> {
        PurchasesHubScreen(
            onBack = back,
            onQuickPurchase = {},
            onShoppingLists = {},
            onExpressPurchase = {},
            onResume = {},
            onHistory = {},
            onMerchants = { navController.navigate(MerchantsRoute) },
            onCatalog = { navController.navigate(CatalogRoute) },
        )
    }
    composable<MerchantsRoute> { MerchantsScreen(onBack = back) }
    composable<CatalogRoute> { CatalogScreen(onBack = back) }
}
