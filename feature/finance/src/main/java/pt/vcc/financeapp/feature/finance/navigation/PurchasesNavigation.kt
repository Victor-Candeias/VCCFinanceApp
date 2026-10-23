package pt.vcc.financeapp.feature.finance.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pt.vcc.financeapp.core.model.PurchaseType
import pt.vcc.financeapp.feature.finance.purchases.CatalogScreen
import pt.vcc.financeapp.feature.finance.purchases.MerchantsScreen
import pt.vcc.financeapp.feature.finance.purchases.PurchaseScreen
import pt.vcc.financeapp.feature.finance.purchases.PurchasesHubScreen
import pt.vcc.financeapp.feature.finance.purchases.ShoppingListEditorScreen
import pt.vcc.financeapp.feature.finance.purchases.ShoppingListsScreen

/** Destinos das Compras (Sprint 8). */
internal fun NavGraphBuilder.purchasesGraph(navController: NavController) {
    val back: () -> Unit = { navController.popBackStack() }
    composable<PurchasesRoute> {
        PurchasesHubScreen(
            onBack = back,
            onOpenPurchase = { navController.navigate(PurchaseRoute(it)) },
            onShoppingLists = { navController.navigate(ShoppingListsRoute) },
            onExpressPurchase = {},
            onResume = { purchase ->
                when (purchase.type) {
                    PurchaseType.QUICK, PurchaseType.PLANNED -> navController.navigate(PurchaseRoute(purchase.id))
                    PurchaseType.EXPRESS -> Unit
                }
            },
            onHistory = {},
            onMerchants = { navController.navigate(MerchantsRoute) },
            onCatalog = { navController.navigate(CatalogRoute) },
        )
    }
    composable<PurchaseRoute> { PurchaseScreen(onBack = back, onClosed = back) }
    composable<ShoppingListsRoute> {
        ShoppingListsScreen(
            onBack = back,
            onOpenList = { navController.navigate(ShoppingListRoute(it)) },
            onOpenPurchase = { navController.navigate(PurchaseRoute(it)) },
        )
    }
    composable<ShoppingListRoute> {
        ShoppingListEditorScreen(onBack = back, onOpenPurchase = { navController.navigate(PurchaseRoute(it)) })
    }
    composable<MerchantsRoute> { MerchantsScreen(onBack = back) }
    composable<CatalogRoute> { CatalogScreen(onBack = back) }
}
