package pt.vcc.financeapp.core.data.ocr

/**
 * Sugere nomes de produto a partir do texto de uma fotografia (embalagem ou etiqueta).
 * O texto maior vem primeiro; preços, códigos, pesos e linhas sem palavras são ignorados.
 * O utilizador confirma ou corrige sempre o nome escolhido.
 */
object ProductNameExtractor {

    const val MAX_CANDIDATES = 6
    private const val MAX_LENGTH = 60

    private val letter = Regex("\\p{L}")
    private val word = Regex("\\p{L}{2,}")
    private val noise = Regex(
        "^(pvp|preço|preco|promo(ção|cao)?|oferta|desconto|poupe|antes|agora|€\\s*/\\s*(kg|l|lt|un)|por\\s+(kg|l|lt|un))$",
        RegexOption.IGNORE_CASE,
    )
    private val priceLike = Regex("\\d+\\s*[,.]\\s*\\d{2}\\s*€|€\\s*\\d|\\d\\s*%")
    private val measureOnly = Regex("^[\\d\\s,.x×]*(kg|g|gr|l|lt|ml|cl|un|uni|unid)\\.?$", RegexOption.IGNORE_CASE)

    fun candidates(lines: List<OcrLine>): List<String> {
        val usable = lines.mapIndexedNotNull { index, line ->
            val text = clean(line.text)
            if (isName(text)) Triple(index, text, line.height) else null
        }
        if (usable.isEmpty()) return emptyList()

        val byHeight = usable.sortedWith(compareByDescending<Triple<Int, String, Int>> { it.third }.thenBy { it.first })
        val joined = byHeight.take(2)
            .takeIf { it.size == 2 }
            ?.sortedBy { it.first }
            ?.takeIf { (a, b) -> b.first == a.first + 1 && similarHeight(a.third, b.third) }
            ?.let { (a, b) -> "${a.second} ${b.second}" }
            ?.takeIf { it.length <= MAX_LENGTH }

        return (listOfNotNull(joined) + byHeight.map { it.second })
            .map(::sentenceCase)
            .distinctBy { it.lowercase() }
            .take(MAX_CANDIDATES)
    }

    private fun clean(text: String): String = text.replace('\u00A0', ' ').replace(Regex("\\s+"), " ").trim()

    private fun isName(text: String): Boolean {
        if (text.length !in 3..MAX_LENGTH || noise.matches(text) || measureOnly.matches(text) || priceLike.containsMatchIn(text)) {
            return false
        }
        if (!word.containsMatchIn(text)) return false
        val letters = letter.findAll(text).count()
        return letters * 2 >= text.count { !it.isWhitespace() }
    }

    private fun similarHeight(a: Int, b: Int): Boolean =
        a == 0 || b == 0 || minOf(a, b) * 100 >= maxOf(a, b) * 80

    /** Etiquetas em maiúsculas (`ARROZ CAROLINO`) passam a `Arroz carolino`. */
    private fun sentenceCase(text: String): String {
        if (text.any { it.isLowerCase() }) return text
        val lower = text.lowercase()
        return lower.replaceFirstChar { it.titlecase() }
    }
}
