package pt.vcc.financeapp.core.database.backup

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Inject
import pt.vcc.financeapp.core.database.VCCFinanceDatabase
import pt.vcc.financeapp.core.security.FieldCipher

/**
 * Lê e substitui as tabelas diretamente em SQL, para que o backup acompanhe o esquema sem mapeamentos
 * por entidade. As tabelas são descobertas em `sqlite_master` e ordenadas pelas chaves estrangeiras.
 */
class RoomDatabaseBackupStore @Inject constructor(
    private val database: VCCFinanceDatabase,
    private val fieldCipher: FieldCipher,
) : DatabaseBackupStore {

    override suspend fun snapshot(): DatabaseSnapshot = database.withTransaction {
        val db = database.openHelper.writableDatabase
        val tables = orderedTables(db).map { name ->
            db.query("SELECT * FROM `$name` ORDER BY rowid").use { cursor -> readTable(name, cursor) }
        }
        DatabaseSnapshot(VCCFinanceDatabase.VERSION, tables)
    }

    override suspend fun restore(snapshot: DatabaseSnapshot) {
        if (snapshot.schemaVersion > VCCFinanceDatabase.VERSION) {
            throw IncompatibleBackupException("Esquema ${snapshot.schemaVersion} mais recente do que ${VCCFinanceDatabase.VERSION}")
        }
        database.withTransaction {
            val db = database.openHelper.writableDatabase
            val ordered = orderedTables(db)
            val current = ordered.associateWith { columnsOf(db, it) }
            snapshot.tables.forEach { table ->
                val columns = current[table.name] ?: throw IncompatibleBackupException("Tabela desconhecida: ${table.name}")
                val unknown = table.columns.map { if (table.name == ACCOUNTS && it == IBAN_PLAIN) IBAN_COLUMN else it } - columns
                if (unknown.isNotEmpty()) throw IncompatibleBackupException("Colunas desconhecidas em ${table.name}: $unknown")
            }
            // As restrições (incluindo a autorreferência das categorias) só são validadas no commit.
            db.execSQL("PRAGMA defer_foreign_keys = ON")
            ordered.asReversed().forEach { db.execSQL("DELETE FROM `$it`") }
            val byName = snapshot.tables.associateBy { it.name }
            ordered.forEach { name -> byName[name]?.let { insertTable(db, it) } }
            // Verificação explícita antes do commit: uma falha no COMMIT diferido não reverte de forma fiável.
            db.query("PRAGMA foreign_key_check").use { cursor ->
                if (cursor.moveToFirst()) throw IncompatibleBackupException("Referência inválida em ${cursor.getString(0)}")
            }
        }
    }

    private fun readTable(name: String, cursor: Cursor): BackupTable {
        val columns = cursor.columnNames.toList()
        val ibanIndex = if (name == ACCOUNTS) columns.indexOf(IBAN_COLUMN) else -1
        val rows = buildList {
            while (cursor.moveToNext()) {
                add(
                    List(columns.size) { i ->
                        val value = when (cursor.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> null
                            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                            Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(i)
                            else -> cursor.getString(i)
                        }
                        if (i == ibanIndex) (value as? String)?.let(fieldCipher::decrypt) else value
                    },
                )
            }
        }
        val names = if (ibanIndex >= 0) columns.toMutableList().also { it[ibanIndex] = IBAN_PLAIN } else columns
        return BackupTable(name, names, rows)
    }

    private fun insertTable(db: SupportSQLiteDatabase, table: BackupTable) {
        table.rows.forEach { row ->
            require(row.size == table.columns.size) { "Linha com ${row.size} valores em ${table.name}" }
            val values = ContentValues(row.size)
            table.columns.forEachIndexed { i, column ->
                if (table.name == ACCOUNTS && column == IBAN_PLAIN) {
                    values.put(IBAN_COLUMN, (row[i] as? String)?.let(fieldCipher::encrypt))
                } else {
                    when (val value = row[i]) {
                        null -> values.putNull(column)
                        is Long -> values.put(column, value)
                        is Double -> values.put(column, value)
                        is String -> values.put(column, value)
                        is ByteArray -> values.put(column, value)
                        else -> throw IllegalArgumentException("Tipo não suportado em ${table.name}.$column")
                    }
                }
            }
            db.insert(table.name, SQLiteDatabase.CONFLICT_ABORT, values)
        }
    }

    /** Tabelas da app com as referenciadas antes das que as referenciam. */
    private fun orderedTables(db: SupportSQLiteDatabase): List<String> {
        val names = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT LIKE 'room_%' AND name <> 'android_metadata' ORDER BY name",
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        val parents = names.associateWith { name ->
            db.query("PRAGMA foreign_key_list(`$name`)").use { cursor ->
                val column = cursor.getColumnIndexOrThrow("table")
                buildSet { while (cursor.moveToNext()) add(cursor.getString(column)) } - name
            }
        }
        val ordered = LinkedHashSet<String>()
        fun visit(name: String, path: Set<String>) {
            if (name in ordered || name in path || name !in parents) return
            parents.getValue(name).forEach { visit(it, path + name) }
            ordered += name
        }
        names.forEach { visit(it, emptySet()) }
        return ordered.toList()
    }

    private fun columnsOf(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val column = cursor.getColumnIndexOrThrow("name")
            buildList { while (cursor.moveToNext()) add(cursor.getString(column)) }
        }

    private companion object {
        const val ACCOUNTS = "accounts"
        const val IBAN_COLUMN = "ibanEncrypted"
        const val IBAN_PLAIN = "iban"
    }
}
