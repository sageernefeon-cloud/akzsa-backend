package com.nefeon.akzsa.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts user-supplied test credentials with an AES key protected by Android Keystore. */
class SecureTokenStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("akzsa_secure_storage", Context.MODE_PRIVATE)
    private val alias = "akzsa_bearer_token_v1"
    /** Decrypted token kept in memory so the Keystore is hit once, not on every request. */
    @Volatile private var cached: String? = null

    @Synchronized
    fun hasToken(): Boolean = prefs.contains(KEY_CIPHERTEXT)

    @Synchronized
    fun saveToken(token: String?) {
        val clean = token?.trim().orEmpty()
        if (clean.isEmpty()) {
            prefs.edit().remove(KEY_CIPHERTEXT).remove(KEY_IV).apply()
            cached = null
            return
        }
        require(clean.length <= 4096) { "Token is unexpectedly long." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
        cached = clean
    }

    /** Same as [readToken] but off the main thread (Keystore calls can take tens of ms). Use from coroutines. */
    suspend fun readTokenAsync(): String? = cached ?: withContext(Dispatchers.IO) { readToken() }

    @Synchronized
    fun readToken(): String? {
        cached?.let { return it }
        val encrypted = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        val ivText = prefs.getString(KEY_IV, null) ?: return clearAndNull()
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)),
            )
            String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8).also { cached = it }
        } catch (e: Exception) {
            if (isPermanent(e)) clearAndNull() else null   // a transient Keystore error must not delete the token
        }
    }

    /** Only errors that can never succeed on retry justify deleting the stored token. */
    internal fun isPermanent(e: Exception): Boolean =
        e is KeyPermanentlyInvalidatedException || e is UnrecoverableKeyException ||
            e is AEADBadTagException || e is IllegalArgumentException

    private fun clearAndNull(): String? {
        cached = null
        prefs.edit().remove(KEY_CIPHERTEXT).remove(KEY_IV).apply()
        return null
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_CIPHERTEXT = "token_ciphertext"
        private const val KEY_IV = "token_iv"
    }
}
