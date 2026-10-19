package pt.vcc.financeapp.core.model

import java.time.Instant

data class Attachment(
    val id: String,
    val transactionId: String,
    val fileName: String,
    val mimeType: String,
    val uri: String,
    val sizeBytes: Long,
    val createdAt: Instant,
)
