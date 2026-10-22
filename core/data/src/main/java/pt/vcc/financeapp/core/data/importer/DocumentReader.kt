package pt.vcc.financeapp.core.data.importer

import android.content.Context
import android.provider.OpenableColumns
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import pt.vcc.financeapp.core.data.importer.ImportParseException.Reason

/** Documento escolhido pelo utilizador (Storage Access Framework). */
data class ImportDocument(val name: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is ImportDocument && name == other.name && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
}

interface DocumentReader {
    /** Lê o documento [uri] (texto `content://...`) com o limite [ImportLimits.MAX_FILE_BYTES]. */
    suspend fun read(uri: String): ImportDocument
}

/** Lê através do ContentResolver; só tem acesso temporário ao ficheiro concedido pelo seletor. */
class ContentResolverDocumentReader @Inject constructor(
    @ApplicationContext private val context: Context,
) : DocumentReader {
    override suspend fun read(uri: String): ImportDocument {
        val parsed = uri.toUri()
        val resolver = context.contentResolver
        var name = parsed.lastPathSegment.orEmpty()
        var declaredSize = -1L
        try {
            resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let { name = cursor.getString(it) }
                    cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let { declaredSize = cursor.getLong(it) }
                }
            }
            if (declaredSize > ImportLimits.MAX_FILE_BYTES) throw ImportParseException(Reason.TOO_LARGE)
            val bytes = resolver.openInputStream(parsed)?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > ImportLimits.MAX_FILE_BYTES) throw ImportParseException(Reason.TOO_LARGE)
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            } ?: throw ImportParseException(Reason.READ_FAILED)
            return ImportDocument(name.ifBlank { "extrato" }, bytes)
        } catch (e: IOException) {
            throw ImportParseException(Reason.READ_FAILED, e.message, e)
        } catch (e: SecurityException) {
            throw ImportParseException(Reason.READ_FAILED, e.message, e)
        }
    }
}
