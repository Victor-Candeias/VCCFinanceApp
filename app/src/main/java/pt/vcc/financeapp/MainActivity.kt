package pt.vcc.financeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import pt.vcc.financeapp.core.designsystem.VCCFinanceTheme
import pt.vcc.financeapp.feature.finance.authentication.AuthenticationGate
import pt.vcc.financeapp.navigation.AppNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VCCFinanceTheme {
                AuthenticationGate {
                    AppNavHost()
                }
            }
        }
    }
}
