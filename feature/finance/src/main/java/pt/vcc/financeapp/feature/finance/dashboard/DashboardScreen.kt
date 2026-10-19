package pt.vcc.financeapp.feature.finance.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pt.vcc.financeapp.core.designsystem.VCCFinanceTheme
import pt.vcc.financeapp.feature.finance.R

@Composable
fun DashboardScreen(
    onAccountsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.finance_dashboard_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.finance_dashboard_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAccountsClick) {
            Text(text = stringResource(R.string.finance_dashboard_view_accounts))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    VCCFinanceTheme {
        DashboardScreen(onAccountsClick = {})
    }
}
