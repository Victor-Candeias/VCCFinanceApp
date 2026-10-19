package pt.vcc.financeapp.core.database

import androidx.room.migration.Migration

/**
 * Registo único das migrations manuais. Ao subir [VCCFinanceDatabase.VERSION]:
 * 1. compilar para exportar `schemas/.../<nova versão>.json` e versioná-lo;
 * 2. acrescentar aqui a `Migration(antiga, nova)`;
 * 3. acrescentar a `VCCFinanceMigrationTest` um caso que valide os dados migrados.
 */
object VCCFinanceMigrations {
    val ALL: Array<Migration> = emptyArray()
}
