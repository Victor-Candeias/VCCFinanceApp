package pt.vcc.financeapp.core.data.usecase

import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import pt.vcc.financeapp.core.data.repository.AccountRepository
import pt.vcc.financeapp.core.data.repository.CatalogRepository
import pt.vcc.financeapp.core.data.repository.CategoryRepository
import pt.vcc.financeapp.core.data.repository.MerchantRepository
import pt.vcc.financeapp.core.data.repository.PurchaseRepository
import pt.vcc.financeapp.core.data.repository.ShoppingListRepository
import pt.vcc.financeapp.core.data.repository.TransactionRepository
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.PaymentAccounts
import pt.vcc.financeapp.core.model.PaymentMethod
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.core.model.PurchaseItem
import pt.vcc.financeapp.core.model.PurchaseMath
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.core.model.PurchaseType
import pt.vcc.financeapp.core.model.Transaction
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.model.TransactionStatus
import pt.vcc.financeapp.core.model.TransactionType

/**
 * Pagamento de uma compra em curso (conclusão) ou já concluída (edição).
 * [categoryId] nulo usa a categoria da compra, a do catálogo dos itens ou uma categoria por omissão do tipo de compra.
 * [date] nulo mantém a data da compra; [time] (despesas rápidas) fixa também a hora.
 */
data class CompletePurchaseInput(
    val purchaseId: String,
    val paymentMethod: PaymentMethod,
    val accountId: String,
    val categoryId: String? = null,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
)

enum class CompletePurchaseError {
    NOT_FOUND,
    CANCELLED,
    ACCOUNT_NOT_FOUND,
    ACCOUNT_INACTIVE,
    /** MB com conta Dinheiro ou Dinheiro com conta bancária. */
    ACCOUNT_NOT_ALLOWED,
    CATEGORY_MISMATCH,
}

sealed interface CompletePurchaseResult {
    /** [transaction] é nulo quando o total é 0 (sem movimento). */
    data class Completed(val purchase: Purchase, val transaction: Transaction?) : CompletePurchaseResult
    data class Invalid(val errors: Set<CompletePurchaseError>) : CompletePurchaseResult
}

/**
 * Conclui (ou volta a gravar) uma compra e sincroniza a despesa `PURCHASE` ligada, tudo numa única
 * transação SQL (`PurchaseRepository.persistWithTransaction`). A validação é feita antes de escrever.
 */
class CompletePurchaseUseCase @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val catalogRepository: CatalogRepository,
    private val merchantRepository: MerchantRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(input: CompletePurchaseInput): CompletePurchaseResult {
        val detail = purchaseRepository.getDetail(input.purchaseId)
            ?: return CompletePurchaseResult.Invalid(setOf(CompletePurchaseError.NOT_FOUND))
        val purchase = detail.purchase
        val errors = mutableSetOf<CompletePurchaseError>()
        if (purchase.status == PurchaseStatus.CANCELLED) errors += CompletePurchaseError.CANCELLED

        val account = accountRepository.getById(input.accountId)
        when {
            account == null -> errors += CompletePurchaseError.ACCOUNT_NOT_FOUND
            // Uma compra antiga de uma conta entretanto desativada continua editável.
            !account.isActive && account.id != purchase.accountId -> errors += CompletePurchaseError.ACCOUNT_INACTIVE
            !PaymentAccounts.accepts(input.paymentMethod, account.type) -> errors += CompletePurchaseError.ACCOUNT_NOT_ALLOWED
        }

        // Uma categoria escolhida (agora ou antes) tem de ser de despesa; a inferida é descartada se não existir.
        val chosenCategoryId = input.categoryId ?: purchase.categoryId
        if (chosenCategoryId != null && categoryRepository.getById(chosenCategoryId)?.type != TransactionType.EXPENSE) {
            errors += CompletePurchaseError.CATEGORY_MISMATCH
        }
        if (errors.isNotEmpty() || account == null) return CompletePurchaseResult.Invalid(errors)
        val validCategoryId = chosenCategoryId
            ?: inferCategory(purchase.type, detail.items)?.takeIf { categoryRepository.getById(it)?.type == TransactionType.EXPENSE }

        val now = clock.instant()
        val currentDate = purchase.occurredAt.atZone(clock.zone).toLocalDate()
        val occurredAt = when {
            input.time != null -> (input.date ?: currentDate).atTime(input.time).atZone(clock.zone).toInstant()
            else -> input.date
                ?.takeIf { it != currentDate }
                ?.atTime(LocalTime.NOON)?.atZone(clock.zone)?.toInstant()
                ?: purchase.occurredAt
        }
        val items = detail.items.sortedBy(PurchaseItem::position)
        val total = PurchaseMath.total(items)

        val transaction = if (total > 0) {
            val existing = purchase.transactionId?.let { transactionRepository.getById(it) }
            Transaction(
                id = existing?.id ?: UUID.randomUUID().toString(),
                accountId = account.id,
                type = TransactionType.EXPENSE,
                amount = Money(total, account.currency),
                description = describe(purchase, items),
                bookingDate = occurredAt,
                source = TransactionSource.PURCHASE,
                status = TransactionStatus.BOOKED,
                categoryId = validCategoryId,
                merchantId = purchase.merchantId,
                notes = purchase.notes,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        } else {
            null
        }
        val completed = purchase.copy(
            status = PurchaseStatus.COMPLETED,
            occurredAt = occurredAt,
            currency = account.currency,
            totalMinor = total,
            paymentMethod = input.paymentMethod,
            accountId = account.id,
            categoryId = validCategoryId,
            transactionId = transaction?.id,
            updatedAt = now,
            completedAt = purchase.completedAt ?: now,
        )
        purchaseRepository.persistWithTransaction(
            purchase = completed,
            items = items,
            transaction = transaction,
            obsoleteTransactionId = purchase.transactionId,
        )
        return CompletePurchaseResult.Completed(completed, transaction)
    }

    /** Categoria de despesa com maior peso no total, a partir do catálogo; senão a categoria por omissão do tipo. */
    private suspend fun inferCategory(type: PurchaseType, items: List<PurchaseItem>): String? {
        val weights = mutableMapOf<String, Long>()
        for (item in items) {
            val product = item.productId?.let { catalogRepository.getProduct(it) } ?: continue
            val financeCategory = product.productCategoryId?.let { catalogRepository.getCategory(it) }?.financeCategoryId ?: continue
            weights[financeCategory] = (weights[financeCategory] ?: 0L) + item.totalMinor
        }
        return weights.maxByOrNull { it.value }?.key ?: DEFAULT_CATEGORY.getValue(type)
    }

    private suspend fun describe(purchase: Purchase, items: List<PurchaseItem>): String {
        val merchant = purchase.merchantId?.let { merchantRepository.getById(it) }?.name
        val list = purchase.shoppingListId?.let { shoppingListRepository.getList(it) }?.name
        val base = when (purchase.type) {
            PurchaseType.QUICK -> merchant ?: "Compra rápida"
            PurchaseType.PLANNED -> merchant ?: list?.let { "Compra: $it" } ?: "Compra planeada"
            PurchaseType.EXPRESS -> merchant ?: items.singleOrNull()?.name ?: "Despesa rápida"
        }
        return base.take(SaveTransactionUseCase.MAX_DESCRIPTION_LENGTH)
    }

    companion object {
        val DEFAULT_CATEGORY: Map<PurchaseType, String> = mapOf(
            PurchaseType.QUICK to "system-food-supermarket",
            PurchaseType.PLANNED to "system-food-supermarket",
            PurchaseType.EXPRESS to "system-other-expense",
        )
    }
}

/** Elimina a compra (em curso ou concluída), os itens e a despesa que gerou, atomicamente. */
class DeletePurchaseUseCase @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
) {
    suspend operator fun invoke(purchaseId: String): Boolean {
        val purchase = purchaseRepository.getById(purchaseId) ?: return false
        purchaseRepository.delete(purchase)
        return true
    }
}

/** Cria a conta "Dinheiro" (saldo inicial 0) quando o utilizador paga em numerário e ainda não a tem. */
class CreateCashAccountUseCase @Inject constructor(
    private val saveAccount: SaveAccountUseCase,
) {
    suspend operator fun invoke(currency: String = Money.DEFAULT_CURRENCY): Account? {
        val result = saveAccount(
            AccountInput(name = "Dinheiro", type = AccountType.CASH, institutionName = null, currency = currency, openingBalanceMinor = 0),
        )
        return (result as? SaveAccountResult.Saved)?.account
    }
}

/** Compra em curso do [type]; criada vazia se ainda não existir. */
class GetOrCreateActivePurchaseUseCase @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(type: PurchaseType, shoppingListId: String? = null, budgetMinor: Long? = null): Purchase {
        purchaseRepository.getActive(type)
            ?.takeIf { shoppingListId == null || it.shoppingListId == shoppingListId }
            ?.let { return it }
        val now = clock.instant()
        val purchase = Purchase(
            id = UUID.randomUUID().toString(),
            type = type,
            status = PurchaseStatus.ACTIVE,
            occurredAt = now,
            shoppingListId = shoppingListId,
            budgetMinor = budgetMinor,
            createdAt = now,
            updatedAt = now,
        )
        purchaseRepository.create(purchase)
        return purchase
    }
}
