package com.vknxworks.memoctrl.data.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * MemoCtrl's settings as a JSON file the user keeps. Only settings: no account, server address, access
 * token, PIN, client certificate or memo is in it. The app lock and its timing are left out on purpose,
 * so a settings file can never turn the lock off or make it slower to lock; "Hide in screenshots" and
 * "Hide in recent apps" can only be turned on by a file, never off.
 */
@Serializable
data class SettingsBackup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    /** MemoCtrl version that wrote the file, for information. */
    val appVersion: String? = null,
    /** ISO time the file was written. */
    val createdAt: String? = null,
    val settings: BackupSettings = BackupSettings(),
) {
    companion object {
        const val FORMAT = "memoctrl-settings"
        const val VERSION = 1
    }
}

/** Every field is optional, so files from older or newer versions still restore what they can. */
@Serializable
data class BackupSettings(
    val editGesture: String? = null,
    val autosave: Boolean? = null,
    val searchIncludesArchived: Boolean? = null,
    val externalImages: Boolean? = null,
    val hideScreenshots: Boolean? = null,
    val hideRecents: Boolean? = null,
    val syncInterval: String? = null,
    val syncWifiOnly: Boolean? = null,
    val syncCharging: Boolean? = null,
    val liveUpdates: Boolean? = null,
    val linkPreviews: Boolean? = null,
    val mapTiles: Boolean? = null,
)

/** Thrown for a file that isn't a MemoCtrl settings file, or one from a newer, incompatible version. */
class BackupFormatException(val newer: Boolean) : Exception()

object SettingsBackups {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun export(s: AppSettings, appVersion: String, createdAt: String): String = json.encodeToString(
        SettingsBackup.serializer(),
        SettingsBackup(
            appVersion = appVersion,
            createdAt = createdAt,
            settings = BackupSettings(
                editGesture = s.editGesture.name,
                autosave = s.autosave,
                searchIncludesArchived = s.searchIncludesArchived,
                externalImages = s.externalImages,
                hideScreenshots = s.hideScreenshots,
                hideRecents = s.hideRecents,
                syncInterval = s.syncInterval.name,
                syncWifiOnly = s.syncWifiOnly,
                syncCharging = s.syncCharging,
                liveUpdates = s.liveUpdates,
                linkPreviews = s.linkPreviews,
                mapTiles = s.mapTiles,
            ),
        ),
    )

    fun parse(text: String): SettingsBackup {
        val backup = try {
            json.decodeFromString(SettingsBackup.serializer(), text)
        } catch (e: Exception) {
            throw BackupFormatException(newer = false)
        }
        if (backup.format != SettingsBackup.FORMAT) throw BackupFormatException(newer = false)
        if (backup.version > SettingsBackup.VERSION) throw BackupFormatException(newer = true)
        return backup
    }

    /** [current] with the settings from [b] applied. Values the app doesn't know are skipped. */
    fun apply(current: AppSettings, b: BackupSettings): AppSettings = current.copy(
        editGesture = b.editGesture?.let { n -> EditGesture.entries.firstOrNull { it.name == n } } ?: current.editGesture,
        autosave = b.autosave ?: current.autosave,
        searchIncludesArchived = b.searchIncludesArchived ?: current.searchIncludesArchived,
        externalImages = b.externalImages ?: current.externalImages,
        // Privacy switches are only ever turned on by a file.
        hideScreenshots = current.hideScreenshots || b.hideScreenshots == true,
        hideRecents = current.hideRecents || b.hideRecents == true,
        syncInterval = b.syncInterval?.let { n -> SyncInterval.entries.firstOrNull { it.name == n } } ?: current.syncInterval,
        syncWifiOnly = b.syncWifiOnly ?: current.syncWifiOnly,
        syncCharging = b.syncCharging ?: current.syncCharging,
        liveUpdates = b.liveUpdates ?: current.liveUpdates,
        linkPreviews = b.linkPreviews ?: current.linkPreviews,
        mapTiles = b.mapTiles ?: current.mapTiles,
    )

    /** The settings in [b] that differ from [current], as "name: old → new" pairs for the confirmation. */
    fun changes(current: AppSettings, b: BackupSettings): List<Triple<String, String, String>> {
        val after = apply(current, b)
        return buildList {
            fun <T> diff(key: String, old: T, new: T) { if (old != new) add(Triple(key, old.toString(), new.toString())) }
            diff("editGesture", current.editGesture, after.editGesture)
            diff("autosave", current.autosave, after.autosave)
            diff("searchIncludesArchived", current.searchIncludesArchived, after.searchIncludesArchived)
            diff("externalImages", current.externalImages, after.externalImages)
            diff("hideScreenshots", current.hideScreenshots, after.hideScreenshots)
            diff("hideRecents", current.hideRecents, after.hideRecents)
            diff("syncInterval", current.syncInterval, after.syncInterval)
            diff("syncWifiOnly", current.syncWifiOnly, after.syncWifiOnly)
            diff("syncCharging", current.syncCharging, after.syncCharging)
            diff("liveUpdates", current.liveUpdates, after.liveUpdates)
            diff("linkPreviews", current.linkPreviews, after.linkPreviews)
            diff("mapTiles", current.mapTiles, after.mapTiles)
        }
    }
}
