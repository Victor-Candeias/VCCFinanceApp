package pt.vcc.financeapp.core.data.ocr

/** Classificação de um preço lido por OCR. */
enum class PriceKind { SALE, OLD, PER_KG, PER_L, DISCOUNT, UNKNOWN }

/** Candidato a preço lido de uma etiqueta; só é usado depois de o utilizador o escolher. */
data class PriceCandidate(
    val cents: Long,
    val kind: PriceKind,
    val confidence: Float,
    val rawText: String,
)
