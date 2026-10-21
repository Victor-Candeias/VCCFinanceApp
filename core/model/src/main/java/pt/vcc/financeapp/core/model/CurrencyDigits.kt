package pt.vcc.financeapp.core.model

import java.util.Currency

/** Casas decimais da moeda segundo ISO 4217 (EUR = 2, JPY = 0); 2 se a moeda for desconhecida. */
fun currencyFractionDigits(currency: String): Int =
    runCatching { Currency.getInstance(currency).defaultFractionDigits }.getOrNull()?.takeIf { it >= 0 } ?: 2
