package pt.vcc.financeapp.core.data.importer

import java.time.Clock
import java.time.LocalDate
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
import pt.vcc.financeapp.core.model.AccountBalanceSnapshot
import pt.vcc.financeapp.core.model.BalanceSource
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

/** Saldo final do extrato comparado com o saldo calculado pela app no fim do mesmo dia. */
data class ImportReconciliation(
    val date: LocalDate,
    val statementBalance: Money,
    val appBalance: Money,
) {
    val difference: Money get() = statementBalance - appBalance
    val isReconciled: Boolean get() = difference.amountMinor == 0L
}

data class ImportResult(
    val batch: ImportBatch,
    /** `null` quando o ficheiro não tem coluna de saldo mapeada. */
    val reconciliation: ImportReconciliation?,
)

/**
 * Grava o lote de importação, os movimentos escolhidos e o saldo final do extrato numa única transação e
 * reconcilia esse saldo com o calculado pela app.
 */
class CommitImportUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val importBatchRepository: ImportBatchRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(request: ImportCommitRequest): ImportResult {
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
        val closing = StatementBalance.closing(request.candidates.map { it.row })
        val endOfClosingDay = closing?.date?.plusDays(1)?.atStartOfDay(clock.zone)?.toInstant()
        val snapshot = if (closing != null && endOfClosingDay != null) {
            AccountBalanceSnapshot(
                id = UUID.randomUUID().toString(),
                accountId = account.id,
                balance = Money(closing.balanceMinor, account.currency),
                referenceDate = endOfClosingDay.minusMillis(1),
                source = BalanceSource.IMPORT,
                createdAt = now,
                importBatchId = batchId,
            )
        } else {
            null
        }
        val inserted = importBatchRepository.saveWithTransactions(batch, transactions, snapshot)
        val reconciliation = if (snapshot != null && closing != null && endOfClosingDay != null) {
            val net = transactionRepository.netMovementUntil(account.id, endOfClosingDay)
            ImportReconciliation(closing.date, snapshot.balance, account.openingBalance + Money(net, account.currency))
        } else {
            null
        }
        return ImportResult(
            batch = batch.copy(importedRows = inserted, duplicateRows = skippedDuplicates + transactions.size - inserted),
            reconciliation = reconciliation,
        )
    }

    private companion object {
        /** Como nos movimentos manuais: meio-dia local, para a data não mudar com o fuso. */
        val BOOKING_TIME: LocalTime = LocalTime.NOON
    }
}

/** Desfaz uma importação concluída; devolve o número de movimentos apagados ou `null` se já não for possível. */
class RevertImportUseCase @Inject constructor(
    private val importBatchRepository: ImportBatchRepository,
) {
    suspend operator fun invoke(batchId: String): Int? = importBatchRepository.revert(batchId)
}
