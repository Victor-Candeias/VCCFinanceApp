package pt.vcc.financeapp.core.data.exporter

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject

interface DocumentWriter {
    /** Escreve no documento [uri] (texto `content://...`) criado pelo seletor do sistema, substituindo o conteúdo. */
    suspend fun write(uri: String, block: (OutputStream) -> Unit)
}

/** Escreve através do ContentResolver; só tem acesso ao documento concedido pelo seletor. */
class ContentResolverDocumentWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) : DocumentWriter {
    override suspend fun write(uri: String, block: (OutputStream) -> Unit) {
        val stream = context.contentResolver.openOutputStream(uri.toUri(), "wt") ?: throw IOException("Sem acesso ao documento")
        BufferedOutputStream(stream).use { out ->
            block(out)
            out.flush()
        }
    }
}
