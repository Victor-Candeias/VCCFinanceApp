package pt.vcc.financeapp.feature.finance.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import pt.vcc.financeapp.core.model.PurchaseType
import pt.vcc.financeapp.feature.finance.purchases.BarcodeScannerScreen
import pt.vcc.financeapp.feature.finance.purchases.CatalogScreen
import pt.vcc.financeapp.feature.finance.purchases.ExpressPurchaseScreen
import pt.vcc.financeapp.feature.finance.purchases.MerchantsScreen
import pt.vcc.financeapp.feature.finance.purchases.PurchaseDetailScreen
import pt.vcc.financeapp.feature.finance.purchases.PurchaseHistoryScreen
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
            onOpenExpress = { navController.navigate(ExpressPurchaseRoute(it)) },
            onResume = { purchase ->
                when (purchase.type) {
                    PurchaseType.QUICK, PurchaseType.PLANNED -> navController.navigate(PurchaseRoute(purchase.id))
                    PurchaseType.EXPRESS -> navController.navigate(ExpressPurchaseRoute(purchase.id))
                }
            },
            onHistory = { navController.navigate(PurchaseHistoryRoute) },
            onMerchants = { navController.navigate(MerchantsRoute) },
            onCatalog = { navController.navigate(CatalogRoute) },
        )
    }
    composable<PurchaseRoute> {
        PurchaseScreen(onBack = back, onClosed = back, onScan = { navController.navigate(BarcodeScannerRoute(it)) })
    }
    composable<BarcodeScannerRoute> { BarcodeScannerScreen(onBack = back) }
    composable<ExpressPurchaseRoute> { ExpressPurchaseScreen(onBack = back, onClosed = back) }
    composable<PurchaseHistoryRoute> {
        PurchaseHistoryScreen(onBack = back, onOpenPurchase = { navController.navigate(PurchaseDetailRoute(it)) })
    }
    composable<PurchaseDetailRoute> {
        PurchaseDetailScreen(
            onBack = back,
            onEdit = { navController.navigate(PurchaseRoute(it)) },
            onOpenPurchase = { purchase ->
                when (purchase.type) {
                    PurchaseType.QUICK, PurchaseType.PLANNED -> navController.navigate(PurchaseRoute(purchase.id))
                    PurchaseType.EXPRESS -> navController.navigate(ExpressPurchaseRoute(purchase.id))
                }
            },
            onOpenTransaction = { navController.navigate(TransactionEditorRoute(it)) },
        )
    }
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
