package com.vknxworks.memoctrl

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.vknxworks.memoctrl.ui.lock.AppLock

class MemoCtrlApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AppLock.onLocked = { container.onLocked() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                val s = container.settings.settings.value
                AppLock.onForeground(s.appLock || container.vault.enabled, s.lockTimeout.millis)
                container.syncInBackground()
            }

            override fun onStop(owner: LifecycleOwner) {
                val s = container.settings.settings.value
                AppLock.onBackground(s.appLock || container.vault.enabled, s.lockTimeout.millis)
            }
        })
    }

    /**
     * Pictures go through the app's own HTTP client, so the same rules apply as everywhere else:
     * plain HTTP only to a server the user chose, the token only to that server, and nothing
     * forwarded on redirects to other servers.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.http })) }
            .build()
}
