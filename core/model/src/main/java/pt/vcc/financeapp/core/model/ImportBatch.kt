package pt.vcc.financeapp.core.model

import java.time.Instant

data class ImportBatch(
    val id: String,
    val accountId: String,
    val format: ImportFormat,
    val fileName: String,
    val fileSha256: String,
    val status: ImportStatus,
    val profileName: String? = null,
    val totalRows: Int = 0,
    val importedRows: Int = 0,
    val duplicateRows: Int = 0,
    val errorRows: Int = 0,
    val importedAt: Instant,
)
