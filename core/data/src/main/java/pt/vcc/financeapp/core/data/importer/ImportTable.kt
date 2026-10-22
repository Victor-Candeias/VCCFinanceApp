package pt.vcc.financeapp.core.data.importer

/**
 * Conteúdo tabular de um extrato, ainda sem interpretação: cada célula é texto. Linhas totalmente
 * vazias são descartadas. Números de XLSX vêm com vírgula decimal (`-12,5`) e datas como
 * `yyyy-MM-dd`, para não serem confundidos com separadores de milhares.
 */
data class ImportTable(val rows: List<List<String>>) {
    val columnCount: Int = rows.maxOfOrNull { it.size } ?: 0

    fun cell(row: Int, column: Int): String = rows.getOrNull(row)?.getOrNull(column).orEmpty()
}

/** Ficheiro lido e interpretado como tabela; [sha256] identifica o mesmo ficheiro em importações repetidas. */
data class ImportFile(
    val fileName: String,
    val format: pt.vcc.financeapp.core.model.ImportFormat,
    val sha256: String,
    val table: ImportTable,
)

class ImportParseException(val reason: Reason, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: reason.name, cause) {
    enum class Reason {
        /** Ficheiro sem linhas com conteúdo. */
        EMPTY,
        TOO_LARGE,
        TOO_MANY_ROWS,

        /** Formato não suportado (por exemplo `.xls` antigo, PDF ou binário). */
        UNSUPPORTED_FORMAT,

        /** XLSX corrompido, cifrado com palavra-passe ou com estrutura inesperada. */
        INVALID_FILE,
        READ_FAILED,
    }
}

object ImportLimits {
    /** Tamanho máximo do ficheiro escolhido (um extrato anual típico tem poucas centenas de KB). */
    const val MAX_FILE_BYTES = 10L * 1024 * 1024

    /** Tamanho máximo descomprimido das partes lidas de um XLSX (proteção contra "zip bombs"). */
    const val MAX_XLSX_UNCOMPRESSED_BYTES = 50L * 1024 * 1024
    const val MAX_ROWS = 50_000
    const val MAX_COLUMNS = 200
}
