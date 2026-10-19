package pt.vcc.financeapp.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Green10 = Color(0xFF00201A)
private val Green20 = Color(0xFF00382E)
private val Green30 = Color(0xFF005143)
private val Green40 = Color(0xFF0B5D4B)
private val Green80 = Color(0xFF7FD8BF)
private val Green90 = Color(0xFF9BF5DA)
private val Blue40 = Color(0xFF3A6373)
private val Blue80 = Color(0xFFA1CDDF)
private val Blue90 = Color(0xFFBDE9FB)
private val Red40 = Color(0xFFBA1A1A)
private val Red80 = Color(0xFFFFB4AB)

private val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = Blue40,
    onSecondary = Color.White,
    secondaryContainer = Blue90,
    error = Red40,
)

private val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = Blue80,
    secondaryContainer = Blue40,
    error = Red80,
)

/**
 * Tema Material 3 da VCCFinanceApp. As cores dinâmicas (Android 12+) ficam desligadas por omissão
 * para manter a identidade visual e o contraste dos valores financeiros.
 */
@Composable
fun VCCFinanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
