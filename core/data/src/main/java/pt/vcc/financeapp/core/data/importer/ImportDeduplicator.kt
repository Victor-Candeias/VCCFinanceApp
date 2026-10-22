package pt.vcc.financeapp.core.data.importer

import java.security.MessageDigest
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import pt.vcc.financeapp.core.model.Transaction
import pt.vcc.financeapp.core.model.TransactionStatus

enum class CandidateStatus {
    /** Movimento novo: selecionado por omissão. */
    NEW,

    /** A mesma linha já foi importada nesta conta (mesmo `externalId`): nunca é gravada outra vez. */
    DUPLICATE,

    /** Há um movimento semelhante (por exemplo, introduzido à mão): fica por rever e não selecionado. */
    POSSIBLE_DUPLICATE,
    INVALID,
}

/** Linha do extrato classificada contra os movimentos já existentes na conta. */
data class ImportCandidate(
    val row: ParsedImportRow,
    val status: CandidateStatus,
    /** Identificador estável da linha (`h:` + SHA-256); nulo nas linhas inválidas. */
    val externalId: String?,
    /** Movimento existente igual ou semelhante. */
    val match: Transaction? = null,
) {
    val isSelectable: Boolean get() = status == CandidateStatus.NEW || status == CandidateStatus.POSSIBLE_DUPLICATE
}

/**
 * Deduplicação em duas etapas (README §11):
 * 1. **Exata** — cada linha recebe um `externalId` derivado da data, tipo, valor, descrição normalizada,
 *    referência e ordem entre linhas iguais do mesmo ficheiro; reimportar o mesmo extrato, ou um
 *    extrato sobreposto, produz os mesmos identificadores.
 * 2. **Heurística** — mesmo tipo e valor com data a ±[POSSIBLE_DUPLICATE_DAYS] dia(s); cada movimento
 *    existente só justifica um possível duplicado. Nada é descartado sem o utilizador ver.
 */
object ImportDeduplicator {
    const val POSSIBLE_DUPLICATE_DAYS = 1L

    /** `rowIndex` → `externalId` das linhas válidas. */
    fun externalIds(rows: List<ParsedImportRow>): Map<Int, String> {
        val occurrences = mutableMapOf<String, Int>()
        val digest = MessageDigest.getInstance("SHA-256")
        return rows.filter { it.isValid }.associate { row ->
            val key = listOf(
                row.bookingDate,
                row.type?.name,
                row.amountMinor,
                ColumnMappingDetector.normalize(row.description),
                row.reference?.trim().orEmpty(),
            ).joinToString("|")
            val occurrence = occurrences.getOrDefault(key, 0)
            occurrences[key] = occurrence + 1
            row.rowIndex to "h:" + digest.digest("$key|$occurrence".toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    fun classify(rows: List<ParsedImportRow>, existing: List<Transaction>, zone: ZoneId): List<ImportCandidate> {
        val ids = externalIds(rows)
        val byExternalId = existing.filter { it.externalId != null }.associateBy { it.externalId }
        val exactMatches = ids.values.mapNotNullTo(mutableSetOf()) { byExternalId[it]?.id }
        // Agrupados por tipo e valor para não comparar cada linha com todos os movimentos do período.
        val available = existing
            .filter { it.id !in exactMatches && it.status != TransactionStatus.CANCELLED }
            .groupByTo(mutableMapOf()) { it.type to it.amount.amountMinor }

        return rows.map { row ->
            val externalId = ids[row.rowIndex] ?: return@map ImportCandidate(row, CandidateStatus.INVALID, null)
            byExternalId[externalId]?.let { return@map ImportCandidate(row, CandidateStatus.DUPLICATE, externalId, it) }
            val date = row.bookingDate!!
            val key = row.type!! to row.amountMinor
            val description = ColumnMappingDetector.normalize(row.description)
            val sameAmount = available[key].orEmpty()
            val similar = sameAmount
                .map { it to abs(ChronoUnit.DAYS.between(it.bookingDate.atZone(zone).toLocalDate(), date)) }
                .filter { (_, days) -> days <= POSSIBLE_DUPLICATE_DAYS }
                .minWithOrNull(
                    compareBy<Pair<Transaction, Long>> { it.second }
                        .thenBy { if (ColumnMappingDetector.normalize(it.first.description) == description) 0 else 1 },
                )
                ?.first
            if (similar != null) {
                available.getValue(key).remove(similar)
                ImportCandidate(row, CandidateStatus.POSSIBLE_DUPLICATE, externalId, similar)
            } else {
                ImportCandidate(row, CandidateStatus.NEW, externalId)
            }
        }
    }
}
