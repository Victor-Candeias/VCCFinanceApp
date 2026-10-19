package pt.vcc.financeapp.core.data.mapper

import pt.vcc.financeapp.core.database.entity.AttachmentEntity
import pt.vcc.financeapp.core.model.Attachment

fun AttachmentEntity.asDomain(): Attachment = Attachment(
    id = id,
    transactionId = transactionId,
    fileName = fileName,
    mimeType = mimeType,
    uri = uri,
    sizeBytes = sizeBytes,
    createdAt = createdAt.toInstant(),
)

fun Attachment.asEntity(): AttachmentEntity = AttachmentEntity(
    id = id,
    transactionId = transactionId,
    fileName = fileName,
    mimeType = mimeType,
    uri = uri,
    sizeBytes = sizeBytes,
    createdAt = createdAt.toEpochMilli(),
)
