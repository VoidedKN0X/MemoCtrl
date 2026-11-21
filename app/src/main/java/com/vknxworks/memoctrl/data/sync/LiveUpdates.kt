package com.vknxworks.memoctrl.data.sync

import com.vknxworks.memoctrl.data.api.ApiException
import com.vknxworks.memoctrl.data.auth.ActiveSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Live updates: while the app is open (and unlocked), it stays connected to the Memos server's event
 * stream (Memos 0.27 and newer) and syncs as soon as the server says a memo, comment or reaction
 * changed, instead of waiting for the next sync. Nothing runs in the background: the connection is
 * closed when the app leaves the screen, and there is none for a local-only account or Memos 0.21.
 *
 * The events only say "something changed"; what changed is read by a normal sync.
 */
class LiveUpdates(
    scope: CoroutineScope,
    session: Flow<ActiveSession?>,
    enabled: Flow<Boolean>,
    private val sync: () -> Unit,
) {
    private val foreground = MutableStateFlow(false)

    private val _changes = MutableStateFlow(0L)
    /** Goes up by one for every event from the server. Screens that show server-only things reload on it. */
    val changes: StateFlow<Long> = _changes.asStateFlow()

    private val _connected = MutableStateFlow(false)
    /** Whether the event stream is open right now. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun onForeground() { foreground.value = true }
    fun onBackground() { foreground.value = false }

    init {
        scope.launch {
            combine(session, enabled, foreground) { s, on, front -> if (on && front && s != null && s.remote.supportsSocial) s else null }
                .distinctUntilChanged { a, b -> a?.account?.key == b?.account?.key && a?.remote === b?.remote }
                .collectLatest { s ->
                    _connected.value = false
                    if (s != null) listen(s)
                }
        }
    }

    private suspend fun listen(s: ActiveSession) {
        val pending = Channel<Unit>(Channel.CONFLATED)
        kotlinx.coroutines.coroutineScope {
            // Several events in a row (a memo with attachments, your own change coming back) become one sync.
            val syncer = launch {
                for (event in pending) {
                    delay(SETTLE_MILLIS)
                    sync()
                }
            }
            var wait = FIRST_RETRY_MILLIS
            try {
                while (true) {
                    try {
                        s.remote.watchChanges(
                            onConnected = {
                                _connected.value = true
                                wait = FIRST_RETRY_MILLIS
                            },
                            onChange = {
                                _changes.update { it + 1 }
                                pending.trySend(Unit)
                            },
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: ApiException) {
                        // No event stream on this server (or behind this proxy): don't keep asking.
                        if (e.status == 404 || e.status == 405 || e.status == 501 || e.status == 0) break
                    } catch (_: Throwable) {
                        // Offline, a timeout, the server restarting: try again below.
                    }
                    _connected.value = false
                    delay(wait)
                    wait = (wait * 2).coerceAtMost(MAX_RETRY_MILLIS)
                }
            } finally {
                _connected.value = false
                syncer.cancel()
            }
        }
    }

    private companion object {
        const val SETTLE_MILLIS = 1_500L
        const val FIRST_RETRY_MILLIS = 10_000L
        const val MAX_RETRY_MILLIS = 5 * 60_000L
    }
}
