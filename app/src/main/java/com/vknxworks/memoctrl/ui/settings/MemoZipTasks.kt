package com.vknxworks.memoctrl.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.vknxworks.memoctrl.R
import com.vknxworks.memoctrl.data.files.MemoZip
import com.vknxworks.memoctrl.data.sync.SyncingRepository
import com.vknxworks.memoctrl.ui.components.friendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The memo ZIP export and import, run in the app's own scope instead of the Settings screen's. Leaving
 * Settings, or the app lock covering it, used to cancel them halfway: an export left a cut-off ZIP file
 * without a word, an import stopped after some memos without a summary. Now they run to the end (or until
 * Cancel is tapped) and the Settings group only shows what is going on: [progress], the import question
 * ([plan]) and the outcome ([message]), which is still there when you come back to Settings.
 */
class MemoZipTasks(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext

    /** What a running job has done: [done] of [total] memos ([total] 0 while still reading the file). */
    data class Progress(val export: Boolean, val done: Int, val total: Int)

    /** A ZIP that was read, waiting for the "Import?" answer, for the account [accountKey]. */
    class PendingPlan(val plan: MemoZip.Plan, val accountKey: String)

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    private val _plan = MutableStateFlow<PendingPlan?>(null)
    val plan: StateFlow<PendingPlan?> = _plan.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val running = AtomicBoolean(false)

    @Volatile
    private var job: Job? = null

    fun cancel() {
        job?.cancel()
    }

    fun dismissMessage() {
        _message.value = null
    }

    /** "Cancel" on the import question, or the account changed: the unpacked files are deleted. */
    fun discardPlan() {
        _plan.value?.plan?.discard()
        _plan.value = null
    }

    private fun start(export: Boolean, block: suspend () -> Unit): Boolean {
        if (!running.compareAndSet(false, true)) return false
        _progress.value = Progress(export, 0, 0)
        job = scope.launch {
            try {
                block()
            } finally {
                job = null
                _progress.value = null
                running.set(false)
            }
        }
        return true
    }

    private fun onProgress(export: Boolean): (Int, Int) -> Unit = { done, total -> _progress.value = Progress(export, done, total) }

    /**
     * Writes every memo of [repo] to [uri]. If it fails or is cancelled, the half-written file is deleted
     * (or, if that isn't possible, the message says it's incomplete).
     */
    fun export(uri: Uri, repo: SyncingRepository, http: OkHttpClient?, server: String?, username: String, displayName: String) {
        start(export = true) {
            try {
                val result = withContext(Dispatchers.IO) {
                    val out = app.contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException(app.getString(R.string.error_write_file))
                    out.use {
                        MemoZip.export(
                            context = app,
                            repo = repo,
                            http = http,
                            server = server,
                            username = username,
                            displayName = displayName,
                            out = it,
                            onProgress = onProgress(export = true),
                        )
                    }
                }
                _message.value = buildString {
                    append(app.resources.getQuantityString(R.plurals.zip_exported, result.memos, result.memos, result.attachments))
                    if (result.missingAttachments > 0) {
                        append("\n\n")
                        append(app.resources.getQuantityString(R.plurals.zip_export_missing, result.missingAttachments, result.missingAttachments))
                    }
                }
            } catch (e: CancellationException) {
                val removed = removePartial(uri)
                _message.value = app.getString(if (removed) R.string.zip_export_cancelled else R.string.zip_export_partial_left)
                throw e
            } catch (e: Throwable) {
                val removed = removePartial(uri)
                _message.value = buildString {
                    append(app.getString(R.string.zip_export_failed, e.friendlyMessage()))
                    if (!removed) append("\n\n").append(app.getString(R.string.zip_export_partial_left))
                }
            }
        }
    }

    /** Deletes the unfinished ZIP at [uri]; true when it's gone. Runs even while the job is being cancelled. */
    private suspend fun removePartial(uri: Uri): Boolean = withContext(NonCancellable + Dispatchers.IO) {
        runCatching { DocumentsContract.deleteDocument(app.contentResolver, uri) }.getOrDefault(false)
    }

    /** Reads the ZIP at [uri] for [repo]'s account; the question then shows in Settings. */
    fun read(uri: Uri, repo: SyncingRepository) {
        start(export = false) {
            try {
                discardPlan()
                _plan.value = PendingPlan(MemoZip.read(app, uri, repo), repo.accountKey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: MemoZip.NotAMemoZip) {
                _message.value = app.getString(R.string.zip_not_valid)
            } catch (e: Throwable) {
                _message.value = app.getString(R.string.zip_import_failed, e.friendlyMessage())
            }
        }
    }

    /** "Import" on the question: creates the memos in [repo], which must be the account the ZIP was read for. */
    fun import(repo: SyncingRepository) {
        val pending = _plan.value ?: return
        if (pending.accountKey != repo.accountKey) {
            discardPlan()
            return
        }
        val total = pending.plan.memos.size
        // Written from the import's thread, read when it's cancelled.
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        start(export = false) {
            _plan.value = null
            try {
                val made = MemoZip.importPlan(pending.plan, repo) { d, t ->
                    done.set(d)
                    _progress.value = Progress(false, d, t)
                }
                _message.value = app.resources.getQuantityString(R.plurals.zip_imported, made, made)
            } catch (e: CancellationException) {
                _message.value = app.getString(R.string.zip_import_cancelled, done.get(), total)
                throw e
            } catch (e: Throwable) {
                _message.value = app.getString(R.string.zip_import_failed, e.friendlyMessage())
            }
        }
    }
}
