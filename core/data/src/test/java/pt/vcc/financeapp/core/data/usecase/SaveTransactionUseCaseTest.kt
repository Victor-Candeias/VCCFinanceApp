package pt.vcc.financeapp.core.data.usecase

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import pt.vcc.financeapp.core.data.testing.FakeAccountRepository
import pt.vcc.financeapp.core.data.testing.FakeCategoryRepository
import pt.vcc.financeapp.core.data.testing.FakeTransactionRepository
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.model.TransactionType

class SaveTransactionUseCaseTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    private val now = Instant.parse("2026-10-21T09:30:00Z")
    private val account = Account(
        id = "acc-1",
        name = "Conta",
        type = AccountType.BANK_ACCOUNT,
        openingBalance = Money(0, "EUR"),
        createdAt = Instant.EPOCH,
    )
    private val accounts = FakeAccountRepository(listOf(account, account.copy(id = "acc-off", isActive = false)))
    private val categories = FakeCategoryRepository(SystemCategories.all)
    private val transactions = FakeTransactionRepository()
    private val save = SaveTransactionUseCase(transactions, accounts, categories, Clock.fixed(now, zone))

    private val input = TransactionInput(
        accountId = "acc-1",
        type = TransactionType.EXPENSE,
        amountMinor = 1_250,
        description = " Almoço ",
        bookingDate = LocalDate.of(2026, 10, 20),
        categoryId = "system-food-restaurant",
        notes = "  ",
    )

    @Test
    fun createsManualTransactionAtLocalNoonInAccountCurrency() = runTest {
        val saved = (save(input) as SaveTransactionResult.Saved).transaction

        assertEquals(saved, transactions.transactions.value.single())
        assertEquals("Almoço", saved.description)
        assertEquals(Money(1_250, "EUR"), saved.amount)
        assertEquals(Instant.parse("2026-10-20T11:00:00Z"), saved.bookingDate)
        assertEquals(TransactionSource.MANUAL, saved.source)
        assertEquals(null, saved.notes)
        assertEquals(now, saved.createdAt)
    }

    @Test
    fun rejectsInvalidInput() = runTest {
        val result = save(
            input.copy(
                accountId = "acc-off",
                type = TransactionType.INCOME,
                amountMinor = 0,
                description = "",
                categoryId = "system-food",
            ),
        )

        assertEquals(
            SaveTransactionResult.Invalid(
                setOf(
                    TransactionValidationError.ACCOUNT_INACTIVE,
                    TransactionValidationError.AMOUNT_NOT_POSITIVE,
                    TransactionValidationError.BLANK_DESCRIPTION,
                    TransactionValidationError.CATEGORY_MISMATCH,
                ),
            ),
            result,
        )
        assertEquals(emptyList<Any>(), transactions.transactions.value)
    }

    @Test
    fun rejectsTransfersAndEditingTransferLegs() = runTest {
        assertEquals(
            SaveTransactionResult.Invalid(setOf(TransactionValidationError.UNSUPPORTED_TYPE)),
            save(input.copy(type = TransactionType.TRANSFER, categoryId = null)),
        )

        val leg = (save(input) as SaveTransactionResult.Saved).transaction.copy(transferGroupId = "group")
        transactions.transactions.value = listOf(leg)
        assertEquals(
            SaveTransactionResult.Invalid(setOf(TransactionValidationError.TRANSFER_LEG)),
            save(input.copy(id = leg.id)),
        )
    }

    @Test
    fun updateKeepsOriginAndTimeWhenDateIsUnchanged() = runTest {
        val original = (save(input) as SaveTransactionResult.Saved).transaction
            .copy(source = TransactionSource.CSV_IMPORT, externalId = "ext-1", bookingDate = Instant.parse("2026-10-20T07:15:00Z"))
        transactions.transactions.value = listOf(original)

        save(input.copy(id = original.id, amountMinor = 990, categoryId = null))

        assertEquals(
            original.copy(amount = Money(990, "EUR"), categoryId = null, updatedAt = now),
            transactions.transactions.value.single(),
        )
    }

    @Test
    fun seedingSystemCategoriesIsIdempotent() = runTest {
        val repository = FakeCategoryRepository()
        val seed = SeedSystemCategoriesUseCase(repository)

        assertEquals(SystemCategories.all.size, seed())
        assertEquals(0, seed())
        val ids = SystemCategories.all.associateBy { it.id }
        SystemCategories.all.mapNotNull { child -> child.parentCategoryId?.let { child to ids.getValue(it) } }
            .forEach { (child, parent) -> assertEquals(parent.type, child.type) }
    }
}
