package pt.vcc.financeapp.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject

enum class BiometricAvailability { AVAILABLE, NONE_ENROLLED, UNAVAILABLE }

sealed interface BiometricResult {
    data object Success : BiometricResult

    /** O utilizador escolheu "Usar PIN" ou fechou o diálogo. */
    data object Cancelled : BiometricResult

    /** As biometrias do dispositivo mudaram desde a ativação; é preciso voltar a ativar com o PIN. */
    data object KeyInvalidated : BiometricResult

    data class Error(val message: String) : BiometricResult
}

data class BiometricPromptTexts(val title: String, val subtitle: String, val negativeButton: String)

/**
 * Desbloqueio biométrico (classe 3 / `BIOMETRIC_STRONG`) ligado a uma chave do Keystore que exige
 * autenticação biométrica em cada uso e é invalidada quando se registam novas biometrias. O
 * sucesso só conta depois de a chave ser usada com o `CryptoObject` autenticado.
 */
interface BiometricAuthenticator {
    fun availability(): BiometricAvailability

    /** Cria a chave e pede a biometria; [onResult] recebe `Success` se a ativação ficou válida. */
    fun enroll(activity: FragmentActivity, texts: BiometricPromptTexts, onResult: (BiometricResult) -> Unit)

    fun authenticate(activity: FragmentActivity, texts: BiometricPromptTexts, onResult: (BiometricResult) -> Unit)

    fun disable()
}

internal class AndroidBiometricAuthenticator @Inject constructor(
    @ApplicationContext private val context: Context,
) : BiometricAuthenticator {
    private val keyStore: KeyStore by lazy { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } }

    override fun availability(): BiometricAvailability =
        when (BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            else -> BiometricAvailability.UNAVAILABLE
        }

    override fun enroll(activity: FragmentActivity, texts: BiometricPromptTexts, onResult: (BiometricResult) -> Unit) {
        val cipher = try {
            createKey()
            initCipher()
        } catch (e: GeneralSecurityException) {
            disable()
            onResult(BiometricResult.Error(e.message.orEmpty()))
            return
        }
        if (cipher == null) {
            onResult(BiometricResult.Error("Chave biométrica indisponível"))
            return
        }
        prompt(activity, texts, cipher) { result ->
            if (result != BiometricResult.Success) disable()
            onResult(result)
        }
    }

    override fun authenticate(activity: FragmentActivity, texts: BiometricPromptTexts, onResult: (BiometricResult) -> Unit) {
        val cipher = try {
            initCipher()
        } catch (_: KeyPermanentlyInvalidatedException) {
            null
        } catch (e: GeneralSecurityException) {
            onResult(BiometricResult.Error(e.message.orEmpty()))
            return
        }
        if (cipher == null) {
            disable()
            onResult(BiometricResult.KeyInvalidated)
            return
        }
        prompt(activity, texts, cipher, onResult)
    }

    override fun disable() {
        runCatching { if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS) }
    }

    private fun prompt(
        activity: FragmentActivity,
        texts: BiometricPromptTexts,
        cipher: Cipher,
        onResult: (BiometricResult) -> Unit,
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authenticated = result.cryptoObject?.cipher
                val usable = authenticated != null &&
                    runCatching { authenticated.doFinal(ByteArray(PROBE_BYTES)) }.isSuccess
                onResult(if (usable) BiometricResult.Success else BiometricResult.Error("CryptoObject inválido"))
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val cancelled = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                onResult(if (cancelled) BiometricResult.Cancelled else BiometricResult.Error(errString.toString()))
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(texts.title)
            .setSubtitle(texts.subtitle)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButtonText(texts.negativeButton)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            .authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun createKey() {
        disable()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(true)
                    .build(),
            )
        }.generateKey()
    }

    /** `null` se a chave não existir; lança [KeyPermanentlyInvalidatedException] se foi invalidada. */
    private fun initCipher(): Cipher? {
        val key = keyStore.getKey(ALIAS, null) as? SecretKey ?: return null
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "vccfinance_biometric_unlock"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PROBE_BYTES = 16
    }
}
