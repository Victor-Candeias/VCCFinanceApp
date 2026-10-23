package pt.vcc.financeapp.core.data.ocr

import kotlin.math.roundToInt

/**
 * Linha reconhecida pelo OCR; [height] é a altura da caixa em píxeis (0 se desconhecida) e [left], [top] e
 * [right] a posição da caixa (usada para juntar euros e cêntimos impressos em tamanhos diferentes).
 */
data class OcrLine(
    val text: String,
    val height: Int = 0,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
)

data class PriceParseResult(
    val candidates: List<PriceCandidate>,
    /** Candidato destacado como sugestão; nunca é aceite sem confirmação do utilizador. */
    val suggested: PriceCandidate?,
    val rawText: String,
)

/**
 * Extrai candidatos a preço de texto de etiquetas portuguesas (§5.3, D26).
 *
 * Formatos: `1,49`, `1.49`, `1, 49`, `1,49 €`, `€ 1,49`, `1€49` e `1 49` (cêntimos separados/expoente, só
 * junto a `€` ou sozinho na linha). Euros grandes e cêntimos pequenos lidos em linhas separadas são juntos pela
 * posição; `149` sozinho no maior texto da etiqueta é lido como 1,49. Corrige trocas comuns do OCR (`O`→`0`,
 * `I`/`l`→`1`, `E`/`C`→`€`). Marcadores: `€/kg`, `€/l`, `antes`, `promo`, descontos e percentagens.
 * Não escolhe o maior, o menor nem o primeiro número: ordena por tipo, contexto e tamanho do texto.
 */
object PriceCandidateParser {

    const val MIN_SUGGESTION_CONFIDENCE = 0.45f

    private val numberPattern = Regex(
        "(?<![\\d,.])(\\d{1,4})\\s?€\\s?(\\d{2})(?!\\d)(?![,.]\\d)" + // 1€49
            "|(?<![\\d,.])(\\d{1,4})[,.]\\s?(\\d{2})(?!\\d)(?![,.]\\d)" + // 1,49 / 1.49 / 1, 49
            "|(?<![\\d,.])(\\d{1,3})\\s(\\d{2})(?![\\d,.])", // 1 49
    )

    // Trocas frequentes do OCR em números de etiquetas.
    private val letterZero = Regex("(?<=\\d[,.]?)[Oo](?=\\d|[,.]\\d|\\s*€|\\s*$)|(?<![\\p{L}\\d])[Oo](?=[,.]\\d\\d)")
    private val letterOne = Regex("(?<=\\d)[Il|](?=[,.]\\d\\d)|(?<![\\p{L}\\d])[Il|](?=[,.]\\d\\d)|(?<=\\d[,.])[Il|](?=\\d)")
    private val letterEuro = Regex("(?<=\\d[,.]\\d\\d)\\s?[EC](?![\\p{L}])")

    /** Euros grandes de um preço com cêntimos em expoente (`1` + `49€`). */
    private val bigEuros = Regex("^\\s*€?\\s*(\\d{1,3})\\s*[,.]?\\s*$")
    private val smallCents = Regex("^\\s*[,.]?\\s*(\\d{2})\\s*(€|eur)?\\s*$")
    private val headlineCents = Regex("^\\s*([1-9])(\\d{2})\\s*(€)?\\s*$")
    private val perKgAfter = Regex("^\\s*(€|eur)?\\s*(/|por)\\s*kg\\b")
    private val perKgBefore = Regex("(/|por)\\s*kg\\s*:?\\s*€?\\s*$")
    private val perLAfter = Regex("^\\s*(€|eur)?\\s*(/|por)\\s*(l|lt|litro)\\b")
    private val perLBefore = Regex("(/|por)\\s*(l|lt|litro)\\s*:?\\s*€?\\s*$")
    private val oldWords = Regex("\\b(antes|anterior|pvp antigo|era)\\b")
    private val promoWords = Regex("promo|oferta|\\bagora\\b|campanha")
    private val discountWords = Regex("desconto|poup|economiz")
    private val onlyPriceLine = Regex("^\\s*€?\\s*\\d{1,3}\\s\\d{2}\\s*€?\\s*$")

    fun parse(lines: List<OcrLine>): PriceParseResult {
        val normalized = lines.map { it.copy(text = normalize(it.text)) }
        val found = normalized.flatMap { line -> parseLine(line.text).map { it to line.height } } +
            splitPrices(normalized) + headlinePrices(normalized)
        val maxHeight = found.maxOfOrNull { it.second } ?: 0

        val candidates = found
            .map { (match, height) -> match.toCandidate(height, maxHeight) }
            .groupBy { it.cents to it.kind }
            .map { (_, same) -> same.maxBy { it.confidence } }
            .sortedWith(compareByDescending<PriceCandidate> { it.kind.isSalePrice() }.thenByDescending { it.confidence })

        val suggested = candidates.firstOrNull { it.kind.isSalePrice() && it.confidence >= MIN_SUGGESTION_CONFIDENCE }
        return PriceParseResult(candidates, suggested, lines.joinToString("\n") { it.text })
    }

    internal fun normalize(text: String): String = text
        .replace('\u00A0', ' ')
        .replace(letterZero, "0")
        .replace(letterOne, "1")
        .replace(letterEuro, "€")

    /**
     * Etiquetas com os euros em letra grande e os cêntimos pequenos em expoente: o OCR devolve-os em linhas
     * separadas (`1` e `49€`). Junta-os quando os cêntimos estão logo à direita, na parte de cima dos euros.
     */
    private fun splitPrices(lines: List<OcrLine>): List<Pair<RawMatch, Int>> {
        val withBox = lines.filter { it.height > 0 && it.right > it.left }
        return withBox.mapNotNull { big ->
            val euros = bigEuros.find(big.text)?.groupValues?.get(1) ?: return@mapNotNull null
            val small = withBox
                .filter { it !== big && smallCents.matches(it.text) }
                .filter { it.height < big.height * 0.85f }
                .filter { it.left >= big.left + (big.right - big.left) / 2 && it.left - big.right <= big.height }
                .filter { it.top >= big.top - it.height / 2 && it.top <= big.top + big.height * 0.6f }
                .minByOrNull { it.left - big.right }
                ?: return@mapNotNull null
            val cents = smallCents.find(small.text)!!.groupValues
            val value = euros.toLong() * 100 + cents[1].toLong()
            if (value <= 0) return@mapNotNull null
            val euroAdjacent = cents[2].isNotEmpty() || big.text.contains('€')
            RawMatch(value, PriceKind.SALE, euroAdjacent, promo = false, text = "${big.text.trim()} ${small.text.trim()}") to
                big.height
        }
    }

    /** `149` sozinho no maior texto da etiqueta: os cêntimos em expoente foram colados aos euros. */
    private fun headlinePrices(lines: List<OcrLine>): List<Pair<RawMatch, Int>> {
        val tallest = lines.maxOfOrNull { it.height }?.takeIf { it > 0 } ?: return emptyList()
        return lines.filter { it.height == tallest }.mapNotNull { line ->
            val g = headlineCents.find(line.text)?.groupValues ?: return@mapNotNull null
            val value = g[1].toLong() * 100 + g[2].toLong()
            val euro = g[3].isNotEmpty()
            RawMatch(value, if (euro) PriceKind.SALE else PriceKind.UNKNOWN, euro, promo = false, text = line.text.trim()) to
                line.height
        }
    }

    private fun PriceKind.isSalePrice() = this == PriceKind.SALE || this == PriceKind.UNKNOWN

    private class RawMatch(
        val cents: Long,
        val kind: PriceKind,
        val euroAdjacent: Boolean,
        val promo: Boolean,
        val text: String,
    )

    private fun parseLine(text: String): List<RawMatch> {
        val line = text.replace('\u00A0', ' ')
        val lower = line.lowercase()
        val matches = numberPattern.findAll(line).toList()
        var previousEnd = 0
        return matches.mapNotNull { match ->
            val prefix = lower.substring(0, match.range.first)
            val suffix = lower.substring(match.range.last + 1)
            // Palavras-chave valem desde o número anterior; se for o único número, vale a linha toda.
            val segment = if (matches.size == 1) lower else lower.substring(previousEnd, match.range.first)
            previousEnd = match.range.last + 1

            val g = match.groupValues
            val euroInside = g[1].isNotEmpty()
            val spaced = g[5].isNotEmpty()
            val euros = g[1].ifEmpty { g[3] }.ifEmpty { g[5] }
            val cents = g[2].ifEmpty { g[4] }.ifEmpty { g[6] }
            if (suffix.trimStart().startsWith("%")) return@mapNotNull null

            val euroAdjacent = euroInside ||
                prefix.trimEnd().endsWith("€") ||
                suffix.trimStart().let { it.startsWith("€") || it.startsWith("eur") }
            if (spaced && !euroAdjacent && !onlyPriceLine.matches(lower)) return@mapNotNull null

            val value = euros.toLong() * 100 + cents.toLong()
            if (value <= 0) return@mapNotNull null

            val promo = promoWords.containsMatchIn(segment)
            val kind = when {
                prefix.endsWith("-") || discountWords.containsMatchIn(segment) -> PriceKind.DISCOUNT
                perKgAfter.containsMatchIn(suffix) || perKgBefore.containsMatchIn(prefix) -> PriceKind.PER_KG
                perLAfter.containsMatchIn(suffix) || perLBefore.containsMatchIn(prefix) -> PriceKind.PER_L
                oldWords.containsMatchIn(segment) -> PriceKind.OLD
                euroAdjacent || promo -> PriceKind.SALE
                else -> PriceKind.UNKNOWN
            }
            RawMatch(value, kind, euroAdjacent, promo, line.trim())
        }
    }

    private fun RawMatch.toCandidate(height: Int, maxHeight: Int): PriceCandidate {
        var confidence = if (kind == PriceKind.UNKNOWN) 0.3f else 0.5f
        if (euroAdjacent) confidence += 0.15f
        if (promo) confidence += 0.1f
        if (maxHeight > 0) confidence += 0.25f * height / maxHeight
        confidence = (confidence.coerceIn(0f, 1f) * 100).roundToInt() / 100f
        return PriceCandidate(cents, kind, confidence, text)
    }
}
