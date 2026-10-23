package pt.vcc.financeapp.core.model

// Os nomes das constantes são persistidos em texto pela base de dados: não renomear sem migration.

enum class AccountType { BANK_ACCOUNT, MEAL_CARD, CREDIT_CARD, CASH, SAVINGS, OTHER }

enum class TransactionType { INCOME, EXPENSE, TRANSFER }

enum class TransactionSource {
    MANUAL,
    CSV_IMPORT,
    EXCEL_IMPORT,
    OFX_IMPORT,
    QIF_IMPORT,
    BANK_SYNC,
    MEAL_CARD_SYNC,

    /** Despesa gerada ao concluir uma compra (S8-02); editável apenas a partir da compra. */
    PURCHASE,
}

enum class TransactionStatus { PENDING, BOOKED, CANCELLED }

enum class ConnectionStatus { DISCONNECTED, PENDING_AUTHORIZATION, CONNECTED, EXPIRED, ERROR }

enum class BudgetPeriod { WEEKLY, MONTHLY, QUARTERLY, YEARLY }

enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }

enum class SyncStatus { RUNNING, SUCCESS, PARTIAL, FAILED }

enum class ImportFormat { CSV, XLSX, OFX, QIF, JSON, PDF }

enum class ImportStatus { PENDING, COMPLETED, FAILED, REVERTED }

enum class RuleMatchField { DESCRIPTION, MERCHANT }

enum class RuleMatchType { CONTAINS, EQUALS, STARTS_WITH, REGEX }

enum class BalanceSource { MANUAL, IMPORT, BANK_SYNC, MEAL_CARD_SYNC }
