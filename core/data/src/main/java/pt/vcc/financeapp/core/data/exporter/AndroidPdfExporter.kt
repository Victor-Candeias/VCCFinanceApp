package pt.vcc.financeapp.core.data.exporter

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import pt.vcc.financeapp.core.model.FinancialAggregations
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.PurchaseDetail
import pt.vcc.financeapp.core.model.currencyFractionDigits

/**
 * Relatório PDF em A4: resumo por moeda, gráfico de barras das despesas por categoria principal
 * (moeda com mais movimentos), tabela de movimentos e tabela de compras (MB/Dinheiro), com paginação.
 */
class AndroidPdfExporter @Inject constructor() : PdfExportWriter {

    override fun write(data: ExportData, out: OutputStream) {
        val document = PdfDocument()
        try {
            PageWriter(document, data).apply {
                header()
                summary()
                categoryChart()
                transactionTable()
                purchaseTable()
                finish()
            }
            document.writeTo(out)
            out.flush()
        } finally {
            document.close()
        }
    }

    private class PageWriter(private val document: PdfDocument, private val data: ExportData) {
        private var pageNumber = 0
        private lateinit var page: PdfDocument.Page
        private lateinit var canvas: Canvas
        private var y = 0f

        private val title = paint(18f, bold = true)
        private val subtitle = paint(13f, bold = true)
        private val body = paint(9.5f)
        private val bodyBold = paint(9.5f, bold = true)
        private val muted = paint(8.5f).apply { color = Color.DKGRAY }
        private val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f }

        init {
            newPage()
        }

        fun header() {
            text("VCCFinanceApp — Relatório financeiro", MARGIN, title)
            y += 6f
            text("Período: ${data.range.label}", MARGIN, body)
            text("Gerado em ${DATE_TIME.format(data.generatedAt.atZone(data.zone))}", MARGIN, muted)
            y += 10f
        }

        fun summary() {
            section("Resumo")
            if (data.totals.isEmpty()) {
                text("Sem movimentos confirmados no período.", MARGIN, body)
            }
            data.totals.forEach { totals ->
                ensureSpace(ROW)
                y += ROW
                canvas.drawText(totals.currency, MARGIN, y, bodyBold)
                canvas.drawText("Receitas ${money(totals.income)}", MARGIN + 50f, y, body)
                canvas.drawText("Despesas ${money(totals.expenses)}", MARGIN + 210f, y, body)
                canvas.drawText("Saldo ${money(totals.net)}", MARGIN + 370f, y, bodyBold)
            }
            y += 12f
        }

        fun categoryChart() {
            val currency = FinancialAggregations.mainCurrency(data.transactions) ?: return
            val totals = FinancialAggregations.expensesByCategory(data.transactions, currency, data::rootCategoryOf)
            if (totals.isEmpty()) return
            val shown = totals.take(MAX_BARS)
            val othersMinor = totals.drop(MAX_BARS).sumOf { it.total.amountMinor }
            val bars = shown.map { (data.categoryName(it.categoryId).ifEmpty { "Sem categoria" }) to it.total.amountMinor } +
                (if (othersMinor > 0) listOf("Outras" to othersMinor) else emptyList())
            val max = bars.maxOf { it.second }.coerceAtLeast(1)
            section("Despesas por categoria ($currency)")
            val labelWidth = 130f
            val valueWidth = 80f
            val barMax = CONTENT_WIDTH - labelWidth - valueWidth
            val barPaint = Paint().apply { color = BAR_COLOR }
            bars.forEach { (label, value) ->
                ensureSpace(BAR_ROW)
                y += BAR_ROW
                canvas.drawText(ellipsize(label, body, labelWidth - 6f), MARGIN, y, body)
                val width = (barMax * value / max).coerceAtLeast(1f)
                canvas.drawRect(MARGIN + labelWidth, y - 9f, MARGIN + labelWidth + width, y + 1f, barPaint)
                val amount = money(Money(value, currency))
                canvas.drawText(amount, PAGE_WIDTH - MARGIN - body.measureText(amount), y, body)
            }
            y += 12f
        }

        fun transactionTable() {
            section("Movimentos (${data.transactions.size})")
            if (data.transactions.isEmpty()) {
                text("Sem movimentos no período.", MARGIN, body)
                return
            }
            tableHeader()
            data.transactions.forEach { tx ->
                if (y + ROW > PAGE_HEIGHT - MARGIN - FOOTER) {
                    newPage()
                    tableHeader()
                }
                y += ROW
                val cells = listOf(
                    DATE.format(data.localDate(tx.bookingDate)),
                    tx.description,
                    data.categoryPath(tx.categoryId),
                    data.accountName(tx.accountId),
                )
                var x = MARGIN
                cells.forEachIndexed { index, value ->
                    canvas.drawText(ellipsize(value, body, COLUMNS[index] - 6f), x, y, body)
                    x += COLUMNS[index]
                }
                val amount = money(Money(ExportText.signedMinor(tx), tx.amount.currency))
                canvas.drawText(amount, PAGE_WIDTH - MARGIN - body.measureText(amount), y, body)
                canvas.drawLine(MARGIN, y + 4f, PAGE_WIDTH - MARGIN, y + 4f, line)
            }
        }

        fun purchaseTable() {
            if (data.purchases.isEmpty()) return
            section("Compras (${data.purchases.size})")
            val headers = listOf("Data", "Estabelecimento", "Tipo", "Pagamento")
            fun purchaseHeader() {
                ensureSpace(ROW)
                y += ROW
                var x = MARGIN
                headers.forEachIndexed { index, label ->
                    canvas.drawText(label, x, y, bodyBold)
                    x += PURCHASE_COLUMNS[index]
                }
                val total = "Total"
                canvas.drawText(total, PAGE_WIDTH - MARGIN - bodyBold.measureText(total), y, bodyBold)
                canvas.drawLine(MARGIN, y + 4f, PAGE_WIDTH - MARGIN, y + 4f, line)
            }
            purchaseHeader()
            data.purchases.forEach { (purchase, items) ->
                if (y + 2 * ROW > PAGE_HEIGHT - MARGIN - FOOTER) {
                    newPage()
                    purchaseHeader()
                }
                y += ROW
                val cells = listOf(
                    DATE.format(data.localDate(purchase.occurredAt)),
                    data.merchantName(purchase.merchantId).ifEmpty { "—" },
                    ExportText.purchaseType(purchase.type),
                    ExportText.paymentMethod(purchase.paymentMethod),
                )
                var x = MARGIN
                cells.forEachIndexed { index, value ->
                    canvas.drawText(ellipsize(value, body, PURCHASE_COLUMNS[index] - 6f), x, y, body)
                    x += PURCHASE_COLUMNS[index]
                }
                val amount = money(Money(purchase.totalMinor, purchase.currency))
                canvas.drawText(amount, PAGE_WIDTH - MARGIN - body.measureText(amount), y, body)
                if (items.isNotEmpty()) {
                    y += ROW - 3f
                    val summary = ExportText.itemsSummary(PurchaseDetail(purchase, items))
                    canvas.drawText(ellipsize(summary, muted, CONTENT_WIDTH - 12f), MARGIN + 12f, y, muted)
                }
                canvas.drawLine(MARGIN, y + 4f, PAGE_WIDTH - MARGIN, y + 4f, line)
            }
        }

        fun finish() {
            footer()
            document.finishPage(page)
        }

        private fun tableHeader() {
            ensureSpace(ROW)
            y += ROW
            var x = MARGIN
            listOf("Data", "Descrição", "Categoria", "Conta").forEachIndexed { index, label ->
                canvas.drawText(label, x, y, bodyBold)
                x += COLUMNS[index]
            }
            val amount = "Montante"
            canvas.drawText(amount, PAGE_WIDTH - MARGIN - bodyBold.measureText(amount), y, bodyBold)
            canvas.drawLine(MARGIN, y + 4f, PAGE_WIDTH - MARGIN, y + 4f, line)
        }

        private fun section(name: String) {
            ensureSpace(40f)
            y += 18f
            canvas.drawText(name, MARGIN, y, subtitle)
            y += 4f
        }

        private fun text(value: String, x: Float, paint: Paint) {
            ensureSpace(paint.textSize + 4f)
            y += paint.textSize + 4f
            canvas.drawText(ellipsize(value, paint, CONTENT_WIDTH), x, y, paint)
        }

        private fun ensureSpace(height: Float) {
            if (y + height > PAGE_HEIGHT - MARGIN - FOOTER) newPage()
        }

        private fun newPage() {
            if (pageNumber > 0) finish()
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), pageNumber).create())
            canvas = page.canvas
            y = MARGIN
        }

        private fun footer() {
            val label = "Página $pageNumber"
            canvas.drawText(label, PAGE_WIDTH - MARGIN - muted.measureText(label), PAGE_HEIGHT - MARGIN / 2, muted)
        }

        private fun ellipsize(value: String, paint: Paint, width: Float): String {
            val clean = value.replace('\n', ' ').replace('\r', ' ')
            if (paint.measureText(clean) <= width) return clean
            val count = paint.breakText(clean, true, width - paint.measureText(ELLIPSIS), null)
            return clean.take(count).trimEnd() + ELLIPSIS
        }

        private fun paint(size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            color = Color.BLACK
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private companion object {
        // A4 em pontos (1/72").
        const val PAGE_WIDTH = 595f
        const val PAGE_HEIGHT = 842f
        const val MARGIN = 40f
        const val FOOTER = 16f
        const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
        const val ROW = 15f
        const val BAR_ROW = 16f
        const val MAX_BARS = 8
        const val ELLIPSIS = "…"
        const val BAR_COLOR = 0xFF3F6E9E.toInt()
        val COLUMNS = floatArrayOf(58f, 170f, 110f, 87f)
        val PURCHASE_COLUMNS = floatArrayOf(58f, 170f, 110f, 87f)
        val LOCALE: Locale = Locale.forLanguageTag("pt-PT")
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE)
        val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", LOCALE)

        fun money(value: Money): String {
            val digits = currencyFractionDigits(value.currency)
            val known = runCatching { Currency.getInstance(value.currency) }.getOrNull()
            val format = if (known != null) {
                NumberFormat.getCurrencyInstance(LOCALE).apply { currency = known }
            } else {
                NumberFormat.getNumberInstance(LOCALE)
            }
            format.minimumFractionDigits = digits
            format.maximumFractionDigits = digits
            val text = format.format(ExportText.decimal(value))
            return if (known != null) text else "$text ${value.currency}"
        }
    }
}
