package pt.vcc.financeapp.feature.finance.purchases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.PaymentAccounts
import pt.vcc.financeapp.core.model.PaymentMethod
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.DropdownField

/**
 * Escolha do pagamento de uma compra, comum a todos os modos: MB (conta bancária) ou Dinheiro (conta `CASH`).
 * [accounts] são todas as contas ativas; a lista é filtrada pela forma de pagamento.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaymentSelector(
    method: PaymentMethod,
    accounts: List<Account>,
    selectedAccountId: String?,
    onMethodChange: (PaymentMethod) -> Unit,
    onAccountChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCreateCashAccount: (() -> Unit)? = null,
    warning: String? = null,
    accountError: String? = null,
) {
    val eligible = PaymentAccounts.eligible(method, accounts)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.purchase_payment), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PaymentMethod.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == method,
                    onClick = { onMethodChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, PaymentMethod.entries.size),
                ) { Text(stringResource(option.labelRes)) }
            }
        }
        if (eligible.isEmpty()) {
            Text(
                stringResource(
                    if (method == PaymentMethod.MB) R.string.purchase_payment_no_bank_account else R.string.purchase_payment_no_cash_account,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            if (method == PaymentMethod.CASH && onCreateCashAccount != null) {
                OutlinedButton(onClick = onCreateCashAccount) { Text(stringResource(R.string.purchase_payment_create_cash_account)) }
            }
        } else {
            DropdownField(
                label = stringResource(R.string.purchase_payment_account),
                selectedText = eligible.firstOrNull { it.id == selectedAccountId }?.name
                    ?: stringResource(R.string.purchase_payment_choose_account),
                options = eligible.map { it.id to it.name },
                onSelect = onAccountChange,
                supportingText = accountError ?: warning,
                isError = accountError != null || warning != null,
            )
        }
    }
}

internal val PaymentMethod.labelRes: Int
    get() = when (this) {
        PaymentMethod.MB -> R.string.payment_method_mb
        PaymentMethod.CASH -> R.string.payment_method_cash
    }
