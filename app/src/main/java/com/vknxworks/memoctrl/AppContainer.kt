package com.vknxworks.memoctrl

import android.content.Context
import android.net.Uri
import com.vknxworks.memoctrl.data.auth.SessionManager
import com.vknxworks.memoctrl.data.auth.SyncCheck
import com.vknxworks.memoctrl.data.db.MemoDatabase
import com.vknxworks.memoctrl.data.files.FileStorage
import com.vknxworks.memoctrl.data.net.CleartextPolicy
import com.vknxworks.memoctrl.data.net.ClientCertificate
import com.vknxworks.memoctrl.data.net.RedirectGuard
import com.vknxworks.memoctrl.data.settings.SettingsRepository
import com.vknxworks.memoctrl.data.sync.SyncingRepository
import com.vknxworks.memoctrl.widget.Widgets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Something another app, a widget or a shortcut asked the app to open. */
sealed interface PendingAction {
    data object NewMemo : PendingAction
    data class Share(val text: String, val images: List<Uri>) : PendingAction
    data class ViewMemo(val identifier: String) : PendingAction
    data class EditMemo(val identifier: String) : PendingAction
}

/** Manual dependency container; one per process. */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val connectionPool = ConnectionPool()

    val http: OkHttpClient = run {
        val (ssl, trust) = ClientCertificate.sslContext(context, connectionPool)
        OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .sslSocketFactory(ssl.socketFactory, trust)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                var request = chain.request().newBuilder()
                    .header("User-Agent", "MemoCtrl/${BuildConfig.VERSION_NAME} (Android)")
                    .build()
                // Pictures and attachments of private memos need the token too. It is only ever added
                // for the signed-in server's own address.
                val session = sessions.active.value
                if (session != null && request.header("Authorization") == null && sameOrigin(request.url, session.account.host)) {
                    request = request.newBuilder().header("Authorization", "Bearer ${session.token}").build()
                }
                chain.proceed(request)
            }
            // Plain HTTP only to the user's own server (after they chose it), and never via a redirect
            // from an HTTPS request.
            .addNetworkInterceptor(CleartextPolicy.interceptor)
            // The token, cookies and request bodies never follow a redirect to another server.
            .addNetworkInterceptor(RedirectGuard.interceptor)
            .build()
    }

    val settings = SettingsRepository(context, appScope)
    val db = MemoDatabase(context)
    val files = FileStorage(context)
    val sessions = SessionManager(context, http, db, files, settings, appScope)

    /** Memos of the signed-in account. Screens are only shown while signed in. */
    val repo: SyncingRepository
        get() = sessions.active.value?.repo ?: error("Not signed in")

    /** Set by MainActivity for share sheets, shortcuts and widgets; the main screen consumes it. */
    val pendingAction = MutableStateFlow<PendingAction?>(null)

    init {
        // Widgets show what's on the phone, so they follow every change.
        appScope.launch {
            db.changes.drop(1).debounce(1500).collect { Widgets.updateAll(context) }
        }
        // ... and hide memos when the app lock goes on, or change account.
        appScope.launch {
            settings.stored.map { it.appLock }.distinctUntilChanged().drop(1).collect { Widgets.updateAll(context) }
        }
        appScope.launch {
            sessions.active.map { it?.account?.key }.distinctUntilChanged().drop(1).collect { Widgets.updateAll(context) }
        }
    }

    /**
     * A quiet sync after start-up or coming back to the app. It skips servers whose version needs
     * confirming, and keeps errors in the sync status instead of showing a dialog.
     */
    fun syncInBackground() {
        val session = sessions.active.value ?: return
        appScope.launch {
            try {
                if (sessions.checkSync(automatic = true) is SyncCheck.Allowed) session.repo.sync()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Shown by the sync badge.
            }
        }
    }

    private fun sameOrigin(url: HttpUrl, host: String): Boolean {
        val base = host.toHttpUrlOrNull() ?: return false
        return url.scheme == base.scheme && url.host.equals(base.host, ignoreCase = true) && url.port == base.port
    }
}

val Context.container: AppContainer
    get() = (applicationContext as MemoCtrlApp).container
