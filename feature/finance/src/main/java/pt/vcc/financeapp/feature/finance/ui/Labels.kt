package pt.vcc.financeapp.feature.finance.ui

import androidx.annotation.StringRes
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.feature.finance.R

@get:StringRes
internal val AccountType.labelRes: Int
    get() = when (this) {
        AccountType.BANK_ACCOUNT -> R.string.account_type_bank_account
        AccountType.MEAL_CARD -> R.string.account_type_meal_card
        AccountType.CREDIT_CARD -> R.string.account_type_credit_card
        AccountType.CASH -> R.string.account_type_cash
        AccountType.SAVINGS -> R.string.account_type_savings
        AccountType.OTHER -> R.string.account_type_other
    }
