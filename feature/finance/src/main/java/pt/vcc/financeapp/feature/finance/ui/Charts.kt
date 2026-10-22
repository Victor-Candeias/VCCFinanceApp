package pt.vcc.financeapp.feature.finance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Fatia de um gráfico circular: [value] em unidades mínimas, já com o texto formatado. */
internal data class ChartSlice(val label: String, val value: Long, val formattedValue: String, val color: Color)

/** Par receitas/despesas de um mês num gráfico de barras. */
internal data class ChartBarGroup(val label: String, val income: Long, val expenses: Long)

/** Paleta usada quando a categoria não tem cor válida. */
internal val ChartPalette = listOf(
    Color(0xFF1565C0),
    Color(0xFFE65100),
    Color(0xFF2E7D32),
    Color(0xFF6A1B9A),
    Color(0xFFC62828),
    Color(0xFF00838F),
    Color(0xFF9E9D24),
)

/** Cor `#RRGGBB` ou `#AARRGGBB`; `null` se o texto não for válido. */
internal fun parseHexColor(hex: String?): Color? {
    val digits = hex?.trim()?.removePrefix("#") ?: return null
    if (digits.length != 6 && digits.length != 8) return null
    val value = digits.toLongOrNull(16) ?: return null
    return Color(if (digits.length == 6) value or 0xFF000000 else value)
}

/** Gráfico circular (donut) com legenda; [description] é lido pelos leitores de ecrã. */
@Composable
internal fun DonutChart(slices: List<ChartSlice>, description: String, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Canvas(
            Modifier
                .size(132.dp)
                .semantics { contentDescription = description },
        ) {
            val stroke = size.minDimension * 0.22f
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            var start = -90f
            slices.forEach { slice ->
                val sweep = 360f * slice.value / total
                drawArc(slice.color, start, sweep, useCenter = false, topLeft = topLeft, size = Size(diameter, diameter), style = Stroke(stroke))
                start += sweep
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEach { slice ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(slice.color, CircleShape),
                    )
                    Column {
                        Text(slice.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${slice.formattedValue} · ${percent(slice.value, total)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Barras de receitas e despesas lado a lado por mês, à mesma escala. */
@Composable
internal fun IncomeExpenseBarChart(groups: List<ChartBarGroup>, description: String, modifier: Modifier = Modifier) {
    val incomeColor = MaterialTheme.colorScheme.primary
    val expenseColor = MaterialTheme.colorScheme.error
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val max = groups.maxOfOrNull { maxOf(it.income, it.expenses) }?.coerceAtLeast(1) ?: 1
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .semantics { contentDescription = description },
        ) {
            if (groups.isEmpty()) return@Canvas
            val slot = size.width / groups.size
            val barWidth = slot * 0.3f
            val radius = CornerRadius(barWidth / 4, barWidth / 4)
            groups.forEachIndexed { index, group ->
                val center = slot * index + slot / 2
                listOf(group.income to incomeColor, group.expenses to expenseColor).forEachIndexed { i, (value, color) ->
                    val height = size.height * value / max
                    if (height > 0f) {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(center - barWidth + i * barWidth, size.height - height),
                            size = Size(barWidth * 0.9f, height),
                            cornerRadius = radius,
                        )
                    }
                }
            }
            drawLine(axisColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
        }
        Row(Modifier.fillMaxWidth()) {
            groups.forEach { group ->
                Text(
                    group.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Legenda das cores do [IncomeExpenseBarChart]. */
@Composable
internal fun IncomeExpenseLegend(incomeLabel: String, expensesLabel: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(MaterialTheme.colorScheme.primary, incomeLabel)
        LegendDot(MaterialTheme.colorScheme.error, expensesLabel)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/** Percentagem arredondada às unidades, sem vírgula flutuante (ex.: `33 %`). */
internal fun percent(value: Long, total: Long): String =
    if (total <= 0) "0 %" else "${(value * 100 + total / 2) / total} %"
