package pt.vcc.financeapp.core.data.exporter

import java.io.IOException
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import pt.vcc.financeapp.core.data.repository.AccountRepository
import pt.vcc.financeapp.core.data.repository.CategoryRepository
import pt.vcc.financeapp.core.data.repository.TransactionRepository

/** Gera o PDF; separado porque usa `android.graphics.pdf` e não corre nos testes JVM. */
fun interface PdfExportWriter {
    fun write(data: ExportData, out: OutputStream)
}

sealed interface ExportResult {
    data class Exported(val transactionCount: Int) : ExportResult
    data object Failed : ExportResult
}

class ExportDataUseCase(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val documentWriter: DocumentWriter,
    private val pdfWriter: PdfExportWriter,
    private val clock: Clock,
    private val dispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(
        accountRepository: AccountRepository,
        categoryRepository: CategoryRepository,
        transactionRepository: TransactionRepository,
        documentWriter: DocumentWriter,
        pdfWriter: PdfExportWriter,
        clock: Clock,
    ) : this(accountRepository, categoryRepository, transactionRepository, documentWriter, pdfWriter, clock, Dispatchers.IO)

    fun observeTransactionCount(range: ExportRange): Flow<Int> =
        transactionRepository.observeInPeriod(range.fromOrMin(), range.toOrMax()).map { it.size }

    suspend fun load(range: ExportRange): ExportData = ExportData(
        generatedAt = clock.instant(),
        zone = clock.zone,
        range = range,
        accounts = accountRepository.observeAllAccounts().first().sortedBy { it.name.lowercase() },
        categories = categoryRepository.observeAllCategories().first()
            .sortedWith(compareBy({ it.type }, { it.name.lowercase() })),
        transactions = transactionRepository.observeInPeriod(range.fromOrMin(), range.toOrMax()).first()
            .sortedWith(compareBy({ it.bookingDate }, { it.createdAt })),
    )

    suspend operator fun invoke(format: ExportFormat, range: ExportRange, uri: String): ExportResult =
        withContext(dispatcher) {
            try {
                val data = load(range)
                documentWriter.write(uri) { out ->
                    when (format) {
                        ExportFormat.CSV -> CsvExporter.write(data, out)
                        ExportFormat.XLSX -> XlsxExporter.write(data, out)
                        ExportFormat.PDF -> pdfWriter.write(data, out)
                        ExportFormat.JSON -> JsonExporter.write(data, out)
                    }
                }
                ExportResult.Exported(data.transactions.size)
            } catch (e: IOException) {
                ExportResult.Failed
            } catch (e: SecurityException) {
                ExportResult.Failed
            } catch (e: IllegalArgumentException) {
                ExportResult.Failed
            }
        }

    private fun ExportRange.fromOrMin(): Instant = from ?: Instant.ofEpochMilli(Long.MIN_VALUE)
    private fun ExportRange.toOrMax(): Instant = to ?: Instant.ofEpochMilli(Long.MAX_VALUE)
}
