package pt.vcc.financeapp.core.model

import java.time.Instant

/** Formatos de data aceites nos extratos; o XLSX converte sempre as datas para [ISO]. */
enum class ImportDateFormat(val pattern: String) {
    DAY_MONTH_YEAR_SLASH("dd/MM/yyyy"),
    DAY_MONTH_YEAR_DASH("dd-MM-yyyy"),
    DAY_MONTH_YEAR_DOT("dd.MM.yyyy"),
    DAY_MONTH_SHORT_YEAR_SLASH("dd/MM/yy"),
    ISO("yyyy-MM-dd"),
    YEAR_MONTH_DAY_SLASH("yyyy/MM/dd"),
    ;

    companion object {
        fun fromPattern(pattern: String): ImportDateFormat? = entries.firstOrNull { it.pattern == pattern }
    }
}

/**
 * Correspondência entre as colunas de um extrato (índices a partir de 0) e os campos de um
 * movimento. O valor vem de [amountColumn] (com sinal: negativo = despesa) ou do par
 * [debitColumn]/[creditColumn]; [invertAmounts] troca despesas e receitas (extratos de cartão de
 * crédito em que as compras aparecem positivas). [headerRowIndex] é -1 quando não há cabeçalho.
 */
data class ColumnMapping(
    val headerRowIndex: Int,
    val dateColumn: Int?,
    val descriptionColumn: Int?,
    val amountColumn: Int? = null,
    val debitColumn: Int? = null,
    val creditColumn: Int? = null,
    val valueDateColumn: Int? = null,
    val referenceColumn: Int? = null,
    val balanceColumn: Int? = null,
    val dateFormat: ImportDateFormat = ImportDateFormat.DAY_MONTH_YEAR_SLASH,
    val invertAmounts: Boolean = false,
) {
    val usesDebitCredit: Boolean get() = amountColumn == null && (debitColumn != null || creditColumn != null)

    /** Campos mínimos para gerar movimentos: data, descrição e valor. */
    val isComplete: Boolean
        get() = dateColumn != null && descriptionColumn != null && (amountColumn != null || debitColumn != null || creditColumn != null)
}

/** Mapeamento guardado para reutilizar com extratos do mesmo banco (reconhecidos pelo cabeçalho). */
data class ImportProfile(
    val id: String,
    val name: String,
    /** Cabeçalho normalizado (`data mov|descritivo|...`); vazio se o extrato não tem cabeçalho. */
    val headerSignature: String,
    val mapping: ColumnMapping,
    val createdAt: Instant,
    val updatedAt: Instant,
)
