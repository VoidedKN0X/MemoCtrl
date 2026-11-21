package com.vknxworks.memoctrl.data.api

import android.util.Base64
import android.util.Base64OutputStream
import com.vknxworks.memoctrl.data.model.MemoLocation
import com.vknxworks.memoctrl.data.model.MemoLocationChange
import com.vknxworks.memoctrl.data.model.MemoVisibility
import com.vknxworks.memoctrl.data.model.LinkPreview
import com.vknxworks.memoctrl.data.model.RemoteMemo
import com.vknxworks.memoctrl.data.model.RemoteReaction
import com.vknxworks.memoctrl.data.model.RemoteRelation
import com.vknxworks.memoctrl.data.model.SavedView
import com.vknxworks.memoctrl.data.model.RemoteResource
import com.vknxworks.memoctrl.data.model.User
import kotlinx.serialization.builtins.serializer
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

/** Everything the app asks of a Memos server. One implementation per API generation. */
interface MemosRemote {
    /** The server address the client talks to, without a trailing slash. */
    val host: String

    /** The Memos version the server reports, e.g. "0.31.0". */
    suspend fun serverVersion(): String

    suspend fun currentUser(): User
    suspend fun listMemos(): List<RemoteMemo>
    suspend fun listArchivedMemos(): List<RemoteMemo>

    /** Memos others shared with everyone on the server, newest first, a page at a time. */
    suspend fun listExploreMemos(pageSize: Int, pageToken: String?): Pair<List<RemoteMemo>, String?>

    suspend fun createMemo(
        content: String,
        visibility: MemoVisibility,
        resourceRemoteIds: List<String>,
        createdAt: Instant? = null,
        location: MemoLocation? = null,
    ): RemoteMemo

    suspend fun updateMemo(
        remoteId: String,
        content: String? = null,
        resourceRemoteIds: List<String>? = null,
        visibility: MemoVisibility? = null,
        pinned: Boolean? = null,
        archived: Boolean? = null,
        location: MemoLocationChange? = null,
    ): RemoteMemo

    suspend fun deleteMemo(remoteId: String)

    suspend fun createResource(
        filename: String,
        type: MediaType?,
        contentLength: Long?,
        openInputStream: () -> InputStream,
        memoRemoteId: String? = null,
    ): RemoteResource

    suspend fun deleteResource(remoteId: String)

    /** Whether memos can carry a location on this server. */
    val supportsLocation: Boolean

    /** Address of a memo on the server's own website, to share. */
    fun memoUrl(remoteId: String): String

    /**
     * Signs in once with a username and password and has the server make a never-expiring access
     * token for this app. The password is used for this one sign-in only and never kept.
     */
    suspend fun createTokenWithPassword(username: String, password: String, description: String): IssuedToken

    /** Revokes an access token this app made, when signing out. */
    suspend fun revokeToken(tokenId: String)

    /**
     * Renames a tag in all of the user's memos on the server with one request, where the server has
     * one (Memos 0.21). False when it doesn't, and the app rewrites each memo itself instead.
     */
    suspend fun renameTag(oldTag: String, newTag: String): Boolean = false

    /** Removes a tag from the server's own list of tags, where it keeps one (Memos 0.21). Memos aren't changed. */
    suspend fun deleteTag(tag: String) = Unit

    // ---------- Only on Memos 0.27 and newer. Everything below says "not on this server" elsewhere. ----------

    /** Comments, reactions, links between memos, saved views and live updates exist on this server. */
    val supportsSocial: Boolean get() = false

    /** One memo by its server id, with its reactions and relations. Also memos of other people you may read. */
    suspend fun getMemo(remoteId: String): RemoteMemo = unsupported()

    /** Whether the signed-in user is an administrator, who may change other people's memos. */
    suspend fun isAdmin(): Boolean = false

    suspend fun listComments(memoRemoteId: String): List<RemoteMemo> = unsupported()
    suspend fun createComment(memoRemoteId: String, content: String, visibility: MemoVisibility): RemoteMemo = unsupported()
    suspend fun listReactions(memoRemoteId: String): List<RemoteReaction> = unsupported()
    suspend fun addReaction(memoRemoteId: String, type: String): RemoteReaction = unsupported()
    suspend fun deleteReaction(reactionName: String): Unit = unsupported()

    /** The emoji this server offers as reactions. */
    suspend fun reactionChoices(): List<String> = emptyList()

    /** Links from and to a memo: references in both directions, and comments. */
    suspend fun listRelations(memoRemoteId: String): List<RemoteRelation> = unsupported()

    /** Replaces the memos this memo refers to. Only the author of the memo (or an administrator) may. */
    suspend fun setReferences(memoRemoteId: String, relatedRemoteIds: List<String>): Unit = unsupported()

    suspend fun listViews(): List<SavedView> = unsupported()
    suspend fun createView(title: String, filter: String): SavedView = unsupported()
    suspend fun updateView(name: String, title: String, filter: String): SavedView = unsupported()
    suspend fun deleteView(name: String): Unit = unsupported()

    /** Your own memos that match a saved view's [filter], which the server works out, a page at a time. */
    suspend fun listMemosByFilter(filter: String, pageSize: Int, pageToken: String?): Pair<List<RemoteMemo>, String?> = unsupported()

    /**
     * Asks the server to look up a link's title, description and picture (Memos 0.29 and newer). The
     * phone never contacts the link itself. Null when this server can't.
     */
    suspend fun linkPreview(url: String): LinkPreview? = null

    /**
     * Stays connected to the server and calls [onChange] every time it says a memo, comment or reaction
     * changed. Returns when the server closes the connection; throws when it can't connect.
     */
    suspend fun watchChanges(onConnected: () -> Unit, onChange: () -> Unit): Unit = unsupported()
}

private fun unsupported(): Nothing =
    throw ApiException(com.vknxworks.memoctrl.str(com.vknxworks.memoctrl.R.string.error_server_too_old))

/**
 * The "server" of a local-only account: there is none. Memos only live in the phone's database, the
 * repository never syncs it, and anything that would need a server says so.
 */
class LocalRemote : MemosRemote {
    override val host: String = LOCAL_HOST
    override val supportsLocation: Boolean get() = true

    private fun none(): Nothing = throw ApiException(com.vknxworks.memoctrl.str(com.vknxworks.memoctrl.R.string.error_local_only))

    override suspend fun serverVersion(): String = ""
    override suspend fun currentUser(): User = none()
    override suspend fun listMemos(): List<RemoteMemo> = emptyList()
    override suspend fun listArchivedMemos(): List<RemoteMemo> = emptyList()
    override suspend fun listExploreMemos(pageSize: Int, pageToken: String?): Pair<List<RemoteMemo>, String?> = none()
    override suspend fun createMemo(
        content: String,
        visibility: MemoVisibility,
        resourceRemoteIds: List<String>,
        createdAt: Instant?,
        location: MemoLocation?,
    ): RemoteMemo = none()
    override suspend fun updateMemo(
        remoteId: String,
        content: String?,
        resourceRemoteIds: List<String>?,
        visibility: MemoVisibility?,
        pinned: Boolean?,
        archived: Boolean?,
        location: MemoLocationChange?,
    ): RemoteMemo = none()
    override suspend fun deleteMemo(remoteId: String): Unit = none()
    override suspend fun createResource(
        filename: String,
        type: MediaType?,
        contentLength: Long?,
        openInputStream: () -> InputStream,
        memoRemoteId: String?,
    ): RemoteResource = none()
    override suspend fun deleteResource(remoteId: String): Unit = none()
    override fun memoUrl(remoteId: String): String = ""
    override suspend fun createTokenWithPassword(username: String, password: String, description: String): IssuedToken = none()
    override suspend fun revokeToken(tokenId: String) = Unit

    companion object {
        /** Stands in for the server address of local-only accounts. Never contacted. */
        const val LOCAL_HOST = "local"
    }
}

/** An access token the server made for the app. [id] is what revokes it later. */
data class IssuedToken(val token: String, val id: String?)

/** Streams a file as the body of a request without reading it into memory first. */
class InputStreamRequestBody(
    private val mediaType: MediaType?,
    private val length: Long?,
    private val openInputStream: () -> InputStream,
) : RequestBody() {
    override fun contentType(): MediaType? = mediaType
    override fun contentLength(): Long = length ?: -1L
    override fun writeTo(sink: BufferedSink) {
        openInputStream().use { sink.writeAll(it.source()) }
    }
}

/**
 * The JSON body Memos expects for a new attachment, `{"filename", "type", "content": base64, "memo"}`,
 * written straight from the file so large attachments never sit in memory as one string.
 */
class StreamingBase64JsonRequestBody(
    filename: String,
    type: String,
    memo: String?,
    private val rawLength: Long?,
    private val openInputStream: () -> InputStream,
) : RequestBody() {
    private val prefix: String = buildString {
        append("{\"filename\":")
        append(ApiJson.encodeToString(String.serializer(), filename))
        append(",\"type\":")
        append(ApiJson.encodeToString(String.serializer(), type))
        append(",\"content\":\"")
    }
    private val suffix: String = buildString {
        append('"')
        if (memo != null) {
            append(",\"memo\":")
            append(ApiJson.encodeToString(String.serializer(), memo))
        }
        append('}')
    }

    override fun contentType(): MediaType = "application/json; charset=utf-8".toMediaType()

    override fun contentLength(): Long {
        val raw = rawLength ?: return -1L
        if (raw < 0) return -1L
        val encoded = ((raw + 2L) / 3L) * 4L
        return prefix.encodeToByteArray().size + encoded + suffix.encodeToByteArray().size
    }

    override fun writeTo(sink: BufferedSink) {
        sink.writeUtf8(prefix)
        openInputStream().use { input ->
            Base64OutputStream(NoCloseOutputStream(sink.outputStream()), Base64.NO_WRAP).use { input.copyTo(it) }
        }
        sink.writeUtf8(suffix)
    }
}

private class NoCloseOutputStream(private val delegate: OutputStream) : OutputStream() {
    override fun write(b: Int) = delegate.write(b)
    override fun write(b: ByteArray) = delegate.write(b)
    override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)
    override fun flush() = delegate.flush()
    override fun close() = flush()
}
