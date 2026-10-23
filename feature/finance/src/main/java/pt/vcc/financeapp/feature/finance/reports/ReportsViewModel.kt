package pt.vcc.financeapp.feature.finance.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import pt.vcc.financeapp.core.data.repository.AccountRepository
import pt.vcc.financeapp.core.data.repository.CategoryRepository
import pt.vcc.financeapp.core.data.repository.MerchantRepository
import pt.vcc.financeapp.core.data.repository.PurchaseRepository
import pt.vcc.financeapp.core.data.repository.TransactionRepository
import pt.vcc.financeapp.core.model.Account
import pt.vcc.financeapp.core.model.Category
import pt.vcc.financeapp.core.model.CategoryTotal
import pt.vcc.financeapp.core.model.FinancialReport
import pt.vcc.financeapp.core.model.Money
import pt.vcc.financeapp.core.model.MonthTotals
import pt.vcc.financeapp.core.model.PeriodTotals
import pt.vcc.financeapp.core.model.Purchase
import pt.vcc.financeapp.core.model.PurchaseItem
import pt.vcc.financeapp.core.model.PurchaseReport
import pt.vcc.financeapp.core.model.ReportPeriod
import pt.vcc.financeapp.core.model.ReportPeriodType

/** Linha de categoria principal (`categoryId == null` = sem categoria). */
data class ReportCategoryLine(
    val categoryId: String?,
    val name: String?,
    val color: String?,
    val total: Money,
)

data class ReportAccountLine(
    val accountId: String,
    val name: String,
    val totals: PeriodTotals,
)

/** Relatório já resolvido (nomes de categorias e contas) para uma moeda. */
data class ReportContent(
    val currency: String,
    val totals: PeriodTotals,
    val expensesByCategory: List<ReportCategoryLine>,
    val incomeByCategory: List<ReportCategoryLine>,
    val byAccount: List<ReportAccountLine>,
    /** Evolução mensal; só tem elementos nos relatórios anuais. */
    val monthly: List<MonthTotals>,
    val transactionCount: Int,
    /** Compras pagas no período (MB/Dinheiro, tipo, estabelecimento e produto); `null` sem compras. */
    val purchases: PurchaseReport? = null,
    val merchantNames: Map<String, String> = emptyMap(),
)

data class ReportsUiState(
    val period: ReportPeriod,
    val isLoading: Boolean = true,
    /** Moedas com movimentos no período (sem conversões, um relatório por moeda). */
    val currencies: List<String> = emptyList(),
    val content: ReportContent? = null,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val purchaseRepository: PurchaseRepository,
    private val merchantRepository: MerchantRepository,
    private val clock: Clock,
) : ViewModel() {
    private val period = MutableStateFlow(ReportPeriod.month(YearMonth.now(clock)))
    private val selectedCurrency = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ReportsUiState> = period
        .flatMapLatest { period ->
            val (from, to) = period.range(clock.zone)
            val purchases = combine(
                purchaseRepository.observeCompleted(),
                purchaseRepository.observeCompletedItemsInPeriod(from, to),
                merchantRepository.observeAll(),
            ) { purchases, items, merchants -> PurchaseSources(purchases, items, merchants.associate { it.id to it.name }) }
            combine(
                transactionRepository.observeInPeriod(from, to),
                accountRepository.observeAllAccounts(),
                categoryRepository.observeAllCategories(),
                purchases,
                selectedCurrency,
            ) { transactions, accounts, categories, purchaseSources, currency ->
                val byId = categories.associateBy { it.id }
                val reports = FinancialReport.build(period, transactions, clock.zone) { id ->
                    id?.let { byId[it]?.parentCategoryId ?: it }
                }
                val report = reports.firstOrNull { it.currency == currency } ?: reports.firstOrNull()
                ReportsUiState(
                    period = period,
                    isLoading = false,
                    currencies = reports.map { it.currency },
                    content = report?.toContent(accounts, byId)?.copy(
                        purchases = PurchaseReport.from(report, purchaseSources.purchases, purchaseSources.items),
                        merchantNames = purchaseSources.merchantNames,
                    ),
                )
            }
                .catch { emit(ReportsUiState(period = period, isLoading = false, error = it.message)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState(period.value))

    fun previousPeriod() = period.update { it.previous() }

    fun nextPeriod() = period.update { it.next() }

    fun selectType(type: ReportPeriodType) = period.update { it.withType(type, YearMonth.now(clock)) }

    fun selectCurrency(currency: String) {
        selectedCurrency.value = currency
    }

    private class PurchaseSources(val purchases: List<Purchase>, val items: List<PurchaseItem>, val merchantNames: Map<String, String>)

    private fun FinancialReport.toContent(accounts: List<Account>, categories: Map<String, Category>): ReportContent {
        val accountNames = accounts.associate { it.id to it.name }
        return ReportContent(
            currency = currency,
            totals = totals,
            expensesByCategory = expensesByCategory.map { it.toLine(categories) },
            incomeByCategory = incomeByCategory.map { it.toLine(categories) },
            byAccount = byAccount.map { ReportAccountLine(it.accountId, accountNames[it.accountId].orEmpty(), it.totals) },
            monthly = if (period.type == ReportPeriodType.YEAR) monthly else emptyList(),
            transactionCount = transactions.size,
        )
    }

    private fun CategoryTotal.toLine(categories: Map<String, Category>): ReportCategoryLine {
        val category = categoryId?.let(categories::get)
        return ReportCategoryLine(categoryId, category?.name, category?.color, total)
    }
}
