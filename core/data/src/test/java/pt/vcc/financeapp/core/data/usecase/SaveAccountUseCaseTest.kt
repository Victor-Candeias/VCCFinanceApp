package pt.vcc.financeapp.core.data.usecase

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.financeapp.core.data.testing.FakeAccountRepository
import pt.vcc.financeapp.core.data.testing.FakeTransactionRepository
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.Transaction
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.model.TransactionType
import pt.vcc.financeapp.core.security.testing.FakeFieldCipher

class SaveAccountUseCaseTest {
    private val now = Instant.parse("2026-10-21T10:00:00Z")
    private val accounts = FakeAccountRepository()
    private val transactions = FakeTransactionRepository()
    private val cipher = FakeFieldCipher()
    private val saveAccount = SaveAccountUseCase(accounts, transactions, Clock.fixed(now, ZoneOffset.UTC), cipher)

    private val input = AccountInput(
        name = "  Conta à ordem ",
        type = AccountType.BANK_ACCOUNT,
        institutionName = " ",
        currency = "eur",
        openingBalanceMinor = 150_000,
    )

    @Test
    fun createsActiveAccountWithNormalizedFields() = runTest {
        val result = saveAccount(input) as SaveAccountResult.Saved

        val stored = accounts.accounts.value.single()
        assertEquals(result.account, stored)
        assertEquals("Conta à ordem", stored.name)
        assertEquals(null, stored.institutionName)
        assertEquals(Money(150_000, "EUR"), stored.openingBalance)
        assertEquals(now, stored.createdAt)
        assertTrue(stored.isActive)
    }

    @Test
    fun reportsAllValidationErrors() = runTest {
        val result = saveAccount(input.copy(name = " ", currency = "EURO"))

        assertEquals(
            SaveAccountResult.Invalid(setOf(AccountValidationError.BLANK_NAME, AccountValidationError.INVALID_CURRENCY)),
            result,
        )
        assertTrue(accounts.accounts.value.isEmpty())
    }

    @Test
    fun updateKeepsIdentityAndStatus() = runTest {
        val existing = Account(
            id = "acc-1",
            name = "Antiga",
            type = AccountType.CASH,
            openingBalance = Money.zero(),
            ibanEncrypted = "cipher",
            isActive = false,
            createdAt = Instant.EPOCH,
        )
        accounts.accounts.value = listOf(existing)

        saveAccount(input.copy(id = "acc-1", name = "Nova", type = AccountType.SAVINGS))

        assertEquals(
            existing.copy(name = "Nova", type = AccountType.SAVINGS, openingBalance = Money(150_000, "EUR")),
            accounts.accounts.value.single(),
        )
    }

    @Test
    fun blocksCurrencyChangeWhenAccountHasTransactions() = runTest {
        accounts.accounts.value = listOf(
            Account(id = "acc-1", name = "Conta", type = AccountType.BANK_ACCOUNT, openingBalance = Money.zero(), createdAt = Instant.EPOCH),
        )
        transactions.transactions.value = listOf(
            Transaction(
                id = "tx-1",
                accountId = "acc-1",
                type = TransactionType.EXPENSE,
                amount = Money(100, "EUR"),
                description = "Café",
                bookingDate = Instant.EPOCH,
                source = TransactionSource.MANUAL,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )

        val result = saveAccount(input.copy(id = "acc-1", currency = "USD"))

        assertEquals(SaveAccountResult.Invalid(setOf(AccountValidationError.CURRENCY_LOCKED)), result)
    }

    @Test
    fun rejectsUnknownAccountId() = runTest {
        assertEquals(
            SaveAccountResult.Invalid(setOf(AccountValidationError.NOT_FOUND)),
            saveAccount(input.copy(id = "missing")),
        )
    }

    @Test
    fun storesIbanEncryptedAndCanRemoveIt() = runTest {
        saveAccount(input.copy(iban = "pt50 0002 0123 1234 5678 9015 4"))
        val stored = accounts.accounts.value.single()
        assertEquals("PT50000201231234567890154", cipher.decrypt(stored.ibanEncrypted!!))
        assertTrue(stored.ibanEncrypted != "PT50000201231234567890154")

        saveAccount(input.copy(id = stored.id, iban = null))
        assertEquals(stored.ibanEncrypted, accounts.accounts.value.single().ibanEncrypted)

        saveAccount(input.copy(id = stored.id, iban = " "))
        assertEquals(null, accounts.accounts.value.single().ibanEncrypted)
    }

    @Test
    fun rejectsInvalidIban() = runTest {
        val result = saveAccount(input.copy(iban = "PT50 0002 0123 1234 5678 9015 5"))

        assertEquals(SaveAccountResult.Invalid(setOf(AccountValidationError.INVALID_IBAN)), result)
    }
}