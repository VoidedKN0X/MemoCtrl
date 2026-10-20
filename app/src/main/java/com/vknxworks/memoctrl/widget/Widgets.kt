package com.vknxworks.memoctrl.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.view.View
import android.widget.RemoteViews
import com.vknxworks.memoctrl.MainActivity
import com.vknxworks.memoctrl.R
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.data.auth.SyncCheck
import com.vknxworks.memoctrl.data.model.MemoEntity
import com.vknxworks.memoctrl.data.settings.EditGesture
import com.vknxworks.memoctrl.util.hasTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Home screen widgets. They show what's kept on the phone, so they work offline and never wait for the server. */
object Widgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    const val ACTION_REFRESH = "com.vknxworks.memoctrl.widget.REFRESH"

    /** Per-widget choices made in the widget's settings. */
    data class Config(val tag: String? = null, val pinnedOnly: Boolean = false, val maxItems: Int = 10)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun config(context: Context, id: Int): Config {
        val p = prefs(context)
        return Config(
            tag = p.getString("tag_$id", null)?.takeIf { it.isNotBlank() },
            pinnedOnly = p.getBoolean("pinned_$id", false),
            maxItems = p.getInt("max_$id", 10),
        )
    }

    fun saveConfig(context: Context, id: Int, config: Config) {
        prefs(context).edit()
            .putString("tag_$id", config.tag)
            .putBoolean("pinned_$id", config.pinnedOnly)
            .putInt("max_$id", config.maxItems)
            .apply()
    }

    fun forget(context: Context, ids: IntArray) {
        val e = prefs(context).edit()
        ids.forEach { e.remove("tag_$it").remove("pinned_$it").remove("max_$it") }
        e.apply()
    }

    /** Redraws every MemoCtrl widget on the home screen. */
    fun updateAll(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val manager = AppWidgetManager.getInstance(app)
            val memosIds = manager.getAppWidgetIds(ComponentName(app, MemosWidgetProvider::class.java))
            val memoryIds = manager.getAppWidgetIds(ComponentName(app, MemoryWidgetProvider::class.java))
            if (memosIds.isEmpty() && memoryIds.isEmpty()) return@launch
            memosIds.forEach { runCatching { manager.updateAppWidget(it, buildMemos(app, it)) } }
            memoryIds.forEach { runCatching { manager.updateAppWidget(it, buildMemory(app, it)) } }
        }
    }

    fun update(context: Context, ids: IntArray, memory: Boolean, onDone: () -> Unit = {}) {
        val app = context.applicationContext
        scope.launch {
            try {
                val manager = AppWidgetManager.getInstance(app)
                ids.forEach { id ->
                    runCatching { manager.updateAppWidget(id, if (memory) buildMemory(app, id) else buildMemos(app, id)) }
                }
            } finally {
                onDone()
            }
        }
    }

    /** The refresh button: sync with the server, then redraw. */
    fun refresh(context: Context, onDone: () -> Unit) {
        val app = context.applicationContext
        scope.launch {
            try {
                val c = app.container
                // With the app lock on, the widget doesn't sync: the login stays encrypted until you unlock.
                if (c.settings.stored.first().appLock) return@launch
                withTimeoutOrNull(5_000) { c.sessions.ready.first { it } }
                val session = c.sessions.active.value
                if (session != null) {
                    withTimeoutOrNull(8_000) {
                        runCatching { if (c.sessions.checkSync(automatic = true) is SyncCheck.Allowed) session.repo.sync() }
                    }
                }
            } finally {
                updateAll(app)
                onDone()
            }
        }
    }

    private data class Snapshot(val memos: List<MemoEntity>, val locked: Boolean, val signedIn: Boolean, val openInEditor: Boolean)

    private suspend fun snapshot(context: Context): Snapshot {
        val c = context.container
        val settings = c.settings.stored.first()
        // Locked: nothing to show, and the login can't be read anyway.
        if (settings.appLock) return Snapshot(emptyList(), true, c.sessions.accounts.value.isNotEmpty() || c.vault.enabled, false)
        withTimeoutOrNull(5_000) { c.sessions.ready.first { it } }
        val session = c.sessions.active.value ?: return Snapshot(emptyList(), settings.appLock, false, false)
        val memos = if (settings.appLock) emptyList() else runCatching { session.repo.listMemos() }.getOrDefault(emptyList())
        return Snapshot(memos, settings.appLock, true, settings.editGesture == EditGesture.SINGLE)
    }

    private fun openApp(context: Context, action: String? = null, requestCode: Int = 0): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action ?: Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun memoIntent(context: Context, id: String, edit: Boolean, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = if (edit) MainActivity.ACTION_EDIT_MEMO else MainActivity.ACTION_VIEW_MEMO
            putExtra(MainActivity.EXTRA_MEMO_ID, id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun relative(m: MemoEntity) =
        DateUtils.getRelativeTimeSpanString(m.date.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

    /** Memo text for a small widget line: Markdown markers and blank lines dropped. */
    private fun plain(content: String): String = content
        .lineSequence()
        .map { it.trim().removePrefix("- [ ] ").removePrefix("- [x] ").removePrefix("- ").removePrefix("* ").trimStart('#', '>', ' ') }
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .replace("**", "")
        .replace("~~", "")
        .replace("`", "")

    private suspend fun buildMemos(context: Context, id: Int): RemoteViews {
        val config = config(context, id)
        val snap = snapshot(context)
        val views = RemoteViews(context.packageName, R.layout.widget_memos)
        views.setOnClickPendingIntent(R.id.widget_header, openApp(context))
        views.setOnClickPendingIntent(R.id.widget_add, openApp(context, MainActivity.ACTION_NEW_MEMO, 1))
        val refresh = Intent(context, MemosWidgetProvider::class.java).setAction(ACTION_REFRESH)
        views.setOnClickPendingIntent(
            R.id.widget_refresh,
            PendingIntent.getBroadcast(context, id, refresh, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        )
        val subtitle = when {
            config.tag != null -> "#${config.tag}"
            config.pinnedOnly -> "Pinned"
            else -> null
        }
        views.setTextViewText(R.id.widget_subtitle, subtitle.orEmpty())
        views.setViewVisibility(R.id.widget_subtitle, if (subtitle != null) View.VISIBLE else View.GONE)

        val message = when {
            !snap.signedIn -> "Sign in to MemoCtrl to see your memos"
            snap.locked -> "MemoCtrl is locked"
            else -> null
        }
        val memos = if (message != null) emptyList() else snap.memos
            .filter { (config.tag == null || hasTag(it.content, config.tag)) && (!config.pinnedOnly || it.pinned) }
            .sortedWith(compareByDescending<MemoEntity> { it.pinned }.thenByDescending { it.date })
            .take(config.maxItems.coerceIn(1, 50))

        if (memos.isEmpty()) {
            views.setTextViewText(R.id.widget_empty, message ?: context.getString(R.string.widget_empty))
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            views.setViewVisibility(R.id.widget_list, View.GONE)
            views.setOnClickPendingIntent(R.id.widget_empty, openApp(context))
            return views
        }
        views.setViewVisibility(R.id.widget_empty, View.GONE)
        views.setViewVisibility(R.id.widget_list, View.VISIBLE)

        val items = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1)
        memos.forEach { m ->
            val item = RemoteViews(context.packageName, R.layout.widget_memo_item)
            item.setTextViewText(R.id.item_date, relative(m))
            item.setViewVisibility(R.id.item_pin, if (m.pinned) View.VISIBLE else View.GONE)
            item.setTextViewText(R.id.item_content, plain(m.content).take(400))
            if (m.pinned) item.setInt(R.id.item_root, "setBackgroundResource", R.drawable.widget_card_pinned)
            item.setOnClickFillInIntent(
                R.id.item_root,
                Intent().putExtra(MainActivity.EXTRA_MEMO_ID, m.identifier),
            )
            items.addItem(m.identifier.hashCode().toLong(), item)
        }
        views.setRemoteAdapter(R.id.widget_list, items.build())
        // The tapped memo's id is filled in; the template decides whether it opens or goes to the editor.
        val template = Intent(context, MainActivity::class.java).apply {
            action = if (snap.openInEditor) MainActivity.ACTION_EDIT_MEMO else MainActivity.ACTION_VIEW_MEMO
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        views.setPendingIntentTemplate(
            R.id.widget_list,
            PendingIntent.getActivity(context, 1000 + id, template, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE),
        )
        return views
    }

    private suspend fun buildMemory(context: Context, widgetId: Int): RemoteViews {
        val snap = snapshot(context)
        val views = RemoteViews(context.packageName, R.layout.widget_memory)
        val memo = if (snap.signedIn && !snap.locked) snap.memos.randomOrNull() else null
        when {
            !snap.signedIn -> {
                views.setTextViewText(R.id.memory_date, "")
                views.setTextViewText(R.id.memory_content, "Sign in to MemoCtrl to see your memos")
                views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
            }
            snap.locked -> {
                views.setTextViewText(R.id.memory_date, "")
                views.setTextViewText(R.id.memory_content, "MemoCtrl is locked")
                views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
            }
            memo == null -> {
                views.setTextViewText(R.id.memory_date, "")
                views.setTextViewText(R.id.memory_content, context.getString(R.string.widget_empty))
                views.setOnClickPendingIntent(R.id.widget_root, openApp(context, MainActivity.ACTION_NEW_MEMO, 1))
            }
            else -> {
                views.setTextViewText(R.id.memory_date, relative(memo))
                views.setTextViewText(R.id.memory_content, plain(memo.content).take(600))
                views.setOnClickPendingIntent(R.id.widget_root, memoIntent(context, memo.identifier, snap.openInEditor, 200_000 + widgetId))
            }
        }
        return views
    }
}

/** "Memos" widget: your latest memos, optionally only one tag or only pinned ones. */
class MemosWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Widgets.update(context, ids, memory = false) { pending.finish() }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Widgets.ACTION_REFRESH) {
            val pending = goAsync()
            Widgets.refresh(context) { pending.finish() }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onDeleted(context: Context, ids: IntArray) = Widgets.forget(context, ids)
}

/** "Memory" widget: a random memo, a different one each time it refreshes. */
class MemoryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Widgets.update(context, ids, memory = true) { pending.finish() }
    }
}
