package pt.vcc.financeapp.core.data.importer

import pt.vcc.financeapp.core.data.importer.ImportParseException.Reason
import pt.vcc.financeapp.core.model.ImportFormat

/** Formato pelo conteúdo (assinatura) e, para texto, pela extensão; o tipo MIME do SAF não é fiável. */
object ImportFormatDetector {
    fun detect(fileName: String, bytes: ByteArray): ImportFormat {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when {
            bytes.hasPrefix(0x50, 0x4B, 0x03, 0x04) -> if (extension in setOf("xlsx", "xlsm", "")) {
                ImportFormat.XLSX
            } else {
                throw ImportParseException(Reason.UNSUPPORTED_FORMAT, "ZIP não é XLSX")
            }
            // Contentor OLE: .xls antigo ou XLSX protegido por palavra-passe.
            bytes.hasPrefix(0xD0, 0xCF, 0x11, 0xE0) -> throw ImportParseException(Reason.UNSUPPORTED_FORMAT, "Excel 97-2003 ou cifrado")
            bytes.hasPrefix(0x25, 0x50, 0x44, 0x46) -> throw ImportParseException(Reason.UNSUPPORTED_FORMAT, "PDF")
            extension in setOf("csv", "txt", "tsv", "") -> ImportFormat.CSV
            else -> throw ImportParseException(Reason.UNSUPPORTED_FORMAT, "Extensão .$extension")
        }
    }

    private fun ByteArray.hasPrefix(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }
}
