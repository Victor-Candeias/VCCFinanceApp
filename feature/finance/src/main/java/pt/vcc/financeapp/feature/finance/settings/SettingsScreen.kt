package pt.vcc.financeapp.feature.finance.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pt.vcc.financeapp.core.designsystem.components.EmptyState
import pt.vcc.financeapp.feature.finance.R

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.finance_settings_title),
        message = stringResource(R.string.finance_settings_empty),
        modifier = modifier,
    )
}
