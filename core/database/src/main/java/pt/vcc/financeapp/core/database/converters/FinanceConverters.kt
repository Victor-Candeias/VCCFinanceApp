package pt.vcc.financeapp.core.database.converters

import androidx.room.TypeConverter
import pt.vcc.financeapp.core.model.AccountType
import pt.vcc.financeapp.core.model.BalanceSource
import pt.vcc.financeapp.core.model.BudgetPeriod
import pt.vcc.financeapp.core.model.ConnectionStatus
import pt.vcc.financeapp.core.model.ImportFormat
import pt.vcc.financeapp.core.model.ImportStatus
import pt.vcc.financeapp.core.model.RecurrenceFrequency
import pt.vcc.financeapp.core.model.RuleMatchField
import pt.vcc.financeapp.core.model.RuleMatchType
import pt.vcc.financeapp.core.model.SyncStatus
import pt.vcc.financeapp.core.model.TransactionSource
import pt.vcc.financeapp.core.model.TransactionStatus
import pt.vcc.financeapp.core.model.TransactionType

/** Enums persistidos pelo nome, para que reordenar constantes não corrompa dados. */
class FinanceConverters {
    @TypeConverter fun accountTypeToString(v: AccountType): String = v.name
    @TypeConverter fun stringToAccountType(v: String): AccountType = AccountType.valueOf(v)

    @TypeConverter fun transactionTypeToString(v: TransactionType): String = v.name
    @TypeConverter fun stringToTransactionType(v: String): TransactionType = TransactionType.valueOf(v)

    @TypeConverter fun sourceToString(v: TransactionSource): String = v.name
    @TypeConverter fun stringToSource(v: String): TransactionSource = TransactionSource.valueOf(v)

    @TypeConverter fun statusToString(v: TransactionStatus): String = v.name
    @TypeConverter fun stringToStatus(v: String): TransactionStatus = TransactionStatus.valueOf(v)

    @TypeConverter fun connectionStatusToString(v: ConnectionStatus): String = v.name
    @TypeConverter fun stringToConnectionStatus(v: String): ConnectionStatus = ConnectionStatus.valueOf(v)

    @TypeConverter fun budgetPeriodToString(v: BudgetPeriod): String = v.name
    @TypeConverter fun stringToBudgetPeriod(v: String): BudgetPeriod = BudgetPeriod.valueOf(v)

    @TypeConverter fun recurrenceFrequencyToString(v: RecurrenceFrequency): String = v.name
    @TypeConverter fun stringToRecurrenceFrequency(v: String): RecurrenceFrequency = RecurrenceFrequency.valueOf(v)

    @TypeConverter fun syncStatusToString(v: SyncStatus): String = v.name
    @TypeConverter fun stringToSyncStatus(v: String): SyncStatus = SyncStatus.valueOf(v)

    @TypeConverter fun importFormatToString(v: ImportFormat): String = v.name
    @TypeConverter fun stringToImportFormat(v: String): ImportFormat = ImportFormat.valueOf(v)

    @TypeConverter fun importStatusToString(v: ImportStatus): String = v.name
    @TypeConverter fun stringToImportStatus(v: String): ImportStatus = ImportStatus.valueOf(v)

    @TypeConverter fun ruleMatchFieldToString(v: RuleMatchField): String = v.name
    @TypeConverter fun stringToRuleMatchField(v: String): RuleMatchField = RuleMatchField.valueOf(v)

    @TypeConverter fun ruleMatchTypeToString(v: RuleMatchType): String = v.name
    @TypeConverter fun stringToRuleMatchType(v: String): RuleMatchType = RuleMatchType.valueOf(v)

    @TypeConverter fun balanceSourceToString(v: BalanceSource): String = v.name
    @TypeConverter fun stringToBalanceSource(v: String): BalanceSource = BalanceSource.valueOf(v)
}
