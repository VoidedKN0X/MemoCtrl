package com.vknxworks.memoctrl.data.net

import android.app.Activity
import android.content.Context
import android.security.KeyChain
import okhttp3.ConnectionPool
import java.net.Socket
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/**
 * Optional client certificate (mutual TLS) for servers behind a proxy that asks for one. The
 * certificate and its private key stay in Android's own certificate store; the app only remembers
 * which one you picked, and the system asks you to allow it.
 */
object ClientCertificate {
    private const val PREFS = "client_certificate"
    private const val KEY_ALIAS = "alias"

    @Volatile
    private var pool: ConnectionPool? = null
    @Volatile
    private var keyManager: KeyChainKeyManager? = null

    fun selectedAlias(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ALIAS, null)

    /** Opens the system picker for a client certificate. */
    fun choose(activity: Activity, onDone: (Boolean) -> Unit) {
        KeyChain.choosePrivateKeyAlias(activity, { alias ->
            if (alias == null) {
                activity.runOnUiThread { onDone(false) }
                return@choosePrivateKeyAlias
            }
            activity.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ALIAS, alias).apply()
            keyManager?.clear()
            // Connections made with the old certificate (or none) must not be reused.
            pool?.evictAll()
            activity.runOnUiThread { onDone(true) }
        }, null, null, null, -1, selectedAlias(activity))
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ALIAS).apply()
        keyManager?.clear()
        Thread { pool?.evictAll() }.start()
    }

    /** TLS set-up for the app's HTTP client: the normal trusted CAs, plus the picked client certificate. */
    fun sslContext(context: Context, connectionPool: ConnectionPool): Pair<SSLContext, X509TrustManager> {
        pool = connectionPool
        val km = KeyChainKeyManager(context.applicationContext).also { keyManager = it }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        val tm = tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
        val ssl = SSLContext.getInstance("TLS")
        ssl.init(arrayOf(km), arrayOf(tm), null)
        return ssl to tm
    }

    private class KeyChainKeyManager(private val context: Context) : X509ExtendedKeyManager() {
        private val lock = Any()
        private var cachedAlias: String? = null
        private var cachedKey: PrivateKey? = null
        private var cachedChain: Array<X509Certificate>? = null

        fun clear() = synchronized(lock) {
            cachedAlias = null
            cachedKey = null
            cachedChain = null
        }

        private fun selected(): String? = ClientCertificate.selectedAlias(context)

        override fun chooseClientAlias(keyType: Array<String>?, issuers: Array<Principal>?, socket: Socket?): String? = selected()

        override fun chooseEngineClientAlias(keyType: Array<String>?, issuers: Array<Principal>?, engine: SSLEngine?): String? = selected()

        override fun getClientAliases(keyType: String?, issuers: Array<Principal>?): Array<String>? = selected()?.let { arrayOf(it) }

        override fun getPrivateKey(alias: String?): PrivateKey? {
            if (alias == null || alias != selected()) return null
            synchronized(lock) { if (cachedAlias == alias && cachedKey != null) return cachedKey }
            val key = runCatching { KeyChain.getPrivateKey(context, alias) }.getOrNull()
            synchronized(lock) {
                if (cachedAlias != alias) { cachedAlias = alias; cachedChain = null }
                cachedKey = key
            }
            return key
        }

        override fun getCertificateChain(alias: String?): Array<X509Certificate>? {
            if (alias == null || alias != selected()) return null
            synchronized(lock) { if (cachedAlias == alias && cachedChain != null) return cachedChain }
            val chain = runCatching { KeyChain.getCertificateChain(context, alias) }.getOrNull()
            synchronized(lock) {
                if (cachedAlias != alias) { cachedAlias = alias; cachedKey = null }
                cachedChain = chain
            }
            return chain
        }

        override fun chooseServerAlias(keyType: String?, issuers: Array<Principal>?, socket: Socket?): String? = null
        override fun chooseEngineServerAlias(keyType: String?, issuers: Array<Principal>?, engine: SSLEngine?): String? = null
        override fun getServerAliases(keyType: String?, issuers: Array<Principal>?): Array<String>? = null
    }
}
