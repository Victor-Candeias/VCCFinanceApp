package pt.vcc.financeapp.core.data.exporter

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.Category
import pt.vcc.financeapp.core.model.Merchant
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.PaymentMethod
import pt.vcc.financeapp.core.model.PeriodTotals
import pt.vcc.financeapp.core.model.PurchaseDetail
import pt.vcc.financeapp.core.model.PurchaseItem
import pt.vcc.financeapp.core.model.PurchaseType
import pt.vcc.financeapp.core.model.Transaction
import pt.vcc.financeapp.core.model.TransactionStatus
import pt.vcc.financeapp.core.model.TransactionType
import pt.vcc.financeapp.core.model.currencyFractionDigits

enum class ExportFormat(val extension: String, val mimeType: String) {
    CSV("csv", "text/csv"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    PDF("pdf", "application/pdf"),
    JSON("json", "application/json"),
}

/** Movimentos com `bookingDate` em `[from, to)`; `null` = sem limite. [label] identifica o período no ficheiro. */
data class ExportRange(val from: Instant?, val to: Instant?, val label: String)

/**
 * Dados de uma exportação, independentes do formato. Inclui todas as contas (também as inativas,
 * porque podem ter movimentos no período) e todas as categorias. O IBAN nunca é exportado.
 */
data class ExportData(
    val generatedAt: Instant,
    val zone: ZoneId,
    val range: ExportRange,
    val accounts: List<Account>,
    val categories: List<Category>,
    /** Ordenados por data contabilística. */
    val transactions: List<Transaction>,
    /** Compras concluídas cujo movimento está em [transactions], ordenadas por data. */
    val purchases: List<PurchaseDetail> = emptyList(),
    val merchants: List<Merchant> = emptyList(),
) {
    private val accountsById = accounts.associateBy { it.id }
    private val categoriesById = categories.associateBy { it.id }
    private val merchantsById = merchants.associateBy { it.id }
    private val purchasesByTransaction = purchases.filter { it.purchase.transactionId != null }
        .associateBy { it.purchase.transactionId }

    /** Compra que gerou o movimento, se existir. */
    fun purchaseOf(transactionId: String): PurchaseDetail? = purchasesByTransaction[transactionId]

    fun merchantName(id: String?): String = id?.let(merchantsById::get)?.name.orEmpty()

    /** Totais por moeda (só movimentos confirmados, sem transferências internas). */
    val totals: List<PeriodTotals> = PeriodTotals.byCurrency(transactions)

    fun accountName(id: String): String = accountsById[id]?.name.orEmpty()

    /** Nome da categoria com a principal antes, ex.: "Alimentação › Restaurantes". */
    fun categoryPath(id: String?): String {
        val category = id?.let(categoriesById::get) ?: return ""
        val parent = category.parentCategoryId?.let(categoriesById::get) ?: return category.name
        return "${parent.name} › ${category.name}"
    }

    fun categoryName(id: String?): String = id?.let(categoriesById::get)?.name.orEmpty()

    /** Categoria principal (as subcategorias contam na principal). */
    fun rootCategoryOf(id: String?): String? = id?.let(categoriesById::get)?.let { it.parentCategoryId ?: it.id }

    fun localDate(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()
}

/** Textos e números comuns aos formatos de exportação. */
internal object ExportText {
    val TRANSACTION_HEADERS = listOf(
        "Data", "Data-valor", "Conta", "Tipo", "Descrição", "Categoria", "Montante", "Moeda", "Estado", "Notas",
        "Pagamento", "Artigos da compra",
    )

    fun paymentMethod(method: PaymentMethod?): String = when (method) {
        PaymentMethod.MB -> "MB"
        PaymentMethod.CASH -> "Dinheiro"
        null -> ""
    }

    fun purchaseType(type: PurchaseType): String = when (type) {
        PurchaseType.QUICK -> "Compra rápida"
        PurchaseType.PLANNED -> "Compra por lista"
        PurchaseType.EXPRESS -> "Despesa rápida"
    }

    /** Quantidade sem zeros à direita, ex.: "1,5". */
    fun quantity(item: PurchaseItem): String = item.quantity.stripTrailingZeros().toPlainString().replace('.', ',')

    /** Resumo dos artigos numa célula, ex.: "Leite ×2 1,90; Pão 0,30". */
    fun itemsSummary(detail: PurchaseDetail): String = detail.items.sortedBy { it.position }.joinToString("; ") { item ->
        val qty = if (item.quantity.compareTo(java.math.BigDecimal.ONE) == 0) "" else " ×${quantity(item)}"
        "${item.name.trim()}$qty ${decimal(item.totalMinor, detail.purchase.currency).toPlainString().replace('.', ',')}"
    }

    fun type(type: TransactionType): String = when (type) {
        TransactionType.INCOME -> "Receita"
        TransactionType.EXPENSE -> "Despesa"
        TransactionType.TRANSFER -> "Transferência"
    }

    fun status(status: TransactionStatus): String = when (status) {
        TransactionStatus.PENDING -> "Pendente"
        TransactionStatus.BOOKED -> "Confirmado"
        TransactionStatus.CANCELLED -> "Cancelado"
    }

    fun accountType(type: AccountType): String = when (type) {
        AccountType.BANK_ACCOUNT -> "Conta bancária"
        AccountType.MEAL_CARD -> "Cartão de refeição"
        AccountType.CREDIT_CARD -> "Cartão de crédito"
        AccountType.CASH -> "Dinheiro"
        AccountType.SAVINGS -> "Poupança"
        AccountType.OTHER -> "Outra"
    }

    fun yesNo(value: Boolean): String = if (value) "Sim" else "Não"

    /** Despesas negativas e receitas positivas; transferências sem perna correspondente mantêm o valor. */
    fun signedMinor(transaction: Transaction): Long =
        if (transaction.type == TransactionType.EXPENSE) -transaction.amount.amountMinor else transaction.amount.amountMinor

    /** Valor decimal sem separador de milhares e com ponto, ex.: "-1250.50". */
    fun decimal(amountMinor: Long, currency: String): BigDecimal =
        BigDecimal.valueOf(amountMinor, currencyFractionDigits(currency))

    fun decimal(money: Money): BigDecimal = decimal(money.amountMinor, money.currency)
}
