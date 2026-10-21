package pt.vcc.financeapp.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.inject.Inject

/**
 * Segredo que não sai do dispositivo, aplicado ao resultado do PBKDF2. Sem ele, uma cópia do
 * verificador não chega para testar PINs fora do dispositivo, onde o limite de tentativas não se
 * aplica.
 */
interface PinPepper {
    fun createKey()

    /** HMAC do [input]; `null` se a chave não existir (por exemplo, depois de limpar o Keystore). */
    fun apply(input: ByteArray): ByteArray?

    fun deleteKey()
}

/** [PinPepper] com uma chave HMAC-SHA256 não exportável no Android Keystore. */
internal class KeystorePinPepper @Inject constructor() : PinPepper {
    private val keyStore: KeyStore by lazy { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } }

    override fun createKey() {
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE).apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN).build())
        }.generateKey()
    }

    override fun apply(input: ByteArray): ByteArray? {
        val key = keyStore.getKey(ALIAS, null) as? SecretKey ?: return null
        return Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256).apply { init(key) }.doFinal(input)
    }

    override fun deleteKey() {
        if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "vccfinance_pin_pepper"
    }
}
