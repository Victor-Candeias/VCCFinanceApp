package pt.vcc.financeapp.core.data.mapper

import pt.vcc.financeapp.core.database.entity.ImportProfileEntity
import pt.vcc.financeapp.core.model.ColumnMapping
import pt.vcc.financeapp.core.model.ImportDateFormat
import pt.vcc.financeapp.core.model.ImportProfile

fun ImportProfileEntity.asDomain(): ImportProfile = ImportProfile(
    id = id,
    name = name,
    headerSignature = headerSignature,
    mapping = ColumnMapping(
        headerRowIndex = headerRowIndex,
        dateColumn = dateColumn,
        descriptionColumn = descriptionColumn,
        amountColumn = amountColumn,
        debitColumn = debitColumn,
        creditColumn = creditColumn,
        valueDateColumn = valueDateColumn,
        referenceColumn = referenceColumn,
        balanceColumn = balanceColumn,
        dateFormat = ImportDateFormat.fromPattern(dateFormat) ?: ImportDateFormat.DAY_MONTH_YEAR_SLASH,
        invertAmounts = invertAmounts,
    ),
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
)

fun ImportProfile.asEntity(): ImportProfileEntity = ImportProfileEntity(
    id = id,
    name = name,
    headerSignature = headerSignature,
    headerRowIndex = mapping.headerRowIndex,
    dateColumn = mapping.dateColumn,
    descriptionColumn = mapping.descriptionColumn,
    amountColumn = mapping.amountColumn,
    debitColumn = mapping.debitColumn,
    creditColumn = mapping.creditColumn,
    valueDateColumn = mapping.valueDateColumn,
    referenceColumn = mapping.referenceColumn,
    balanceColumn = mapping.balanceColumn,
    dateFormat = mapping.dateFormat.pattern,
    invertAmounts = mapping.invertAmounts,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)
