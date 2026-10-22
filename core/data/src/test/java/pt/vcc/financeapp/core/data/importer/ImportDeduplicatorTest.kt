package pt.vcc.financeapp.core.data.importer

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.financeapp.core.data.testing.FakeAccountRepository
import pt.vcc.financeapp.core.data.testing.FakeImportBatchRepository
import pt.vcc.financeapp.core.data.testing.FakeTransactionRepository
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.ImportFormat
import pt.vcc.financeapp.core.model.ImportStatus
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.Transaction
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.model.TransactionType

class ImportDeduplicatorTest {
    private val zone = ZoneOffset.UTC
    private val clock = Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), zone)
    private val account = Account("acc", "CGD", AccountType.BANK_ACCOUNT, Money(0, "EUR"), createdAt = clock.instant())

    @Test
    fun identicalRowsInTheSameFileGetDistinctStableIds() {
        val rows = listOf(row(0, "2026-01-02", "Café", 150), row(1, "2026-01-02", "CAFÉ", 150), row(2, "2026-01-03", "Pão", 90))

        val ids = ImportDeduplicator.externalIds(rows)

        assertNotEquals(ids[0], ids[1])
        assertEquals(ids, ImportDeduplicator.externalIds(rows))
        // Num extrato sobreposto, a mesma linha mantém o identificador mesmo noutra posição.
        assertEquals(ids[2], ImportDeduplicator.externalIds(listOf(row(7, "2026-01-03", "Pão", 90)))[7])
        assertTrue(ids.values.all { it.startsWith("h:") && it.length == 66 })
    }

    @Test
    fun classifiesNewInvalidAndPossibleDuplicates() {
        val manual = transaction("m1", "2026-01-03", "Supermercado", 2_000)
        val rows = listOf(
            row(1, "2026-01-02", "Continente", 2_000),
            row(2, "2026-01-02", "Outro", 2_000),
            row(3, "2026-01-10", "Livraria", 990),
            row(4, null, "Sem data", 100, RowError.INVALID_DATE),
        )

        val candidates = ImportDeduplicator.classify(rows, listOf(manual), zone)

        assertEquals(
            listOf(CandidateStatus.POSSIBLE_DUPLICATE, CandidateStatus.NEW, CandidateStatus.NEW, CandidateStatus.INVALID),
            candidates.map { it.status },
        )
        // O movimento manual só justifica um possível duplicado.
        assertEquals("m1", candidates[0].match?.id)
        assertNull(candidates[3].externalId)
    }

    @Test
    fun reimportingTheSameFileOnlyFlagsDuplicates() = runTest {
        val transactions = FakeTransactionRepository()
        val batches = FakeImportBatchRepository(transactions)
        val review = ReviewImportUseCase(transactions, batches, clock, Dispatchers.Unconfined)
        val commit = CommitImportUseCase(FakeAccountRepository(listOf(account)), transactions, batches, clock)
        val rows = listOf(row(1, "2026-01-02", "Café", 150), row(2, "2026-01-02", "Café", 150), row(3, "2026-01-05", "Salário", 200_000, type = TransactionType.INCOME))

        val first = review(account.id, "sha", rows)
        assertNull(first.previousImport)
        val batch = commit(request(first.candidates, first.candidates.map { it.row.rowIndex }.toSet())).batch

        assertEquals(3, batch.importedRows)
        assertEquals(3, transactions.transactions.value.size)
        val saved = transactions.transactions.value.first()
        assertEquals(TransactionSource.CSV_IMPORT, saved.source)
        assertEquals(batch.id, saved.importBatchId)
        assertEquals(Instant.parse("2026-01-02T12:00:00Z"), saved.bookingDate)

        val second = review(account.id, "sha", rows)
        assertEquals(batch.id, second.previousImport?.id)
        assertEquals(3, second.count(CandidateStatus.DUPLICATE))
        val again = commit(request(second.candidates, second.candidates.map { it.row.rowIndex }.toSet())).batch
        assertEquals(0, again.importedRows)
        assertEquals(3, again.duplicateRows)
        assertEquals(3, transactions.transactions.value.size)
    }

    @Test
    fun unselectedPossibleDuplicatesAreSkipped() = runTest {
        val transactions = FakeTransactionRepository(listOf(transaction("m1", "2026-01-02", "Café", 150)))
        val batches = FakeImportBatchRepository(transactions)
        val review = ReviewImportUseCase(transactions, batches, clock, Dispatchers.Unconfined)
        val commit = CommitImportUseCase(FakeAccountRepository(listOf(account)), transactions, batches, clock)

        val result = review(account.id, "sha", listOf(row(1, "2026-01-02", "Café", 150), row(2, "2026-01-04", "Pão", 90)))
        val batch = commit(request(result.candidates, setOf(2))).batch

        assertEquals(1, batch.importedRows)
        assertEquals(1, batch.duplicateRows)
        assertEquals(listOf("m1", "Pão"), transactions.transactions.value.map { if (it.id == "m1") it.id else it.description })
    }

    @Test
    fun closingBalanceIsReconciledAndRevertUndoesTheImport() = runTest {
        val opening = account.copy(openingBalance = Money(10_000, "EUR"))
        val transactions = FakeTransactionRepository(listOf(transaction("m1", "2026-01-01", "Renda", 1_000)))
        val batches = FakeImportBatchRepository(transactions)
        val review = ReviewImportUseCase(transactions, batches, clock, Dispatchers.Unconfined)
        val commit = CommitImportUseCase(FakeAccountRepository(listOf(opening)), transactions, batches, clock)
        // Extrato em ordem decrescente: o saldo final é o da primeira linha.
        val rows = listOf(
            row(1, "2026-01-05", "Salário", 5_000, type = TransactionType.INCOME, balance = 13_850),
            row(2, "2026-01-03", "Pão", 150, balance = 8_850),
        )

        val candidates = review(account.id, "sha", rows).candidates
        val result = commit(request(candidates, setOf(1, 2)))

        val reconciliation = result.reconciliation!!
        assertEquals(LocalDate.parse("2026-01-05"), reconciliation.date)
        assertEquals(Money(13_850, "EUR"), reconciliation.appBalance)
        assertTrue(reconciliation.isReconciled)
        val snapshot = batches.snapshots.value.single()
        assertEquals(result.batch.id, snapshot.importBatchId)
        assertEquals(Instant.parse("2026-01-05T23:59:59.999Z"), snapshot.referenceDate)

        assertEquals(2, RevertImportUseCase(batches)(result.batch.id))
        assertEquals(listOf("m1"), transactions.transactions.value.map { it.id })
        assertTrue(batches.snapshots.value.isEmpty())
        assertEquals(ImportStatus.REVERTED, batches.batches.value.single().status)
        assertNull(RevertImportUseCase(batches)(result.batch.id))
        // Depois de revertido, o mesmo ficheiro volta a ser importável.
        val again = review(account.id, "sha", rows)
        assertNull(again.previousImport)
        assertEquals(2, again.count(CandidateStatus.NEW))
    }

    @Test
    fun closingBalanceDetectsFileOrder() {
        val ascending = listOf(
            row(1, "2026-01-03", "Pão", 150, balance = 850),
            row(2, "2026-01-03", "Café", 100, balance = 750),
        )
        assertEquals(StatementClosingBalance(LocalDate.parse("2026-01-03"), 750), StatementBalance.closing(ascending))
        assertEquals(StatementClosingBalance(LocalDate.parse("2026-01-03"), 750), StatementBalance.closing(ascending.reversed()))
        assertEquals(
            StatementClosingBalance(LocalDate.parse("2026-01-09"), 1),
            StatementBalance.closing(listOf(row(1, "2026-01-09", "A", 5, balance = 1), row(2, "2026-01-02", "B", 5, balance = 99))),
        )
        assertNull(StatementBalance.closing(listOf(row(1, "2026-01-03", "Pão", 150))))
    }

    private fun request(candidates: List<ImportCandidate>, selected: Set<Int>) =
        ImportCommitRequest(account.id, "extrato.csv", ImportFormat.CSV, "sha", "CGD", candidates, selected)

    private fun row(
        index: Int,
        date: String?,
        description: String,
        amountMinor: Long,
        error: RowError? = null,
        type: TransactionType = TransactionType.EXPENSE,
        balance: Long? = null,
    ) = ParsedImportRow(
        rowIndex = index,
        bookingDate = date?.let(LocalDate::parse),
        valueDate = null,
        description = description,
        type = type,
        amountMinor = amountMinor,
        reference = null,
        balanceMinor = balance,
        error = error,
    )

    private fun transaction(id: String, date: String, description: String, amountMinor: Long) = Transaction(
        id = id,
        accountId = account.id,
        type = TransactionType.EXPENSE,
        amount = Money(amountMinor, "EUR"),
        description = description,
        bookingDate = LocalDate.parse(date).atTime(12, 0).toInstant(zone),
        source = TransactionSource.MANUAL,
        createdAt = clock.instant(),
        updatedAt = clock.instant(),
    )
}
