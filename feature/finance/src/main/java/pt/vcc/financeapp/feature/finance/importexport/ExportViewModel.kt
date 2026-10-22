package pt.vcc.financeapp.feature.finance.importexport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.vcc.financeapp.core.data.exporter.ExportDataUseCase
import pt.vcc.financeapp.core.data.exporter.ExportFormat
import pt.vcc.financeapp.core.data.exporter.ExportRange
import pt.vcc.financeapp.core.data.exporter.ExportResult
import pt.vcc.financeapp.core.model.ReportPeriod
import pt.vcc.financeapp.core.model.ReportPeriodType

enum class ExportScope { MONTH, YEAR, ALL }

data class ExportUiState(
    val format: ExportFormat = ExportFormat.CSV,
    val scope: ExportScope = ExportScope.MONTH,
    val period: ReportPeriod,
    /** Movimentos no período escolhido; `null` enquanto carrega. */
    val transactionCount: Int? = null,
    val isExporting: Boolean = false,
    val result: ExportResult? = null,
) {
    /** Nome sugerido ao seletor de destino, ex.: `vccfinance-2026-10.csv`. */
    val fileName: String
        get() {
            val suffix = when (scope) {
                ExportScope.MONTH -> period.month.toString()
                ExportScope.YEAR -> period.year.toString()
                ExportScope.ALL -> "completo"
            }
            return "vccfinance-$suffix.${format.extension}"
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExportViewModel @Inject constructor(
    private val exportData: ExportDataUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExportUiState(period = ReportPeriod.month(YearMonth.now(clock))))
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState
                .map { it.scope to it.period }
                .distinctUntilChanged()
                .flatMapLatest { (scope, period) -> exportData.observeTransactionCount(range(scope, period, "")) }
                .collect { count -> _uiState.update { it.copy(transactionCount = count) } }
        }
    }

    fun selectFormat(format: ExportFormat) = _uiState.update { it.copy(format = format, result = null) }

    fun selectScope(scope: ExportScope) = _uiState.update {
        val period = when (scope) {
            ExportScope.MONTH -> it.period.withType(ReportPeriodType.MONTH, YearMonth.now(clock))
            ExportScope.YEAR -> it.period.withType(ReportPeriodType.YEAR, YearMonth.now(clock))
            ExportScope.ALL -> it.period
        }
        it.copy(scope = scope, period = period, transactionCount = null, result = null)
    }

    fun previousPeriod() = _uiState.update { it.copy(period = it.period.previous(), transactionCount = null, result = null) }

    fun nextPeriod() = _uiState.update { it.copy(period = it.period.next(), transactionCount = null, result = null) }

    /** Escreve no documento [uri] escolhido pelo utilizador; [label] descreve o período dentro do ficheiro. */
    fun export(uri: String, label: String) {
        val state = _uiState.value
        if (state.isExporting) return
        _uiState.update { it.copy(isExporting = true, result = null) }
        viewModelScope.launch {
            val result = exportData(state.format, range(state.scope, state.period, label), uri)
            _uiState.update { it.copy(isExporting = false, result = result) }
        }
    }

    private fun range(scope: ExportScope, period: ReportPeriod, label: String): ExportRange =
        if (scope == ExportScope.ALL) {
            ExportRange(null, null, label)
        } else {
            val (from, to) = period.range(clock.zone)
            ExportRange(from, to, label)
        }
}
