package pt.vcc.financeapp.core.data.exporter

import java.io.OutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * JSON estruturado com identificadores e valores em unidades mínimas (`amountMinor`), para
 * interoperabilidade. Não é o formato de backup: não inclui orçamentos, regras nem anexos.
 */
object JsonExporter {
    const val FORMAT = "vccfinanceapp-export"
    const val VERSION = 1

    private val json = Json { prettyPrint = true }

    fun write(data: ExportData, out: OutputStream) {
        val root = buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("generatedAt", data.generatedAt.toString())
            putJsonObject("period") {
                put("label", data.range.label)
                put("from", data.range.from?.toString())
                put("to", data.range.to?.toString())
            }
            putJsonArray("accounts") {
                data.accounts.forEach { account ->
                    addJsonObject {
                        put("id", account.id)
                        put("name", account.name)
                        put("type", account.type.name)
                        put("institutionName", account.institutionName)
                        put("currency", account.currency)
                        put("openingBalanceMinor", account.openingBalance.amountMinor)
                        put("active", account.isActive)
                    }
                }
            }
            putJsonArray("categories") {
                data.categories.forEach { category ->
                    addJsonObject {
                        put("id", category.id)
                        put("name", category.name)
                        put("type", category.type.name)
                        put("parentCategoryId", category.parentCategoryId)
                        put("system", category.isSystem)
                        put("active", category.isActive)
                    }
                }
            }
            putJsonArray("transactions") {
                data.transactions.forEach { tx ->
                    addJsonObject {
                        put("id", tx.id)
                        put("accountId", tx.accountId)
                        put("type", tx.type.name)
                        put("status", tx.status.name)
                        put("bookingDate", tx.bookingDate.toString())
                        put("valueDate", tx.valueDate?.toString())
                        put("description", tx.description)
                        put("amountMinor", tx.amount.amountMinor)
                        put("currency", tx.amount.currency)
                        put("categoryId", tx.categoryId)
                        put("transferGroupId", tx.transferGroupId)
                        put("source", tx.source.name)
                        put("notes", tx.notes)
                    }
                }
            }
            putJsonArray("totals") {
                data.totals.forEach { totals ->
                    addJsonObject { putTotals(totals.currency, totals.income.amountMinor, totals.expenses.amountMinor) }
                }
            }
        }
        out.write(json.encodeToString(JsonElement.serializer(), root).toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun JsonObjectBuilder.putTotals(currency: String, income: Long, expenses: Long) {
        put("currency", currency)
        put("incomeMinor", income)
        put("expensesMinor", expenses)
        put("netMinor", income - expenses)
    }
}
