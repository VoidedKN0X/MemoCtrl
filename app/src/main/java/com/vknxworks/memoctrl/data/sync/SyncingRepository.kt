package com.vknxworks.memoctrl.data.sync

import android.net.Uri
import androidx.core.net.toUri
import com.vknxworks.memoctrl.data.api.AccessTokenInvalidException
import com.vknxworks.memoctrl.data.api.ApiException
import com.vknxworks.memoctrl.data.api.MemosRemote
import com.vknxworks.memoctrl.data.db.MemoDatabase
import com.vknxworks.memoctrl.data.files.FileStorage
import com.vknxworks.memoctrl.data.model.MemoEntity
import com.vknxworks.memoctrl.data.model.MemoLocation
import com.vknxworks.memoctrl.data.model.MemoLocationChange
import com.vknxworks.memoctrl.data.model.MemoVisibility
import com.vknxworks.memoctrl.data.model.RemoteMemo
import com.vknxworks.memoctrl.data.model.RemoteResource
import com.vknxworks.memoctrl.data.model.ResourceEntity
import com.vknxworks.memoctrl.data.model.SyncStatus
import com.vknxworks.memoctrl.data.model.User
import com.vknxworks.memoctrl.util.extractTags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.time.Instant
import java.util.UUID
import com.vknxworks.memoctrl.R
import com.vknxworks.memoctrl.str

/**
 * Offline-first memos for one account. Every change is written to the phone first and pushed to the
 * server right after (or a moment later while autosave is typing); a full sync pulls the server's
 * memos and merges both sides. When both sides changed the same memo, the server's version wins and
 * yours is kept as a new memo, so nothing is lost.
 */
class SyncingRepository(
    private val db: MemoDatabase,
    private val files: FileStorage,
    val remote: MemosRemote,
    val accountKey: String,
    initialUser: User,
    deferredPushDelayMillis: Long = 2000,
    private val onUserSynced: suspend (User) -> Unit = {},
) {
    private var currentUser: User = initialUser
    private val operationMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val deferred = DeferredPushScheduler(scope, deferredPushDelayMillis) { identifier ->
        if (db.getMemoById(identifier, accountKey)?.needsSync == true) enqueuePushMemo(identifier)
    }
    private var pendingDetailedError: String? = null
    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    val user: User get() = currentUser

    init {
        scope.launch { refreshUnsyncedCount() }
    }

    // ---------- Reading ----------

    /** The timeline (with attachments), reloaded after every change on the phone. */
    fun observeMemos(): Flow<List<MemoEntity>> = db.changes
        .debounce(80)
        .conflate()
        .map { listMemos() }
        .flowOn(Dispatchers.IO)

    /** Archived memos, reloaded after every change. */
    fun observeArchived(): Flow<List<MemoEntity>> = db.changes
        .debounce(80)
        .conflate()
        .map { listArchivedMemos() }
        .flowOn(Dispatchers.IO)

    suspend fun listMemos(): List<MemoEntity> = withResources(db.getAllMemos(accountKey))

    suspend fun listArchivedMemos(): List<MemoEntity> = withResources(db.getArchivedMemos(accountKey))

    suspend fun getMemo(identifier: String): MemoEntity? = db.getMemoById(identifier, accountKey)?.let { withResources(it) }

    suspend fun listTags(): List<String> =
        db.getAllMemos(accountKey).asSequence()
            .flatMap { extractTags(it.content).asSequence() }
            .filter { it.isNotBlank() }
            .toSortedSet()
            .toList()

    suspend fun listResources(): List<ResourceEntity> = db.getAllResources(accountKey)

    // ---------- Writing ----------

    suspend fun createMemo(
        content: String,
        visibility: MemoVisibility,
        resources: List<ResourceEntity>,
        deferPush: Boolean = false,
        location: MemoLocation? = null,
    ): MemoEntity {
        val now = Instant.now()
        val memo = MemoEntity(
            identifier = UUID.randomUUID().toString(),
            remoteId = null,
            accountKey = accountKey,
            content = content,
            location = location,
            date = now,
            visibility = visibility,
            pinned = false,
            archived = false,
            needsSync = true,
            isDeleted = false,
            lastModified = now,
            lastSyncedAt = null,
        )
        db.insertMemo(memo)
        resources.forEach { db.attachResourceToMemo(it, memo.identifier, accountKey) }
        refreshUnsyncedCount()
        if (deferPush) deferred.schedule(memo.identifier) else enqueuePushMemo(memo.identifier)
        return withResources(memo)
    }

    suspend fun updateMemo(
        identifier: String,
        content: String? = null,
        resources: List<ResourceEntity>? = null,
        visibility: MemoVisibility? = null,
        pinned: Boolean? = null,
        deferPush: Boolean = false,
        location: MemoLocationChange? = null,
    ): MemoEntity {
        var updated: MemoEntity
        // Compare-and-set, so a push landing between the read and the write (it records the server id)
        // isn't overwritten with the stale copy, which would create the memo on the server again.
        do {
            val existing = db.getMemoById(identifier, accountKey) ?: throw IllegalStateException(str(R.string.error_memo_gone))
            updated = existing.copy(
                content = content ?: existing.content,
                location = if (location != null) location.value else existing.location,
                visibility = visibility ?: existing.visibility,
                pinned = pinned ?: existing.pinned,
                needsSync = true,
                isDeleted = false,
                lastModified = nextLastModified(existing),
                resources = emptyList(),
            )
        } while (!db.insertMemoIfUnchanged(updated, existing.lastModified))

        if (resources != null) {
            val keep = resources.mapTo(HashSet()) { it.identifier }
            db.getMemoResources(identifier, accountKey).forEach { existing ->
                if (existing.identifier !in keep) {
                    deleteLocalFile(existing)
                    db.deleteResource(existing.identifier)
                }
            }
            resources.forEach { db.attachResourceToMemo(it, identifier, accountKey) }
        }

        refreshUnsyncedCount()
        if (deferPush) deferred.schedule(identifier) else enqueuePushMemo(identifier)
        return withResources(updated)
    }

    suspend fun flushPendingPush(identifier: String) = deferred.flush(identifier)

    suspend fun deleteMemo(identifier: String) {
        val memo = db.getMemoById(identifier, accountKey) ?: return
        db.insertMemo(memo.copy(isDeleted = true, needsSync = true, lastModified = nextLastModified(memo)))
        refreshUnsyncedCount()
        deferred.cancel(identifier)
        enqueuePushMemo(identifier)
    }

    suspend fun archiveMemo(identifier: String) = setArchived(identifier, true)

    suspend fun restoreMemo(identifier: String) = setArchived(identifier, false)

    private suspend fun setArchived(identifier: String, archived: Boolean) {
        val memo = db.getMemoById(identifier, accountKey) ?: return
        db.insertMemo(memo.copy(archived = archived, needsSync = true, lastModified = nextLastModified(memo)))
        refreshUnsyncedCount()
        enqueuePushMemo(identifier)
    }

    /** Keeps a copy of a picked file on the phone and uploads it with its memo (or right away if it has none). */
    suspend fun createResource(filename: String, type: MediaType?, contentUri: Uri, memoIdentifier: String? = null): ResourceEntity {
        val id = UUID.randomUUID().toString()
        val uri = files.saveFile(accountKey, contentUri, id + "_" + filename)
        val resource = ResourceEntity(
            identifier = id,
            remoteId = null,
            accountKey = accountKey,
            date = Instant.now(),
            filename = filename,
            uri = uri.toString(),
            localUri = uri.toString(),
            mimeType = type?.toString(),
            memoId = memoIdentifier?.takeIf { db.getMemoById(it, accountKey) != null },
        )
        db.insertResource(resource)
        if (resource.memoId != null) {
            db.getMemoById(resource.memoId, accountKey)?.let { memo ->
                db.insertMemo(memo.copy(needsSync = true, lastModified = nextLastModified(memo)))
            }
            enqueuePushMemo(resource.memoId)
        } else {
            enqueuePushResource(resource.identifier)
        }
        refreshUnsyncedCount()
        return resource
    }

    suspend fun deleteResource(identifier: String) {
        val resource = db.getResourceById(identifier, accountKey) ?: return
        val memoId = resource.memoId
        deleteLocalFile(resource)
        db.deleteResource(resource.identifier)
        if (!memoId.isNullOrBlank()) {
            db.getMemoById(memoId, accountKey)?.let { memo ->
                db.insertMemo(memo.copy(needsSync = true, lastModified = nextLastModified(memo)))
            }
        }
        resource.remoteId?.let { enqueueDeleteRemoteResource(it) }
        if (!memoId.isNullOrBlank()) enqueuePushMemo(memoId)
        refreshUnsyncedCount()
    }

    /** Keeps a downloaded server file as the attachment's copy on the phone, so it opens offline next time. */
    suspend fun cacheResourceFile(identifier: String, downloaded: File): ResourceEntity? {
        val resource = db.getResourceById(identifier, accountKey) ?: return null
        existingLocalFile(resource)?.let { return resource }
        val canonical = downloaded.inputStream().use {
            files.saveFile(accountKey, it, "${resource.identifier}_${resource.filename}")
        }.toString()
        resource.localUri?.takeIf { it != canonical }?.toUri()?.let { if (it.scheme == "file") files.deleteFile(it) }
        val updated = resource.copy(
            uri = if (resource.remoteId == null && resource.uri.toUri().scheme == "file") canonical else resource.uri,
            localUri = canonical,
        )
        db.insertResource(updated)
        return updated
    }

    // ---------- Sync ----------

    /** Pulls everything from the server and pushes what changed here. Throws when it didn't fully work. */
    suspend fun sync() {
        operationMutex.withLock {
            setSyncing(true)
            pendingDetailedError = null
            try {
                syncInternal()
                setSyncError(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                setSyncError(e.message ?: str(R.string.error_sync_failed))
                throw e
            } finally {
                refreshUnsyncedCount()
                setSyncing(false)
            }
        }
    }

    private suspend fun syncInternal() {
        refreshCurrentUser()

        val remoteMemos = remote.listMemos() + remote.listArchivedMemos()
        val remoteById = remoteMemos.associateBy { remoteMemoId(it) }

        var firstError: String? = null
        fun recordFailure() {
            if (firstError == null) firstError = consumeDetailedError() ?: str(R.string.error_some_memos)
        }

        val localByRemoteId = db.getAllMemosForSync(accountKey).mapNotNull { m -> m.remoteId?.let { it to m } }.toMap()

        for (remoteMemo in remoteMemos) {
            val remoteId = remoteMemoId(remoteMemo)
            // Read again: the snapshot can be seconds old, and autosave keeps writing during a sync.
            val local = localByRemoteId[remoteId]?.let { db.getMemoById(it.identifier, accountKey) }

            if (local == null) {
                applyRemoteMemo(remoteMemo)
                continue
            }

            if (local.isDeleted) {
                if (!local.needsSync) {
                    applyRemoteMemo(remoteMemo, local.identifier, expectedLastModified = local.lastModified)
                    continue
                }
                if (hasRemoteChanged(local, remoteMemo) || !memoEquivalent(local, remoteMemo)) {
                    applyRemoteMemo(remoteMemo, local.identifier, expectedLastModified = local.lastModified)
                } else {
                    if (tryRemote { remote.deleteMemo(remoteId) }) permanentlyDeleteMemo(local.identifier) else recordFailure()
                }
                continue
            }

            if (memoEquivalent(local, remoteMemo)) {
                markSynced(local, remoteMemo)
                continue
            }

            when {
                !local.needsSync -> applyRemoteMemo(remoteMemo, local.identifier, expectedLastModified = local.lastModified)
                !hasRemoteChanged(local, remoteMemo) -> if (!pushLocalMemo(local.identifier)) recordFailure()
                else -> if (!duplicateConflict(local, remoteMemo)) recordFailure()
            }
        }

        for (snapshot in db.getAllMemosForSync(accountKey)) {
            val local = db.getMemoById(snapshot.identifier, accountKey) ?: continue
            if (local.remoteId != null && remoteById.containsKey(local.remoteId)) continue
            // A conflict copy (or a push running alongside) can get a server id after the server list
            // was fetched. Missing from that list doesn't mean it was deleted on the server.
            if (local.remoteId != null && !localByRemoteId.containsKey(local.remoteId)) continue

            if (local.remoteId != null) {
                // It was on the server before and isn't anymore.
                when {
                    local.isDeleted -> permanentlyDeleteMemo(local.identifier)
                    local.needsSync -> if (!pushLocalMemo(local.identifier, forceCreate = true)) recordFailure()
                    else -> permanentlyDeleteMemo(local.identifier)
                }
                continue
            }

            when {
                local.isDeleted -> permanentlyDeleteMemo(local.identifier)
                local.needsSync -> if (!pushLocalMemo(local.identifier, forceCreate = true)) recordFailure()
            }
        }

        firstError?.let { throw ApiException(it) }
    }

    private suspend fun refreshCurrentUser() {
        val user = try {
            remote.currentUser()
        } catch (e: ApiException) {
            if (e.isUnauthorized) throw AccessTokenInvalidException()
            throw e
        }
        currentUser = user
        onUserSynced(user)
    }

    /** Runs a server call, keeping its error message for the sync status. False when it failed. */
    private suspend fun tryRemote(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        if (pendingDetailedError == null) pendingDetailedError = e.message
        false
    }

    private suspend fun pushLocalMemo(identifier: String, forceCreate: Boolean = false): Boolean {
        pendingDetailedError = null
        val local = db.getMemoById(identifier, accountKey) ?: return true

        if (local.isDeleted) {
            if (local.remoteId != null && !tryRemote { remote.deleteMemo(local.remoteId) }) return false
            permanentlyDeleteMemo(local.identifier)
            return true
        }

        val uploaded = ensureUploadedResources(local)
        if (uploaded.failed > 0) {
            if (pendingDetailedError == null) pendingDetailedError = str(R.string.error_attachments_failed)
            return false
        }
        val resourceIds = uploaded.remoteIds

        if (!forceCreate && local.remoteId != null) {
            var result: RemoteMemo? = null
            val ok = tryRemote {
                result = remote.updateMemo(
                    remoteId = local.remoteId,
                    content = local.content,
                    resourceRemoteIds = resourceIds,
                    visibility = local.visibility,
                    pinned = local.pinned,
                    archived = local.archived,
                    location = if (remote.supportsLocation) MemoLocationChange(local.location) else null,
                )
            }
            val updated = result
            if (!ok || updated == null) return false
            reconcileServerCreatedMemo(local, updated.copy(archived = local.archived))
            return true
        }

        var created: RemoteMemo? = null
        val ok = tryRemote {
            created = remote.createMemo(
                content = local.content,
                visibility = local.visibility,
                resourceRemoteIds = resourceIds,
                createdAt = local.date,
                location = if (remote.supportsLocation) local.location else null,
            )
        }
        var remoteMemo = created
        if (!ok || remoteMemo == null) return false
        val createdId = remoteMemoId(remoteMemo)

        // A new memo can't be created pinned or archived; set those right after.
        if (local.pinned || local.archived) {
            var after: RemoteMemo? = null
            val updatedOk = tryRemote {
                after = remote.updateMemo(
                    remoteId = createdId,
                    content = local.content,
                    resourceRemoteIds = resourceIds,
                    visibility = local.visibility,
                    pinned = local.pinned,
                    archived = local.archived,
                    location = if (remote.supportsLocation) MemoLocationChange(local.location) else null,
                )
            }
            val afterMemo = after
            if (!updatedOk || afterMemo == null) {
                // Keep the server link and needsSync: the next push is an update, and it tries again.
                reconcileServerCreatedMemo(local, remoteMemo, keepLocal = true)
                return false
            }
            remoteMemo = afterMemo.copy(remoteId = createdId, archived = local.archived)
        }
        reconcileServerCreatedMemo(local, remoteMemo)
        return true
    }

    private suspend fun duplicateConflict(local: MemoEntity, remoteMemo: RemoteMemo): Boolean {
        val copy = local.copy(
            identifier = UUID.randomUUID().toString(),
            remoteId = null,
            needsSync = true,
            isDeleted = false,
            lastSyncedAt = null,
            lastModified = Instant.now(),
            resources = emptyList(),
        )
        db.insertMemo(copy)
        db.getMemoResources(local.identifier, accountKey).forEach { r ->
            db.insertResource(r.copy(identifier = UUID.randomUUID().toString(), memoId = copy.identifier))
        }
        applyRemoteMemo(remoteMemo, local.identifier)
        return pushLocalMemo(copy.identifier, forceCreate = true)
    }

    private suspend fun reconcileServerCreatedMemo(pushed: MemoEntity, remoteMemo: RemoteMemo, keepLocal: Boolean = false) {
        // Each write only happens if the row is unchanged since it was read, so an edit landing in
        // between (autosave writes on every keystroke) is never overwritten; it tries again instead.
        repeat(MAX_RECONCILE_ATTEMPTS) {
            val current = db.getMemoById(pushed.identifier, accountKey) ?: return
            val written = if (keepLocal || current.lastModified != pushed.lastModified) {
                // Edited here while the push was on its way: keep the local text, record the server link.
                db.insertMemoIfUnchanged(
                    current.copy(
                        remoteId = remoteMemoId(remoteMemo),
                        lastSyncedAt = remoteMemo.updatedAt ?: remoteMemo.date,
                        needsSync = true,
                    ),
                    current.lastModified,
                )
            } else {
                applyRemoteMemo(remoteMemo, pushed.identifier, keepPendingResources = true, expectedLastModified = pushed.lastModified)
            }
            if (written) return
        }
        if (!keepLocal) applyRemoteMemo(remoteMemo, pushed.identifier, keepPendingResources = true)
    }

    private data class Uploaded(val remoteIds: List<String>, val failed: Int)

    private suspend fun ensureUploadedResources(memo: MemoEntity): Uploaded {
        val ids = ArrayList<String>()
        var failed = 0
        for (r in db.getMemoResources(memo.identifier, accountKey)) {
            val rid = ensureUploadedResource(r, memo.remoteId)?.remoteId
            if (rid != null) ids += rid
            else if (r.remoteId == null) failed++
        }
        return Uploaded(ids, failed)
    }

    private suspend fun ensureUploadedResource(resource: ResourceEntity, memoRemoteId: String?): ResourceEntity? {
        if (resource.remoteId != null) return resource
        val file = existingLocalFile(resource) ?: return null
        var uploaded: RemoteResource? = null
        tryRemote {
            uploaded = remote.createResource(
                filename = resource.filename,
                type = resource.mimeType?.toMediaTypeOrNull(),
                contentLength = file.length(),
                openInputStream = { file.inputStream() },
                memoRemoteId = memoRemoteId,
            )
        }
        val done = uploaded ?: return null
        return db.recordResourceUpload(resource.identifier, accountKey, remoteResourceId(done), done.uri)
    }

    /**
     * Writes [remoteMemo] over the local row. With [expectedLastModified] it only writes if the row
     * still has that lastModified, and returns false when it skipped the write for that reason.
     */
    private suspend fun applyRemoteMemo(
        remoteMemo: RemoteMemo,
        preferredLocalIdentifier: String? = null,
        keepPendingResources: Boolean = false,
        expectedLastModified: Instant? = null,
    ): Boolean {
        val remoteId = remoteMemoId(remoteMemo)
        val current = db.getMemoByRemoteId(remoteId, accountKey)
            ?: preferredLocalIdentifier?.let { db.getMemoById(it, accountKey) }
        val localId = current?.identifier ?: UUID.randomUUID().toString()
        val updatedAt = remoteMemo.updatedAt ?: remoteMemo.date

        val entity = MemoEntity(
            identifier = localId,
            remoteId = remoteId,
            accountKey = accountKey,
            content = remoteMemo.content,
            location = remoteMemo.location,
            date = remoteMemo.date,
            visibility = remoteMemo.visibility,
            pinned = remoteMemo.pinned,
            archived = remoteMemo.archived,
            needsSync = false,
            isDeleted = false,
            lastModified = updatedAt,
            lastSyncedAt = updatedAt,
        )
        if (current != null && expectedLastModified != null) {
            if (!db.insertMemoIfUnchanged(entity, expectedLastModified)) return false
        } else {
            db.insertMemo(entity)
        }

        val currentResources = db.getMemoResources(localId, accountKey)
        val remoteIds = remoteMemo.resources.mapTo(HashSet()) { remoteResourceId(it) }
        currentResources.forEach { r ->
            if (keepPendingResources && r.remoteId == null) return@forEach
            if (r.remoteId !in remoteIds) {
                deleteLocalFile(r)
                db.deleteResource(r.identifier)
            }
        }
        remoteMemo.resources.forEach { res ->
            val rid = remoteResourceId(res)
            val existing = currentResources.firstOrNull { it.remoteId == rid }
            val existingLocal = existing?.localUri
            val existingUri = existing?.uri
            val keptLocal = when {
                existingLocal != null && fileOf(existingLocal)?.exists() == true -> existingLocal
                existingUri != null && fileOf(existingUri)?.exists() == true -> existingUri
                else -> null
            }
            db.insertResource(
                ResourceEntity(
                    identifier = existing?.identifier ?: UUID.randomUUID().toString(),
                    remoteId = rid,
                    accountKey = accountKey,
                    date = res.date,
                    filename = res.filename,
                    uri = res.uri,
                    localUri = keptLocal,
                    mimeType = res.mimeType,
                    memoId = localId,
                ),
            )
        }
        return true
    }

    private suspend fun markSynced(local: MemoEntity, remoteMemo: RemoteMemo) {
        // Skipped if the row was edited after `local` was read: it keeps needsSync and gets pushed.
        db.insertMemoIfUnchanged(
            local.copy(
                remoteId = remoteMemoId(remoteMemo),
                date = remoteMemo.date,
                needsSync = false,
                isDeleted = false,
                archived = remoteMemo.archived,
                lastSyncedAt = remoteMemo.updatedAt ?: remoteMemo.date,
                resources = emptyList(),
            ),
            local.lastModified,
        )
    }

    private suspend fun memoEquivalent(local: MemoEntity, remote: RemoteMemo): Boolean {
        if (this.remote.supportsLocation && local.location != remote.location) return false
        if (local.content != remote.content) return false
        if (local.pinned != remote.pinned) return false
        if (local.visibility != remote.visibility) return false
        if (local.archived != remote.archived) return false
        val localSig = db.getMemoResources(local.identifier, accountKey).map { it.remoteId ?: "local:${it.localUri ?: it.uri}" }.sorted()
        val remoteSig = remote.resources.map { it.remoteId }.sorted()
        return localSig == remoteSig
    }

    private fun hasRemoteChanged(local: MemoEntity, remote: RemoteMemo): Boolean {
        val remoteUpdated = remote.updatedAt ?: remote.date
        val lastSynced = local.lastSyncedAt ?: return true
        return remoteUpdated.toEpochMilli() != lastSynced.toEpochMilli()
    }

    private suspend fun pushLocalResource(identifier: String): Boolean {
        pendingDetailedError = null
        val local = db.getResourceById(identifier, accountKey) ?: return true
        val ensured = ensureUploadedResource(local, null)
        if (ensured?.remoteId == null) {
            if (pendingDetailedError == null) pendingDetailedError = str(R.string.error_attachments_failed)
            return false
        }
        return true
    }

    private fun enqueuePushMemo(identifier: String) = enqueue(str(R.string.error_push_memo)) { pushLocalMemo(identifier) }

    private fun enqueuePushResource(identifier: String) = enqueue(str(R.string.error_push_attachment)) { pushLocalResource(identifier) }

    private fun enqueueDeleteRemoteResource(remoteId: String) =
        enqueue(str(R.string.error_delete_attachment)) { tryRemote { remote.deleteResource(remoteId) } }

    private fun enqueue(defaultError: String, block: suspend () -> Boolean) {
        scope.launch {
            operationMutex.withLock {
                setSyncing(true)
                try {
                    if (block()) setSyncError(null) else setSyncError(consumeDetailedError() ?: defaultError)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    setSyncError(e.message ?: defaultError)
                } finally {
                    refreshUnsyncedCount()
                    setSyncing(false)
                }
            }
        }
    }

    // Strictly after the stored value, so a compare-and-set on lastModified sees every local write
    // even when two land in the same millisecond.
    private fun nextLastModified(existing: MemoEntity): Instant {
        val now = Instant.ofEpochMilli(System.currentTimeMillis())
        val minimum = existing.lastModified.plusMillis(1)
        return if (now.isBefore(minimum)) minimum else now
    }

    private suspend fun refreshUnsyncedCount() {
        val count = db.countUnsyncedMemos(accountKey)
        _syncStatus.update { it.copy(unsyncedCount = count) }
    }

    private fun setSyncing(syncing: Boolean) = _syncStatus.update { it.copy(syncing = syncing) }

    private fun setSyncError(message: String?) = _syncStatus.update { it.copy(errorMessage = message) }

    private fun consumeDetailedError(): String? = pendingDetailedError.also { pendingDetailedError = null }

    private suspend fun withResources(memo: MemoEntity): MemoEntity =
        memo.copy(resources = db.getMemoResources(memo.identifier, accountKey))

    private suspend fun withResources(memos: List<MemoEntity>): List<MemoEntity> {
        if (memos.isEmpty()) return memos
        val byMemo = db.getResourcesByMemo(accountKey)
        return memos.map { it.copy(resources = byMemo[it.identifier].orEmpty()) }
    }

    private suspend fun permanentlyDeleteMemo(identifier: String) {
        db.getMemoById(identifier, accountKey) ?: return
        db.getMemoResources(identifier, accountKey).forEach { deleteLocalFile(it) }
        db.deleteMemo(identifier)
    }

    private fun fileOf(uri: String): File? {
        val u = uri.toUri()
        if (u.scheme != "file") return null
        return u.path?.let(::File)
    }

    private fun existingLocalFile(resource: ResourceEntity): File? =
        (resource.localUri?.let(::fileOf) ?: fileOf(resource.uri))?.takeIf { it.exists() }

    private fun deleteLocalFile(resource: ResourceEntity) {
        val uri = resource.localUri?.toUri() ?: resource.uri.toUri().takeIf { it.scheme == "file" }
        if (uri != null && uri.scheme == "file") files.deleteFile(uri)
    }

    private fun remoteMemoId(memo: RemoteMemo): String =
        memo.remoteId.takeIf { it.isNotBlank() } ?: throw IllegalStateException(str(R.string.error_memo_no_id))

    private fun remoteResourceId(resource: RemoteResource): String =
        resource.remoteId.takeIf { it.isNotBlank() } ?: throw IllegalStateException(str(R.string.error_attachment_no_id))

    fun close() = scope.cancel()

    companion object {
        private const val MAX_RECONCILE_ATTEMPTS = 5
    }
}

/**
 * Coalesces pushes per memo: every [schedule] restarts a short wait and only the last one pushes.
 * [flush] pushes right away, [cancel] drops the waiting push.
 */
class DeferredPushScheduler(
    private val scope: CoroutineScope,
    private val delayMillis: Long,
    private val push: suspend (String) -> Unit,
) {
    private val jobs = mutableMapOf<String, Job>()
    private val lock = Any()

    fun schedule(identifier: String) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            delay(delayMillis)
            push(identifier)
        }
        synchronized(lock) { jobs.put(identifier, job)?.cancel() }
        job.invokeOnCompletion { synchronized(lock) { if (jobs[identifier] === job) jobs.remove(identifier) } }
        job.start()
    }

    fun cancel(identifier: String) {
        synchronized(lock) { jobs.remove(identifier) }?.cancel()
    }

    suspend fun flush(identifier: String) {
        cancel(identifier)
        push(identifier)
    }
}
