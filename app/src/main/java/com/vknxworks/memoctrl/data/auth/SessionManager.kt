package com.vknxworks.memoctrl.data.auth

import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vknxworks.memoctrl.data.api.ApiException
import com.vknxworks.memoctrl.data.api.ApiJson
import com.vknxworks.memoctrl.data.api.MemosRemote
import com.vknxworks.memoctrl.data.api.MemosV0
import com.vknxworks.memoctrl.data.api.MemosV1
import com.vknxworks.memoctrl.data.appDataStore
import com.vknxworks.memoctrl.data.db.MemoDatabase
import com.vknxworks.memoctrl.data.files.FileStorage
import com.vknxworks.memoctrl.data.lock.LockVault
import com.vknxworks.memoctrl.data.model.MemoVisibility
import com.vknxworks.memoctrl.data.model.User
import com.vknxworks.memoctrl.data.net.CleartextPolicy
import com.vknxworks.memoctrl.data.settings.SettingsRepository
import com.vknxworks.memoctrl.data.sync.SyncingRepository
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.io.IOException
import java.time.Instant

/** Which generation of the Memos API a server speaks. */
@Serializable
enum class ServerApi { V0, V1 }

@Serializable
data class Account(
    /** Server address without a trailing slash, e.g. "https://memos.example.com". */
    val host: String,
    val api: ServerApi,
    /** "users/1" on newer servers, "1" on Memos 0.21. */
    val remoteIdentifier: String,
    val username: String,
    val displayName: String = "",
    /** Optional name you gave the account when signing in. */
    val label: String = "",
    /** Access token, encrypted with the Keystore. */
    val tokenEnc: String,
    val startDateEpochSecond: Long = 0,
    val defaultVisibility: MemoVisibility = MemoVisibility.PRIVATE,
    val avatarUrl: String? = null,
    val serverVersion: String? = null,
    /** Server versions newer than the app knows that you chose to sync with anyway. */
    val acceptedVersions: List<String> = emptyList(),
    /** Set when the app made the token itself (password sign-in), so signing out can revoke it. */
    val tokenId: String? = null,
) {
    val key: String get() = "memos:$host:$remoteIdentifier"
    val serverName: String get() = Uri.parse(host).host ?: host
    val isEncrypted: Boolean get() = host.startsWith("https://", ignoreCase = true)
    val title: String get() = label.trim().ifEmpty { displayName.trim().ifEmpty { username.trim().ifEmpty { serverName } } }

    fun toUser() = User(
        identifier = remoteIdentifier,
        name = displayName.ifBlank { username },
        startDate = if (startDateEpochSecond > 0) Instant.ofEpochSecond(startDateEpochSecond) else Instant.now(),
        defaultVisibility = defaultVisibility,
        avatarUrl = avatarUrl,
    )
}

/** How the user signs in: a username and password (the app then makes its own token), or a token. */
sealed interface Credentials {
    data class Password(val username: String, val password: String) : Credentials
    data class Token(val token: String) : Credentials
}

/** The signed-in account, with its token (in memory only) and its memos. */
data class ActiveSession(val account: Account, val token: String, val repo: SyncingRepository) {
    val remote: MemosRemote get() = repo.remote
}

/**
 * Thrown when HTTPS couldn't be reached for an address typed without a scheme. The UI asks the user
 * whether to try plain HTTP (which sends the token unencrypted) and signs in again with [httpUrl].
 */
class HttpFallbackNeeded(val httpUrl: String, cause: Throwable) :
    Exception("Couldn't connect securely (HTTPS). The server may only support plain HTTP.", cause)

/** The server runs a Memos version newer than the app was made for. The user can continue anyway. */
class NewerServerException(val version: String) : Exception(
    "This server runs Memos $version, newer than MemoCtrl was made for (${VersionPolicy.SUPPORTED_TEXT}). " +
        "Most things should work, but a newer version can change how memos are stored.",
)

object VersionPolicy {
    const val SUPPORTED_TEXT = "Memos 0.21 and Memos 0.27 to 0.31"
    private val V0_MIN = listOf(0, 21, 0)
    private val V1_MIN = listOf(0, 27, 0)
    private val V1_MAX = listOf(0, 31, 0)

    enum class Verdict { SUPPORTED, TOO_OLD, NEWER }

    /** "v0.31.0" or "26.10-rc.1" to [0, 31, 0] / [26, 10, 0]; null when it isn't a version. */
    fun parse(version: String): List<Int>? {
        val parts = version.trim().removePrefix("v").split('.', '-', '+')
        val nums = parts.take(3).map { it.toIntOrNull() ?: return null }
        if (nums.isEmpty()) return null
        return nums + List(3 - nums.size) { 0 }
    }

    private fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until 3) if (a[i] != b[i]) return a[i].compareTo(b[i])
        return 0
    }

    fun verdict(api: ServerApi, version: String): Verdict {
        if (version.isBlank()) return Verdict.TOO_OLD
        val v = parse(version)
        return when (api) {
            ServerApi.V0 -> if (v != null && compare(v, V0_MIN) < 0) Verdict.TOO_OLD else Verdict.SUPPORTED
            ServerApi.V1 -> when {
                v == null -> Verdict.NEWER
                compare(v, V1_MIN) < 0 -> Verdict.TOO_OLD
                compare(v, V1_MAX) > 0 -> Verdict.NEWER
                else -> Verdict.SUPPORTED
            }
        }
    }
}

/** What the version check before a sync decided. */
sealed interface SyncCheck {
    data object Allowed : SyncCheck
    /** No sync. [message] is null for automatic syncs, which stay quiet. */
    data class Blocked(val message: String?) : SyncCheck
    data class NeedsConfirmation(val version: String, val message: String) : SyncCheck
}

/** Saved Memos accounts and the active one. Tokens are stored encrypted and never leave the phone except to their server. */
class SessionManager(
    private val context: Context,
    private val http: OkHttpClient,
    private val db: MemoDatabase,
    private val files: FileStorage,
    private val settings: SettingsRepository,
    private val vault: LockVault,
    private val scope: CoroutineScope,
) {
    private val accountsKey = stringPreferencesKey("accounts")
    private val activeKey = stringPreferencesKey("active_account")
    private val mutex = Mutex()

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _active = MutableStateFlow<ActiveSession?>(null)
    val active: StateFlow<ActiveSession?> = _active.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        scope.launch { load() }
    }

    private suspend fun load() = mutex.withLock {
        val prefs = context.appDataStore.data.first()
        val list = prefs[accountsKey]?.let {
            runCatching { ApiJson.decodeFromString(ListSerializer(Account.serializer()), it) }.getOrNull()
        }.orEmpty()
        _accounts.value = list
        // With the app lock on, the login can only be decrypted after unlocking; onUnlocked() continues.
        if (vault.enabled && !vault.isOpen) return@withLock
        activateSaved()
        _ready.value = true
    }

    private suspend fun activateSaved() {
        val activeKeyValue = context.appDataStore.data.first()[activeKey]
        val list = _accounts.value
        val acc = list.firstOrNull { it.key == activeKeyValue } ?: list.firstOrNull()
        if (acc != null) activate(acc) else _active.value = null
    }

    /** The token of [acc], or null while it's sealed by the app lock and the app is locked. */
    private fun tokenOf(acc: Account): String? {
        val plain = KeystoreCrypto.decrypt(acc.tokenEnc) ?: return null
        return if (LockVault.isSealed(plain)) vault.open(plain) else plain
    }

    /** Encrypts a token for saving: with the Keystore, and also with the app lock's key when it's on. */
    private fun storeToken(token: String): String {
        val inner = if (vault.enabled) vault.seal(token) ?: throw ApiException("Unlock MemoCtrl first.") else token
        return KeystoreCrypto.encrypt(inner)
    }

    /** The app locked: forget the session and its token until the next unlock. */
    fun onLocked() {
        if (!vault.enabled) return
        _ready.value = false
        _active.value?.repo?.close()
        _active.value = null
    }

    /** The app unlocked: decrypt the login and open the session again. */
    fun onUnlocked() {
        scope.launch {
            mutex.withLock {
                if (_active.value == null) activateSaved()
                _ready.value = true
            }
        }
    }

    /** The app lock was turned on: encrypt every saved login with its key too. */
    suspend fun sealTokens() = mutex.withLock {
        val list = _accounts.value.map { acc ->
            val token = tokenOf(acc) ?: return@map acc
            acc.copy(tokenEnc = storeToken(token))
        }
        persist(list, context.appDataStore.data.first()[activeKey])
        _active.value?.let { s -> list.firstOrNull { it.key == s.account.key }?.let { _active.value = s.copy(account = it) } }
    }

    /** The app lock is being turned off (while unlocked): go back to Keystore-only encryption. */
    suspend fun unsealTokens() = mutex.withLock {
        val list = _accounts.value.map { acc ->
            val token = tokenOf(acc) ?: return@map acc
            acc.copy(tokenEnc = KeystoreCrypto.encrypt(token))
        }
        persist(list, context.appDataStore.data.first()[activeKey])
        _active.value?.let { s -> list.firstOrNull { it.key == s.account.key }?.let { _active.value = s.copy(account = it) } }
    }

    /** "Forgot PIN?": deletes every account and what's kept for it on the phone. Tokens can't be revoked. */
    suspend fun forgetAllAccounts() = mutex.withLock {
        _active.value?.repo?.close()
        _active.value = null
        val keys = _accounts.value.map { it.key }
        persist(emptyList(), null)
        for (key in keys) {
            db.deleteAccount(key)
            files.deleteAccountFiles(key)
            settings.clearDraft(key)
        }
        _ready.value = true
    }

    private fun clientFor(api: ServerApi, host: String, token: String?, userName: String = ""): MemosRemote = when (api) {
        ServerApi.V0 -> MemosV0(host, http, token)
        ServerApi.V1 -> MemosV1(host, http, token, userName)
    }

    /** Finds out which Memos API and version the server at [host] runs. */
    private suspend fun detect(host: String): Pair<ServerApi, String> {
        // Memos 0.21 answers on api/v1/status; newer servers don't have it.
        val v0 = try {
            MemosV0(host, http, null).serverVersion()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            ""
        } catch (e: kotlinx.serialization.SerializationException) {
            ""
        }
        if (v0.isNotEmpty()) return ServerApi.V0 to v0
        val v1 = MemosV1(host, http, null).serverVersion()
        if (v1.isNotEmpty()) return ServerApi.V1 to v1
        throw ApiException("This doesn't look like a Memos server.")
    }

    private fun normalize(address: String): Pair<String, Boolean> {
        val raw = address.trim().trimEnd('/')
        val explicit = raw.startsWith("http://", true) || raw.startsWith("https://", true)
        val url = if (explicit) raw else "https://$raw"
        val parsed = url.toHttpUrlOrNull() ?: throw ApiException("That isn't a valid server address.")
        return parsed.toString().trimEnd('/') to explicit
    }

    /**
     * Checks the server and the access token, then saves and switches to the account.
     * An address without a scheme is tried over HTTPS only. If that fails with a connection error
     * (not a TLS/certificate error), [HttpFallbackNeeded] is thrown so the UI can ask before using
     * plain HTTP. An explicit http:// address is used as typed (the sign-in screen warns about it).
     * A server newer than the app knows throws [NewerServerException] unless [allowNewer].
     * With a password, the server makes a never-expiring token for the app; the password itself is
     * sent once and never kept.
     */
    suspend fun signIn(address: String, credentials: Credentials, label: String, allowNewer: Boolean): Account {
        val (host, explicit) = normalize(address)
        // Only reached for an http:// address the user typed or confirmed in the "unencrypted" dialog.
        CleartextPolicy.allowServer(host)
        val (api, version) = try {
            detect(host)
        } catch (e: ApiException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.net.UnknownHostException) {
            throw e
        } catch (e: IOException) {
            // TLS and certificate failures never lead to HTTP: the server (or someone in between)
            // answered on the HTTPS port. Plain connection failures may offer HTTP, but only behind
            // the explicit "unencrypted" confirmation.
            if (explicit) throw e
            if (e.isTlsProblem()) {
                throw ApiException(
                    "Secure connection failed: ${e.message ?: "TLS error"}. " +
                        "If your server only supports plain HTTP, type http:// in front of the address.",
                )
            }
            throw HttpFallbackNeeded("http://" + host.removePrefix("https://"), e)
        }

        when (VersionPolicy.verdict(api, version)) {
            VersionPolicy.Verdict.TOO_OLD -> throw ApiException(
                "This server runs Memos ${version.ifBlank { "(unknown)" }}. MemoCtrl works with ${VersionPolicy.SUPPORTED_TEXT}.",
            )
            VersionPolicy.Verdict.NEWER -> if (!allowNewer) throw NewerServerException(version)
            VersionPolicy.Verdict.SUPPORTED -> Unit
        }

        var tokenId: String? = null
        val cleanToken = when (credentials) {
            is Credentials.Token -> credentials.token.trim()
            is Credentials.Password -> try {
                val issued = clientFor(api, host, null).createTokenWithPassword(
                    credentials.username.trim(),
                    credentials.password,
                    "MemoCtrl on ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim(),
                )
                tokenId = issued.id
                issued.token
            } catch (e: ApiException) {
                if (e.status == 401 || e.status == 403) {
                    throw ApiException(e.message?.takeIf { it.isNotBlank() && !it.startsWith("Server answered") } ?: "Wrong username or password.", e.status)
                }
                throw e
            }
        }
        val account = try {
            when (api) {
                ServerApi.V1 -> {
                    val client = MemosV1(host, http, cleanToken)
                    val me = client.me()
                    val user = MemosV1(host, http, cleanToken, me.name).currentUser()
                    Account(
                        host = host,
                        api = api,
                        remoteIdentifier = me.name,
                        username = me.username,
                        displayName = user.name,
                        label = label.trim(),
                        tokenEnc = storeToken(cleanToken),
                        startDateEpochSecond = me.createTime?.epochSecond ?: 0,
                        defaultVisibility = user.defaultVisibility,
                        avatarUrl = me.avatarUrl,
                        serverVersion = version,
                        acceptedVersions = if (allowNewer) listOf(version) else emptyList(),
                        tokenId = tokenId,
                    )
                }
                ServerApi.V0 -> {
                    val me = MemosV0(host, http, cleanToken).me()
                    Account(
                        host = host,
                        api = api,
                        remoteIdentifier = me.id.toString(),
                        username = me.username ?: me.name.orEmpty(),
                        displayName = me.nickname ?: me.name.orEmpty(),
                        label = label.trim(),
                        tokenEnc = storeToken(cleanToken),
                        startDateEpochSecond = me.createdTs,
                        defaultVisibility = me.defaultVisibility,
                        avatarUrl = me.avatarUrl,
                        serverVersion = version,
                        tokenId = tokenId,
                    )
                }
            }
        } catch (e: ApiException) {
            if (e.isUnauthorized) throw ApiException("The server didn't accept this access token.", e.status)
            throw e
        }

        mutex.withLock {
            val existing = _accounts.value.firstOrNull { it.key == account.key }
            val merged = account.copy(acceptedVersions = (existing?.acceptedVersions.orEmpty() + account.acceptedVersions).distinct())
            persist(_accounts.value.filterNot { it.key == account.key } + merged, merged.key)
            activate(merged, cleanToken)
        }
        return account
    }

    private fun Throwable.isTlsProblem(): Boolean {
        var t: Throwable? = this
        while (t != null) {
            if (t is javax.net.ssl.SSLException ||
                t is java.security.cert.CertificateException ||
                t is java.security.cert.CertPathValidatorException
            ) return true
            t = t.cause
        }
        return false
    }

    suspend fun switchTo(key: String) = mutex.withLock {
        val acc = _accounts.value.firstOrNull { it.key == key } ?: return@withLock
        context.appDataStore.edit { it[activeKey] = key }
        activate(acc)
    }

    /**
     * Signs out of [key] and deletes everything kept on the phone for it: memos, attachments, draft.
     * A token the app made at password sign-in is revoked on the server too (when it can be reached).
     */
    suspend fun signOut(key: String) = mutex.withLock {
        _accounts.value.firstOrNull { it.key == key }?.let { acc ->
            val id = acc.tokenId ?: return@let
            val token = tokenOf(acc) ?: return@let
            kotlinx.coroutines.withTimeoutOrNull(5_000) {
                runCatching { clientFor(acc.api, acc.host, token, acc.remoteIdentifier).revokeToken(id) }
            }
        }
        val remaining = _accounts.value.filterNot { it.key == key }
        val wasActive = _active.value?.account?.key == key
        if (wasActive) _active.value?.repo?.close()
        val nextActive = if (wasActive) remaining.firstOrNull() else _active.value?.account
        persist(remaining, nextActive?.key)
        if (wasActive) {
            if (nextActive != null) activate(nextActive) else _active.value = null
        }
        db.deleteAccount(key)
        files.deleteAccountFiles(key)
        settings.clearDraft(key)
    }

    /** Remembers that the user chose to sync with a server version newer than the app knows. */
    suspend fun acceptVersion(version: String) = mutex.withLock {
        val acc = _active.value?.account ?: return@withLock
        val updated = acc.copy(acceptedVersions = (acc.acceptedVersions + version).distinct())
        replaceAccount(updated)
    }

    /**
     * Whether syncing is safe with the server's current version. Automatic syncs never ask: they
     * quietly skip a server that needs confirmation and a manual sync asks instead.
     */
    suspend fun checkSync(automatic: Boolean, allowVersion: String? = null): SyncCheck {
        val session = _active.value ?: return SyncCheck.Blocked(null)
        val version = try {
            session.remote.serverVersion()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return if (automatic) SyncCheck.Blocked(null) else SyncCheck.Allowed // the sync itself reports the error
        }
        if (version != session.account.serverVersion) {
            mutex.withLock { _active.value?.account?.let { replaceAccount(it.copy(serverVersion = version)) } }
        }
        return when (VersionPolicy.verdict(session.account.api, version)) {
            VersionPolicy.Verdict.SUPPORTED -> SyncCheck.Allowed
            VersionPolicy.Verdict.TOO_OLD -> SyncCheck.Blocked(
                if (automatic) null else "This server runs Memos $version. MemoCtrl works with ${VersionPolicy.SUPPORTED_TEXT}.",
            )
            VersionPolicy.Verdict.NEWER -> when {
                version in session.account.acceptedVersions || version == allowVersion -> SyncCheck.Allowed
                automatic -> SyncCheck.Blocked(null)
                else -> SyncCheck.NeedsConfirmation(
                    version,
                    "Your server runs Memos $version, newer than MemoCtrl was made for (${VersionPolicy.SUPPORTED_TEXT}). " +
                        "Syncing anyway could mix up changes that haven't synced yet. Check the release notes first.",
                )
            }
        }
    }

    /** Keeps the saved account's name, default visibility and picture in step with the server. */
    private suspend fun onUserSynced(key: String, user: User) = mutex.withLock {
        val acc = _accounts.value.firstOrNull { it.key == key } ?: return@withLock
        val updated = acc.copy(
            displayName = user.name,
            defaultVisibility = user.defaultVisibility,
            avatarUrl = user.avatarUrl,
            startDateEpochSecond = user.startDate.epochSecond,
        )
        if (updated != acc) replaceAccount(updated)
    }

    private suspend fun replaceAccount(updated: Account) {
        val list = _accounts.value.map { if (it.key == updated.key) updated else it }
        persist(list, _active.value?.account?.key)
        _active.value?.let { s -> if (s.account.key == updated.key) _active.value = s.copy(account = updated) }
    }

    private fun activate(acc: Account, knownToken: String? = null) {
        _active.value?.repo?.close()
        val token = knownToken ?: tokenOf(acc)
        if (token == null) {
            _active.value = null
            return
        }
        // Reconnects always use the saved address and scheme; never switch an https account to http.
        CleartextPolicy.allowServer(acc.host)
        val remote = clientFor(acc.api, acc.host, token, acc.remoteIdentifier)
        val repo = SyncingRepository(
            db = db,
            files = files,
            remote = remote,
            accountKey = acc.key,
            initialUser = acc.toUser(),
            onUserSynced = { user -> onUserSynced(acc.key, user) },
        )
        _active.value = ActiveSession(acc, token, repo)
    }

    private suspend fun persist(list: List<Account>, activeKeyValue: String?) {
        _accounts.value = list
        context.appDataStore.edit { prefs ->
            prefs[accountsKey] = ApiJson.encodeToString(ListSerializer(Account.serializer()), list)
            if (activeKeyValue != null) prefs[activeKey] = activeKeyValue else prefs.remove(activeKey)
        }
    }
}
