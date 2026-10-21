package com.vknxworks.memoctrl.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vknxworks.memoctrl.data.api.ApiJson
import com.vknxworks.memoctrl.data.appDataStore
import com.vknxworks.memoctrl.data.model.MemoLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.security.MessageDigest
import androidx.annotation.StringRes
import com.vknxworks.memoctrl.R

enum class ThemeChoice(@StringRes val label: Int) {
    SYSTEM(R.string.theme_system),
    DARK(R.string.theme_dark),
    LIGHT(R.string.theme_light),
    BLACK(R.string.theme_black),
}

/** How a memo in a list opens in the editor, besides its menu. */
enum class EditGesture(@StringRes val label: Int) {
    NONE(R.string.edit_gesture_none),
    SINGLE(R.string.edit_gesture_tap),
    DOUBLE(R.string.edit_gesture_double),
    LONG(R.string.edit_gesture_long),
}

/** When the app lock asks again after you leave the app. */
enum class LockTimeout(@StringRes val label: Int, val millis: Long) {
    IMMEDIATELY(R.string.lock_again_immediately, 0L),
    MINUTE_1(R.string.lock_again_1m, 60_000L),
    MINUTES_5(R.string.lock_again_5m, 5 * 60_000L),
    MINUTES_15(R.string.lock_again_15m, 15 * 60_000L),
    HOUR_1(R.string.lock_again_1h, 60 * 60_000L),
    RESTART(R.string.lock_again_restart, -1L),
}

data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val dynamicColor: Boolean = false,
    val editGesture: EditGesture = EditGesture.NONE,
    /** Save every change as you type, and sync it in the background. */
    val autosave: Boolean = false,
    val searchIncludesArchived: Boolean = false,
    val appLock: Boolean = false,
    val lockTimeout: LockTimeout = LockTimeout.IMMEDIATELY,
    /** Load pictures that memos link from other websites. Off: they show as a link instead. */
    val externalImages: Boolean = false,
    /** "Hide in screenshots": FLAG_SECURE on every window (also blanks the recent-apps preview). */
    val hideScreenshots: Boolean = false,
    /** "Hide in recent apps": the recent-apps view shows a blank card. Screenshots still work. */
    val hideRecents: Boolean = false,
)

/** An unsent new memo, kept per account until it's posted or thrown away. */
data class Draft(val content: String = "", val location: MemoLocation? = null)

class SettingsRepository(private val context: Context, scope: CoroutineScope) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val editGesture = stringPreferencesKey("edit_gesture")
        val autosave = booleanPreferencesKey("autosave")
        val searchArchived = booleanPreferencesKey("search_includes_archived")
        val appLock = booleanPreferencesKey("app_lock")
        val lockTimeout = stringPreferencesKey("lock_timeout")
        val externalImages = booleanPreferencesKey("external_images")
        val hideScreenshots = booleanPreferencesKey("hide_screenshots")
        val hideRecents = booleanPreferencesKey("hide_recents")
        /** 0.1.6's single "Hide in screenshots and recent apps": turned on, it turns both switches on. */
        val legacyHideContent = booleanPreferencesKey("hide_content")
    }

    val settings: StateFlow<AppSettings> = context.appDataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    /** The stored settings, without [settings]' placeholder defaults at start-up. */
    val stored: Flow<AppSettings> = context.appDataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        theme = enumOr(this[K.theme], ThemeChoice.SYSTEM),
        dynamicColor = this[K.dynamic] ?: false,
        editGesture = enumOr(this[K.editGesture], EditGesture.NONE),
        autosave = this[K.autosave] ?: false,
        searchIncludesArchived = this[K.searchArchived] ?: false,
        appLock = this[K.appLock] ?: false,
        lockTimeout = enumOr(this[K.lockTimeout], LockTimeout.IMMEDIATELY),
        externalImages = this[K.externalImages] ?: false,
        hideScreenshots = this[K.hideScreenshots] ?: this[K.legacyHideContent] ?: false,
        hideRecents = this[K.hideRecents] ?: this[K.legacyHideContent] ?: false,
    )

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.appDataStore.edit { p ->
            val s = transform(p.toSettings())
            p[K.theme] = s.theme.name
            p[K.dynamic] = s.dynamicColor
            p[K.editGesture] = s.editGesture.name
            p[K.autosave] = s.autosave
            p[K.searchArchived] = s.searchIncludesArchived
            p[K.appLock] = s.appLock
            p[K.lockTimeout] = s.lockTimeout.name
            p[K.externalImages] = s.externalImages
            p[K.hideScreenshots] = s.hideScreenshots
            p[K.hideRecents] = s.hideRecents
            p.remove(K.legacyHideContent)
        }
    }

    // ---------- Drafts ----------

    private fun draftKey(accountKey: String) = stringPreferencesKey("draft_" + hash(accountKey))

    suspend fun draft(accountKey: String): Draft {
        val raw = context.appDataStore.data.first()[draftKey(accountKey)] ?: return Draft()
        return runCatching { ApiJson.decodeFromString(StoredDraft.serializer(), raw) }.getOrNull()
            ?.let { Draft(it.content, it.location) } ?: Draft()
    }

    suspend fun saveDraft(accountKey: String, draft: Draft) {
        context.appDataStore.edit { p ->
            if (draft.content.isEmpty() && draft.location == null) p.remove(draftKey(accountKey))
            else p[draftKey(accountKey)] = ApiJson.encodeToString(StoredDraft.serializer(), StoredDraft(draft.content, draft.location))
        }
    }

    suspend fun clearDraft(accountKey: String) {
        context.appDataStore.edit { it.remove(draftKey(accountKey)) }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    companion object {
        fun hash(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    }
}

@kotlinx.serialization.Serializable
private data class StoredDraft(val content: String = "", val location: MemoLocation? = null)
