package pt.vcc.financeapp.feature.finance.transactions

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.Category
import pt.vcc.financeapp.core.model.Transaction
import pt.vcc.financeapp.core.model.TransactionStatus
import pt.vcc.financeapp.core.model.TransactionType
import pt.vcc.financeapp.feature.finance.R
import pt.vcc.financeapp.feature.finance.ui.formatted
import pt.vcc.financeapp.feature.finance.ui.toLocalDate

/** Movimento com os nomes da conta e da categoria já resolvidos para apresentação. */
data class TransactionItem(
    val transaction: Transaction,
    val accountName: String,
    val categoryName: String?,
)

internal fun List<Transaction>.toItems(accounts: List<Account>, categories: List<Category>): List<TransactionItem> {
    val accountNames = accounts.associate { it.id to it.name }
    val categoryNames = categories.associate { it.id to it.name }
    return map { tx ->
        TransactionItem(
            transaction = tx,
            accountName = accountNames[tx.accountId].orEmpty(),
            categoryName = tx.categoryId?.let(categoryNames::get),
        )
    }
}

@Composable
internal fun TransactionRow(
    item: TransactionItem,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val tx = item.transaction
    val isIncome = tx.type == TransactionType.INCOME
    val amountText = (if (isIncome) "+" else "−") + tx.amount.formatted()
    val details = buildList {
        add(tx.bookingDate.toLocalDate().formatted())
        add(item.accountName)
        when {
            tx.isInternalTransfer -> add(stringResource(R.string.transaction_transfer_label))
            item.categoryName != null -> add(item.categoryName)
        }
        if (tx.status == TransactionStatus.PENDING) add(stringResource(R.string.transaction_status_pending))
        if (tx.status == TransactionStatus.CANCELLED) add(stringResource(R.string.transaction_status_cancelled))
    }.filter(String::isNotBlank).joinToString(" · ")

    ListItem(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        headlineContent = { Text(tx.description) },
        supportingContent = { Text(details) },
        trailingContent = {
            Text(
                text = amountText,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    tx.isInternalTransfer -> MaterialTheme.colorScheme.onSurfaceVariant
                    isIncome -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                },
            )
        },
    )
}
