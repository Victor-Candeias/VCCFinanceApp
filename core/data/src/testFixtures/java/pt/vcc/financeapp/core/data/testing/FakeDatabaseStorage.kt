package pt.vcc.financeapp.core.data.testing

import pt.vcc.financeapp.core.database.DatabaseStorage

/** [DatabaseStorage] em memória: [readable] simula uma base de dados cuja chave se perdeu. */
class FakeDatabaseStorage(var readable: Boolean = true) : DatabaseStorage {
    var deleted = false
        private set

    override fun isReadable(): Boolean = readable

    override fun deleteDatabase() {
        deleted = true
        readable = true
    }
}
