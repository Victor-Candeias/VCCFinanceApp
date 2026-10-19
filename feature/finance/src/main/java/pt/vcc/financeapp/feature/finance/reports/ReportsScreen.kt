package pt.vcc.financeapp.feature.finance.reports

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pt.vcc.financeapp.core.designsystem.components.EmptyState
import pt.vcc.financeapp.feature.finance.R

@Composable
fun ReportsScreen(modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.finance_reports_title),
        message = stringResource(R.string.finance_reports_empty),
        modifier = modifier,
    )
}
