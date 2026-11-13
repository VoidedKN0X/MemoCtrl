package com.vknxworks.memoctrl.data.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts saved access tokens with a non-exportable AES key held in the Android Keystore. */
object KeystoreCrypto {
    private const val ALIAS = "memoctrl_credentials"
    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    @Volatile private var cached: SecretKey? = null

    @Synchronized
    private fun key(): SecretKey {
        cached?.let { return it }
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        val key = (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            // New keys go into the phone's secure element (StrongBox) when it has one, else the normal Keystore.
            // An existing key keeps working as it is, so nobody has to sign in again.
            ?: runCatching { generate(strongBox = true) }.getOrElse { generate(strongBox = false) }
        cached = key
        return key
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .setIsStrongBoxBacked(strongBox)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String? = runCatching {
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = data.copyOfRange(0, 12)
        val ct = data.copyOfRange(12, data.size)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ct), Charsets.UTF_8)
    }.getOrNull()
}
