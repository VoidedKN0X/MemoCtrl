package com.vknxworks.memoctrl.ui.nav

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import kotlin.reflect.KClass

/** One place that knows where things open. */
class Navigator(val nav: NavHostController) {

    /** True when the screen on top is fully shown (not still animating in or already on its way out). */
    private fun settled(): Boolean =
        nav.currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false

    /** Opens a screen from a tap, ignoring taps on a screen that's already leaving or not shown yet. */
    fun navigate(route: Any) {
        if (!settled()) return
        nav.navigate(route) { launchSingleTop = true }
    }

    /** Opens a screen no matter what is animating: for things another app or a widget asked for. */
    fun navigateNow(route: Any) {
        nav.navigate(route) { launchSingleTop = true }
    }

    /**
     * Back from the screen the user is looking at. Repeat taps while a screen change is still
     * animating are ignored, and the first screen is never popped (that emptied the screen stack
     * and left a black screen until the app was restarted).
     */
    fun back() {
        val current = nav.currentBackStackEntry ?: return
        if (nav.previousBackStackEntry == null) return
        if (!current.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        nav.popBackStack()
    }

    /** Closes [route] if it's still on top, for screens that finish on their own (not from a tap). */
    fun close(route: KClass<out Any>) {
        val dest = nav.currentBackStackEntry?.destination ?: return
        if (nav.previousBackStackEntry != null && dest.hasRoute(route)) nav.popBackStack()
    }

    fun memo(id: String) = navigate(MemoRoute(id))
    fun tag(tag: String) = navigate(TagRoute(tag))
    fun edit(id: String) = navigate(EditorRoute(id))
    fun compose() = navigate(EditorRoute())
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator") }
