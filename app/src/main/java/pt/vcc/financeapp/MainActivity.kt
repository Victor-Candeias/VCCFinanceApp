package pt.vcc.financeapp

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.designsystem.VCCFinanceTheme
import pt.vcc.financeapp.core.security.AutoLockController
import pt.vcc.financeapp.feature.finance.authentication.AuthenticationGate
import pt.vcc.financeapp.navigation.AppNavHost

/** `FragmentActivity` porque o `BiometricPrompt` precisa de um `FragmentManager`. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var autoLockController: AutoLockController

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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(INACTIVITY_CHECK_MILLIS)
                    autoLockController.checkInactivity()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        autoLockController.onForeground()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) autoLockController.onBackground()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        autoLockController.onUserInteraction()
    }

    private companion object {
        const val INACTIVITY_CHECK_MILLIS = 5_000L
    }
}
