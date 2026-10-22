package pt.vcc.financeapp.core.security

import java.util.Base64
import javax.inject.Inject

/** Cifra de campos sensíveis guardados na base de dados (por exemplo, o IBAN). */
interface FieldCipher {
    fun encrypt(plainText: String): String

    /** `null` se o valor não puder ser decifrado (chave perdida ou dados corrompidos). */
    fun decrypt(cipherText: String): String?

    /** Apaga a chave; os valores já cifrados deixam de poder ser lidos. */
    fun deleteKey()
}

/** [FieldCipher] com AES-GCM e uma chave própria do Keystore; o texto guardado é `v1:<Base64>`. */
internal class KeystoreFieldCipher @Inject constructor() : FieldCipher {
    private val aes = KeystoreAesGcm(ALIAS)

    override fun encrypt(plainText: String): String =
        PREFIX + Base64.getEncoder().encodeToString(aes.encrypt(plainText.toByteArray(Charsets.UTF_8)))

    override fun decrypt(cipherText: String): String? {
        if (!cipherText.startsWith(PREFIX)) return null
        val blob = runCatching { Base64.getDecoder().decode(cipherText.substring(PREFIX.length)) }.getOrNull() ?: return null
        return aes.decrypt(blob)?.toString(Charsets.UTF_8)
    }

    override fun deleteKey() = aes.deleteKey()

    private companion object {
        const val ALIAS = "vccfinance_field_key"
        const val PREFIX = "v1:"
    }
}
