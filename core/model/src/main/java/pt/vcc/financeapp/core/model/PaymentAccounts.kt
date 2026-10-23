package pt.vcc.financeapp.core.model

/** Contas aceites por cada forma de pagamento de uma compra (S8-02). */
object PaymentAccounts {
    /** MB debita uma conta que não seja `CASH`; Dinheiro só aceita contas `CASH`. */
    fun accepts(method: PaymentMethod, type: AccountType): Boolean = when (method) {
        PaymentMethod.MB -> type != AccountType.CASH
        PaymentMethod.CASH -> type == AccountType.CASH
    }

    /** Contas ativas elegíveis, pela ordem recebida. */
    fun eligible(method: PaymentMethod, accounts: List<Account>): List<Account> =
        accounts.filter { it.isActive && accepts(method, it.type) }

    /**
     * Conta pré-selecionada: [preferredId] se for elegível; senão a conta por omissão (se elegível);
     * senão a primeira conta bancária (MB) ou a primeira conta Dinheiro.
     */
    fun defaultFor(method: PaymentMethod, accounts: List<Account>, preferredId: String? = null): Account? {
        val eligible = eligible(method, accounts)
        return eligible.firstOrNull { it.id == preferredId }
            ?: eligible.firstOrNull { it.isDefault }
            ?: eligible.firstOrNull { method == PaymentMethod.MB && it.type == AccountType.BANK_ACCOUNT }
            ?: eligible.firstOrNull()
    }
}
