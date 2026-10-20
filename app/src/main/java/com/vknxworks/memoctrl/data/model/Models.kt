package com.vknxworks.memoctrl.data.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.Locale

/** Who can see a memo. SPACE only exists on newer servers and is kept as-is when a memo already has it. */
@Serializable
enum class MemoVisibility { PRIVATE, PROTECTED, PUBLIC, SPACE }

/** Matches the location metadata in the Memos API. */
@Serializable
data class MemoLocation(
    val placeholder: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
) {
    val hasValidCoordinates: Boolean
        get() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

    val coordinatesText: String
        get() = String.format(Locale.US, "%.6f°, %.6f°", latitude, longitude)

    val displayText: String
        get() = placeholder.trim().ifEmpty { coordinatesText }

    companion object {
        fun fromInput(placeholder: String, latitude: String, longitude: String): MemoLocation? {
            val location = MemoLocation(
                placeholder.trim(),
                latitude.trim().replace(',', '.').toDoubleOrNull() ?: return null,
                longitude.trim().replace(',', '.').toDoubleOrNull() ?: return null,
            )
            return location.takeIf { it.hasValidCoordinates }
        }
    }
}

/** A null change leaves the location alone; a change holding null removes it. */
data class MemoLocationChange(val value: MemoLocation?)

data class User(
    val identifier: String,
    val name: String,
    val startDate: Instant = Instant.now(),
    val defaultVisibility: MemoVisibility = MemoVisibility.PRIVATE,
    val avatarUrl: String? = null,
)

/** A memo as the server returned it. */
data class RemoteMemo(
    val remoteId: String,
    val content: String,
    val date: Instant,
    val pinned: Boolean,
    val visibility: MemoVisibility,
    val resources: List<RemoteResource>,
    val archived: Boolean = false,
    val updatedAt: Instant? = null,
    val location: MemoLocation? = null,
    val creator: User? = null,
)

/** An attachment as the server returned it. */
data class RemoteResource(
    val remoteId: String,
    val date: Instant,
    val filename: String,
    val uri: String,
    val mimeType: String?,
)

/** A memo kept on the phone. Every change is saved here first and synced to the server after. */
data class MemoEntity(
    val identifier: String,
    val remoteId: String? = null,
    val accountKey: String,
    val content: String,
    val date: Instant,
    val visibility: MemoVisibility,
    val pinned: Boolean,
    val archived: Boolean = false,
    val needsSync: Boolean = true,
    val isDeleted: Boolean = false,
    val lastModified: Instant = Instant.now(),
    val lastSyncedAt: Instant? = null,
    val location: MemoLocation? = null,
    /** Filled in when read with its attachments; never written with the memo row. */
    val resources: List<ResourceEntity> = emptyList(),
)

/** An attachment kept on the phone: a server file, a local copy of one, or a file not uploaded yet. */
data class ResourceEntity(
    val identifier: String,
    val remoteId: String? = null,
    val accountKey: String,
    val date: Instant,
    val filename: String,
    /** Server address, or a file:// address while it isn't uploaded. */
    val uri: String,
    /** A copy of the file on the phone, when there is one. */
    val localUri: String? = null,
    val mimeType: String?,
    val memoId: String? = null,
) {
    val isImage: Boolean get() = mimeType?.startsWith("image/") == true
    /** Where to load it from: the local copy when there is one. */
    val displayUri: String get() = localUri ?: uri
}

data class SyncStatus(
    val syncing: Boolean = false,
    val unsyncedCount: Int = 0,
    val errorMessage: String? = null,
)

/** One day in the activity heatmap. */
data class DailyCount(val date: java.time.LocalDate, val count: Int)
