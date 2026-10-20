package com.vknxworks.memoctrl.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.vknxworks.memoctrl.data.api.ApiJson
import com.vknxworks.memoctrl.data.model.MemoEntity
import com.vknxworks.memoctrl.data.model.MemoLocation
import com.vknxworks.memoctrl.data.model.MemoVisibility
import com.vknxworks.memoctrl.data.model.ResourceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * The memos and attachments kept on the phone, for every signed-in account. Plain SQLite, so the app
 * works offline and nothing needs a code generator. Every write bumps [changes], which screens watch.
 */
class MemoDatabase(context: Context) : SQLiteOpenHelper(context, "memoctrl.db", null, 1) {

    private val _changes = MutableStateFlow(0L)

    /** Goes up after every write, so lists that show memos reload. */
    val changes: StateFlow<Long> = _changes.asStateFlow()

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE memos (
                identifier TEXT NOT NULL PRIMARY KEY,
                remote_id TEXT,
                account_key TEXT NOT NULL,
                content TEXT NOT NULL,
                date INTEGER NOT NULL,
                visibility TEXT NOT NULL,
                pinned INTEGER NOT NULL,
                archived INTEGER NOT NULL,
                needs_sync INTEGER NOT NULL,
                is_deleted INTEGER NOT NULL,
                last_modified INTEGER NOT NULL,
                last_synced_at INTEGER,
                location TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX memos_account ON memos(account_key)")
        db.execSQL("CREATE INDEX memos_remote ON memos(account_key, remote_id)")
        db.execSQL(
            """
            CREATE TABLE resources (
                identifier TEXT NOT NULL PRIMARY KEY,
                remote_id TEXT,
                account_key TEXT NOT NULL,
                date INTEGER NOT NULL,
                filename TEXT NOT NULL,
                uri TEXT NOT NULL,
                local_uri TEXT,
                mime_type TEXT,
                memo_id TEXT REFERENCES memos(identifier) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX resources_memo ON resources(memo_id)")
        db.execSQL("CREATE INDEX resources_account ON resources(account_key)")
        db.execSQL("CREATE INDEX resources_remote ON resources(account_key, remote_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun changed() = _changes.update { it + 1 }

    private suspend fun <T> io(block: SQLiteDatabase.() -> T): T = withContext(Dispatchers.IO) { writableDatabase.block() }

    // ---------- Memos ----------

    private fun Cursor.toMemo(): MemoEntity = MemoEntity(
        identifier = getString(getColumnIndexOrThrow("identifier")),
        remoteId = getStringOrNull("remote_id"),
        accountKey = getString(getColumnIndexOrThrow("account_key")),
        content = getString(getColumnIndexOrThrow("content")),
        date = Instant.ofEpochMilli(getLong(getColumnIndexOrThrow("date"))),
        visibility = runCatching { MemoVisibility.valueOf(getString(getColumnIndexOrThrow("visibility"))) }.getOrDefault(MemoVisibility.PRIVATE),
        pinned = getInt(getColumnIndexOrThrow("pinned")) != 0,
        archived = getInt(getColumnIndexOrThrow("archived")) != 0,
        needsSync = getInt(getColumnIndexOrThrow("needs_sync")) != 0,
        isDeleted = getInt(getColumnIndexOrThrow("is_deleted")) != 0,
        lastModified = Instant.ofEpochMilli(getLong(getColumnIndexOrThrow("last_modified"))),
        lastSyncedAt = getLongOrNull("last_synced_at")?.let(Instant::ofEpochMilli),
        location = getStringOrNull("location")?.let { runCatching { ApiJson.decodeFromString(MemoLocation.serializer(), it) }.getOrNull() },
    )

    private fun MemoEntity.values() = ContentValues().apply {
        put("identifier", identifier)
        put("remote_id", remoteId)
        put("account_key", accountKey)
        put("content", content)
        put("date", date.toEpochMilli())
        put("visibility", visibility.name)
        put("pinned", if (pinned) 1 else 0)
        put("archived", if (archived) 1 else 0)
        put("needs_sync", if (needsSync) 1 else 0)
        put("is_deleted", if (isDeleted) 1 else 0)
        put("last_modified", lastModified.toEpochMilli())
        put("last_synced_at", lastSyncedAt?.toEpochMilli())
        put("location", location?.let { ApiJson.encodeToString(MemoLocation.serializer(), it) })
    }

    private fun SQLiteDatabase.queryMemos(where: String, args: Array<String>, order: String? = null): List<MemoEntity> =
        query("memos", null, where, args, null, null, order).use { c ->
            buildList { while (c.moveToNext()) add(c.toMemo()) }
        }

    /** The timeline: not archived, not deleted, pinned first, newest first. */
    suspend fun getAllMemos(accountKey: String): List<MemoEntity> = io {
        queryMemos("account_key = ? AND archived = 0 AND is_deleted = 0", arrayOf(accountKey), "pinned DESC, date DESC")
    }

    suspend fun getArchivedMemos(accountKey: String): List<MemoEntity> = io {
        queryMemos("account_key = ? AND archived = 1 AND is_deleted = 0", arrayOf(accountKey), "date DESC")
    }

    suspend fun getAllMemosForSync(accountKey: String): List<MemoEntity> = io {
        queryMemos("account_key = ?", arrayOf(accountKey))
    }

    suspend fun countUnsyncedMemos(accountKey: String): Int = io {
        rawQuery("SELECT COUNT(*) FROM memos WHERE account_key = ? AND needs_sync = 1", arrayOf(accountKey)).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    suspend fun getMemoById(identifier: String, accountKey: String): MemoEntity? = io {
        queryMemos("identifier = ? AND account_key = ?", arrayOf(identifier, accountKey)).firstOrNull()
    }

    suspend fun getMemoByRemoteId(remoteId: String, accountKey: String): MemoEntity? = io {
        queryMemos("remote_id = ? AND account_key = ?", arrayOf(remoteId, accountKey)).firstOrNull()
    }

    /** Inserts or replaces the row, keeping its attachments (a replace would cascade-delete them). */
    suspend fun insertMemo(memo: MemoEntity) {
        io { upsertMemo(memo) }
        changed()
    }

    private fun SQLiteDatabase.upsertMemo(memo: MemoEntity) {
        val v = memo.values()
        val updated = update("memos", v, "identifier = ?", arrayOf(memo.identifier))
        if (updated == 0) insertOrThrow("memos", null, v)
    }

    /**
     * Writes [memo] only if its stored row still has [expectedLastModified], i.e. nothing wrote the
     * row since the caller read it. Returns false (and writes nothing) otherwise or if the row is gone.
     */
    suspend fun insertMemoIfUnchanged(memo: MemoEntity, expectedLastModified: Instant): Boolean {
        val written = io {
            update(
                "memos",
                memo.values(),
                "identifier = ? AND account_key = ? AND last_modified = ?",
                arrayOf(memo.identifier, memo.accountKey, expectedLastModified.toEpochMilli().toString()),
            ) > 0
        }
        if (written) changed()
        return written
    }

    suspend fun deleteMemo(identifier: String) {
        io { delete("memos", "identifier = ?", arrayOf(identifier)) }
        changed()
    }

    suspend fun deleteAccount(accountKey: String) {
        io {
            beginTransaction()
            try {
                delete("resources", "account_key = ?", arrayOf(accountKey))
                delete("memos", "account_key = ?", arrayOf(accountKey))
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
        changed()
    }

    // ---------- Attachments ----------

    private fun Cursor.toResource(): ResourceEntity = ResourceEntity(
        identifier = getString(getColumnIndexOrThrow("identifier")),
        remoteId = getStringOrNull("remote_id"),
        accountKey = getString(getColumnIndexOrThrow("account_key")),
        date = Instant.ofEpochMilli(getLong(getColumnIndexOrThrow("date"))),
        filename = getString(getColumnIndexOrThrow("filename")),
        uri = getString(getColumnIndexOrThrow("uri")),
        localUri = getStringOrNull("local_uri"),
        mimeType = getStringOrNull("mime_type"),
        memoId = getStringOrNull("memo_id"),
    )

    private fun ResourceEntity.values() = ContentValues().apply {
        put("identifier", identifier)
        put("remote_id", remoteId)
        put("account_key", accountKey)
        put("date", date.toEpochMilli())
        put("filename", filename)
        put("uri", uri)
        put("local_uri", localUri)
        put("mime_type", mimeType)
        put("memo_id", memoId)
    }

    private fun SQLiteDatabase.queryResources(where: String, args: Array<String>, order: String? = null): List<ResourceEntity> =
        query("resources", null, where, args, null, null, order).use { c ->
            buildList { while (c.moveToNext()) add(c.toResource()) }
        }

    suspend fun getMemoResources(memoId: String, accountKey: String): List<ResourceEntity> = io {
        queryResources("memo_id = ? AND account_key = ?", arrayOf(memoId, accountKey), "date ASC")
    }

    /** All attachments of an account, keyed by the memo they belong to. One query for a whole list. */
    suspend fun getResourcesByMemo(accountKey: String): Map<String, List<ResourceEntity>> = io {
        queryResources("account_key = ? AND memo_id IS NOT NULL", arrayOf(accountKey), "date ASC").groupBy { it.memoId!! }
    }

    suspend fun getAllResources(accountKey: String): List<ResourceEntity> = io {
        queryResources("account_key = ?", arrayOf(accountKey), "date DESC")
    }

    suspend fun getResourceById(identifier: String, accountKey: String): ResourceEntity? = io {
        queryResources("identifier = ? AND account_key = ?", arrayOf(identifier, accountKey)).firstOrNull()
    }

    suspend fun insertResource(resource: ResourceEntity) {
        io {
            val v = resource.values()
            val updated = update("resources", v, "identifier = ?", arrayOf(resource.identifier))
            if (updated == 0) insertOrThrow("resources", null, v)
        }
        changed()
    }

    /** Links an attachment to a memo, keeping server details stored since the editor read it. */
    suspend fun attachResourceToMemo(resource: ResourceEntity, memoId: String, accountKey: String) {
        val current = getResourceById(resource.identifier, accountKey) ?: resource
        insertResource(current.copy(accountKey = accountKey, memoId = memoId))
    }

    /** Records that an attachment reached the server. Null when it was removed while uploading. */
    suspend fun recordResourceUpload(identifier: String, accountKey: String, remoteId: String, uri: String): ResourceEntity? {
        val current = getResourceById(identifier, accountKey) ?: return null
        val uploaded = current.copy(remoteId = remoteId, uri = uri, localUri = current.localUri ?: current.uri)
        insertResource(uploaded)
        return uploaded
    }

    suspend fun deleteResource(identifier: String) {
        io { delete("resources", "identifier = ?", arrayOf(identifier)) }
        changed()
    }
}

private fun Cursor.getStringOrNull(column: String): String? {
    val i = getColumnIndexOrThrow(column)
    return if (isNull(i)) null else getString(i)
}

private fun Cursor.getLongOrNull(column: String): Long? {
    val i = getColumnIndexOrThrow(column)
    return if (isNull(i)) null else getLong(i)
}
