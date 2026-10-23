package pt.vcc.financeapp.core.model

/** Total de um agrupamento das compras; [count] é o número de compras (ou de linhas, nos produtos). */
data class PurchaseTotal<K>(val key: K, val total: Money, val count: Int)

/**
 * Compras de um relatório. Parte dos movimentos já contados no [FinancialReport] (`source = PURCHASE`) e liga-os às
 * compras pelo `transactionId`: os totais por pagamento e por tipo são os montantes desses movimentos e coincidem
 * com as despesas das contas, sem duplicar nada.
 */
data class PurchaseReport(
    val currency: String,
    val total: Money,
    val purchaseCount: Int,
    val byPaymentMethod: List<PurchaseTotal<PaymentMethod?>>,
    val byType: List<PurchaseTotal<PurchaseType>>,
    /** Por `merchantId` (`null` = sem estabelecimento). */
    val byMerchant: List<PurchaseTotal<String?>>,
    /** Por produto (nome mostrado), do maior para o menor; no máximo [MAX_PRODUCTS]. */
    val byProduct: List<PurchaseTotal<String>>,
) {
    companion object {
        const val MAX_PRODUCTS = 10

        fun from(report: FinancialReport, purchases: Iterable<Purchase>, items: Iterable<PurchaseItem>): PurchaseReport? {
            val byTransaction = purchases.filter { it.transactionId != null }.associateBy { it.transactionId }
            val counted = report.transactions
                .filter { it.source == TransactionSource.PURCHASE && it.type == TransactionType.EXPENSE }
                .mapNotNull { tx -> byTransaction[tx.id]?.let { it to tx.amount.amountMinor } }
            if (counted.isEmpty()) return null
            val currency = report.currency
            fun <K> totals(key: (Purchase) -> K): List<PurchaseTotal<K>> = counted
                .groupBy({ key(it.first) }, { it.second })
                .map { (k, amounts) -> PurchaseTotal(k, Money(amounts.fold(0L, Math::addExact), currency), amounts.size) }
                .sortedByDescending { it.total.amountMinor }

            val purchaseIds = counted.mapTo(HashSet()) { it.first.id }
            val byProduct = items.filter { it.purchaseId in purchaseIds }
                .groupBy { it.productId ?: it.name.trim().lowercase() }
                .map { (_, lines) ->
                    PurchaseTotal(lines.first().name.trim(), Money(lines.fold(0L) { s, i -> Math.addExact(s, i.totalMinor) }, currency), lines.size)
                }
                .sortedWith(compareByDescending<PurchaseTotal<String>> { it.total.amountMinor }.thenBy { it.key.lowercase() })
                .take(MAX_PRODUCTS)

            return PurchaseReport(
                currency = currency,
                total = Money(counted.fold(0L) { s, (_, amount) -> Math.addExact(s, amount) }, currency),
                purchaseCount = counted.size,
                byPaymentMethod = totals { it.paymentMethod },
                byType = totals { it.type },
                byMerchant = totals { it.merchantId },
                byProduct = byProduct,
            )
        }
    }
}
