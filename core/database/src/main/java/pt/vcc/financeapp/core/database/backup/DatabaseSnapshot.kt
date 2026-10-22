package pt.vcc.financeapp.core.database.backup

/** Valor de uma célula: `null`, [Long], [Double], [String] ou [ByteArray]. */
typealias BackupValue = Any?

/** Conteúdo integral de uma tabela; cada linha tem um valor por coluna de [columns]. */
data class BackupTable(
    val name: String,
    val columns: List<String>,
    val rows: List<List<BackupValue>>,
)

/**
 * Fotografia de todas as tabelas da base de dados na versão de esquema [schemaVersion]. Os campos
 * cifrados com a chave do dispositivo seguem decifrados (ex.: `accounts.iban`), porque a chave não
 * sai do Keystore; o ficheiro de backup é cifrado como um todo.
 */
data class DatabaseSnapshot(
    val schemaVersion: Int,
    val tables: List<BackupTable>,
) {
    fun rowCount(table: String): Int = tables.firstOrNull { it.name == table }?.rows?.size ?: 0
}

/** O backup não corresponde ao esquema desta versão da app (mais recente ou com tabelas/colunas desconhecidas). */
class IncompatibleBackupException(message: String) : Exception(message)

interface DatabaseBackupStore {
    suspend fun snapshot(): DatabaseSnapshot

    /**
     * Substitui todos os dados por [snapshot] numa única transação: ou fica tudo restaurado ou nada
     * muda. Lança [IncompatibleBackupException] se o esquema não for compatível.
     */
    suspend fun restore(snapshot: DatabaseSnapshot)
}
