package pt.vcc.financeapp.core.model

import java.text.Normalizer
import java.time.Instant

data class Merchant(
    val id: String,
    val name: String,
    val normalizedName: String,
    val defaultCategoryId: String? = null,
    val isActive: Boolean = true,
    val createdAt: Instant,
) {
    companion object {
        /** Chave de unicidade: minúsculas, sem acentos e com espaços normalizados ("Café  Central" = "cafe central"). */
        fun normalize(name: String): String =
            Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replace(DIACRITICS, "")
                .lowercase()
                .replace(WHITESPACE, " ")

        private val DIACRITICS = Regex("\\p{Mn}+")
        private val WHITESPACE = Regex("\\s+")
    }
}
