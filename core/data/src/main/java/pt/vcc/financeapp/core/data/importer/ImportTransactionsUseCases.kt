package pt.vcc.financeapp.core.data.importer

import java.time.Clock
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pt.vcc.financeapp.core.data.repository.AccountRepository
import pt.vcc.financeapp.core.data.repository.ImportBatchRepository
import pt.vcc.financeapp.core.data.repository.TransactionRepository
import pt.vcc.financeapp.core.data.usecase.SaveTransactionUseCase
import pt.vcc.financeapp.core.model.ImportBatch
import pt.vcc.financeapp.core.model.ImportFormat
import pt.vcc.financeapp.core.model.ImportStatus
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.Transaction
import pt.vcc.financeapp.core.model.TransactionSource

/** Linhas classificadas para revisão e, se existir, a última importação concluída do mesmo ficheiro na conta. */
data class ImportReview(
    val candidates: List<ImportCandidate>,
    val previousImport: ImportBatch?,
) {
    fun count(status: CandidateStatus): Int = candidates.count { it.status == status }
}

/** Compara as linhas interpretadas com os movimentos da conta à volta das mesmas datas. */
class ReviewImportUseCase(
    private val transactionRepository: TransactionRepository,
    private val importBatchRepository: ImportBatchRepository,
    private val clock: Clock,
    private val dispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(
        transactionRepository: TransactionRepository,
        importBatchRepository: ImportBatchRepository,
        clock: Clock,
    ) : this(transactionRepository, importBatchRepository, clock, Dispatchers.Default)

    suspend operator fun invoke(accountId: String, fileSha256: String, rows: List<ParsedImportRow>): ImportReview {
        val zone = clock.zone
        val dates = rows.filter { it.isValid }.mapNotNull { it.bookingDate }
        val existing = if (dates.isEmpty()) {
            emptyList()
        } else {
            transactionRepository.getByAccountInPeriod(
                accountId,
                dates.min().minusDays(SEARCH_MARGIN_DAYS).atStartOfDay(zone).toInstant(),
                dates.max().plusDays(SEARCH_MARGIN_DAYS + 1).atStartOfDay(zone).toInstant(),
            )
        }
        val candidates = withContext(dispatcher) { ImportDeduplicator.classify(rows, existing, zone) }
        val previous = importBatchRepository.findCompletedByFile(accountId, fileSha256).firstOrNull()
        return ImportReview(candidates, previous)
    }

    private companion object {
        const val SEARCH_MARGIN_DAYS = ImportDeduplicator.POSSIBLE_DUPLICATE_DAYS + 1
    }
}

data class ImportCommitRequest(
    val accountId: String,
    val fileName: String,
    val format: ImportFormat,
    val fileSha256: String,
    val profileName: String?,
    val candidates: List<ImportCandidate>,
    /** `rowIndex` das linhas escolhidas; só contam as [ImportCandidate.isSelectable]. */
    val selectedRows: Set<Int>,
)

/** Grava o lote de importação e os movimentos escolhidos numa única transação. */
class CommitImportUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val importBatchRepository: ImportBatchRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(request: ImportCommitRequest): ImportBatch {
        val account = accountRepository.getById(request.accountId)
        require(account != null && account.isActive) { "Conta inexistente ou inativa" }
        val now = clock.instant()
        val batchId = UUID.randomUUID().toString()
        val source = if (request.format == ImportFormat.XLSX) TransactionSource.EXCEL_IMPORT else TransactionSource.CSV_IMPORT
        val selected = request.candidates.filter { it.isSelectable && it.row.rowIndex in request.selectedRows }
        val transactions = selected.map { candidate ->
            val row = candidate.row
            Transaction(
                id = UUID.randomUUID().toString(),
                accountId = account.id,
                type = row.type!!,
                amount = Money(row.amountMinor, account.currency),
                description = row.description.take(SaveTransactionUseCase.MAX_DESCRIPTION_LENGTH),
                bookingDate = row.bookingDate!!.atTime(BOOKING_TIME).atZone(clock.zone).toInstant(),
                source = source,
                valueDate = row.valueDate?.atTime(BOOKING_TIME)?.atZone(clock.zone)?.toInstant(),
                externalId = candidate.externalId,
                importBatchId = batchId,
                createdAt = now,
                updatedAt = now,
            )
        }
        val skippedDuplicates = request.candidates.count {
            it.status == CandidateStatus.DUPLICATE ||
                (it.status == CandidateStatus.POSSIBLE_DUPLICATE && it.row.rowIndex !in request.selectedRows)
        }
        val batch = ImportBatch(
            id = batchId,
            accountId = account.id,
            format = request.format,
            fileName = request.fileName,
            fileSha256 = request.fileSha256,
            status = ImportStatus.COMPLETED,
            profileName = request.profileName,
            totalRows = request.candidates.size,
            importedRows = transactions.size,
            duplicateRows = skippedDuplicates,
            errorRows = request.candidates.count { it.status == CandidateStatus.INVALID },
            importedAt = now,
        )
        val inserted = importBatchRepository.saveWithTransactions(batch, transactions)
        return batch.copy(importedRows = inserted, duplicateRows = skippedDuplicates + transactions.size - inserted)
    }

    private companion object {
        /** Como nos movimentos manuais: meio-dia local, para a data não mudar com o fuso. */
        val BOOKING_TIME: LocalTime = LocalTime.NOON
    }
}
