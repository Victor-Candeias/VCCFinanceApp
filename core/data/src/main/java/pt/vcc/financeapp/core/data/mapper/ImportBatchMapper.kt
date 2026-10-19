package pt.vcc.financeapp.core.data.mapper

import pt.vcc.financeapp.core.database.entity.ImportBatchEntity
import pt.vcc.financeapp.core.model.ImportBatch

fun ImportBatchEntity.asDomain(): ImportBatch = ImportBatch(
    id = id,
    accountId = accountId,
    format = format,
    fileName = fileName,
    fileSha256 = fileSha256,
    status = status,
    profileName = profileName,
    totalRows = totalRows,
    importedRows = importedRows,
    duplicateRows = duplicateRows,
    errorRows = errorRows,
    importedAt = importedAt.toInstant(),
)

fun ImportBatch.asEntity(): ImportBatchEntity = ImportBatchEntity(
    id = id,
    accountId = accountId,
    format = format,
    fileName = fileName,
    fileSha256 = fileSha256,
    profileName = profileName,
    status = status,
    totalRows = totalRows,
    importedRows = importedRows,
    duplicateRows = duplicateRows,
    errorRows = errorRows,
    importedAt = importedAt.toEpochMilli(),
)
