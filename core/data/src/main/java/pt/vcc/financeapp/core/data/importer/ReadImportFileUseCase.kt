package pt.vcc.financeapp.core.data.importer

import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pt.vcc.financeapp.core.data.importer.ImportParseException.Reason
import pt.vcc.financeapp.core.model.ImportFormat

/** Lê um extrato CSV/XLSX escolhido pelo utilizador e devolve-o como tabela de texto. */
class ReadImportFileUseCase(
    private val reader: DocumentReader,
    private val dispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(reader: DocumentReader) : this(reader, Dispatchers.Default)

    suspend operator fun invoke(uri: String): ImportFile {
        val document = reader.read(uri)
        return withContext(dispatcher) { parse(document) }
    }

    internal fun parse(document: ImportDocument): ImportFile {
        if (document.bytes.isEmpty()) throw ImportParseException(Reason.EMPTY)
        if (document.bytes.size > ImportLimits.MAX_FILE_BYTES) throw ImportParseException(Reason.TOO_LARGE)
        val format = ImportFormatDetector.detect(document.name, document.bytes)
        val table = when (format) {
            ImportFormat.XLSX -> XlsxParser.parse(document.bytes)
            else -> CsvParser.parse(document.bytes)
        }
        return ImportFile(document.name, format, sha256(document.bytes), table)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
