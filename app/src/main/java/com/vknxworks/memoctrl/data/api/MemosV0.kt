package com.vknxworks.memoctrl.data.api

import com.vknxworks.memoctrl.data.model.MemoLocation
import com.vknxworks.memoctrl.data.model.MemoLocationChange
import com.vknxworks.memoctrl.data.model.MemoVisibility
import com.vknxworks.memoctrl.data.model.RemoteMemo
import com.vknxworks.memoctrl.data.model.RemoteResource
import com.vknxworks.memoctrl.data.model.User
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.time.Instant
import com.vknxworks.memoctrl.R
import com.vknxworks.memoctrl.str

// ---------- Memos 0.21 (the older api/v1 with numeric ids) ----------

@Serializable
enum class V0RowStatus {
    @SerialName("ROW_STATUS_UNSPECIFIED") ROW_STATUS_UNSPECIFIED,
    @SerialName("NORMAL") NORMAL,
    @SerialName("ARCHIVED") ARCHIVED,
    @SerialName("ACTIVE") ACTIVE,
}

@Serializable
data class V0UserSetting(val key: String = "", val value: String = "")

@Serializable
data class V0User(
    val id: Long,
    val createdTs: Long = 0,
    val username: String? = null,
    val nickname: String? = null,
    val name: String? = null,
    val avatarUrl: String? = null,
    val userSettingList: List<V0UserSetting>? = null,
) {
    val defaultVisibility: MemoVisibility
        get() {
            val raw = userSettingList?.firstOrNull { it.key == "memo-visibility" }?.value?.trim('"') ?: return MemoVisibility.PRIVATE
            return when (raw) {
                "PUBLIC" -> MemoVisibility.PUBLIC
                "PROTECTED" -> MemoVisibility.PROTECTED
                else -> MemoVisibility.PRIVATE
            }
        }
}

@Serializable
data class V0Resource(
    val id: Long,
    val createdTs: Long = 0,
    val filename: String = "",
    val type: String? = null,
    val externalLink: String? = null,
    val publicId: String? = null,
    val name: String? = null,
    val uid: String? = null,
)

@Serializable
data class V0Memo(
    val id: Long,
    val createdTs: Long = 0,
    val updatedTs: Long = 0,
    val creatorId: Long = 0,
    val creatorName: String? = null,
    val content: String = "",
    val pinned: Boolean = false,
    val rowStatus: V0RowStatus = V0RowStatus.NORMAL,
    val visibility: ApiVisibility = ApiVisibility.PRIVATE,
    val resourceList: List<V0Resource>? = null,
)

@Serializable
private data class V0CreateMemo(
    val content: String,
    val visibility: ApiVisibility? = null,
    val resourceIdList: List<Long>? = null,
    val createdTs: Long? = null,
)

@Serializable
private data class V0PatchMemo(
    val id: Long,
    val content: String? = null,
    val visibility: ApiVisibility? = null,
    val resourceIdList: List<Long>? = null,
    val rowStatus: V0RowStatus? = null,
)

@Serializable
private data class V0Organizer(val pinned: Boolean)

@Serializable
private data class V0Profile(val version: String = "")

@Serializable
private data class V0Status(val profile: V0Profile? = null)

@Serializable
private data class V0SignIn(val username: String, val password: String, val remember: Boolean = false)

@Serializable
private data class V0CreateToken(val description: String)

@Serializable
private data class V0AccessToken(val accessToken: String = "")

@Serializable
private data class V0CreateTokenResponse(val accessToken: V0AccessToken? = null)

class MemosV0(
    override val host: String,
    private val http: OkHttpClient,
    private val accessToken: String?,
) : MemosRemote {
    private val base: HttpUrl = host.toHttpUrl()

    override val supportsLocation: Boolean get() = false

    private fun request(url: HttpUrl): Request.Builder = Request.Builder().url(url).apply {
        if (!accessToken.isNullOrBlank()) header("Authorization", "Bearer $accessToken")
        else header(NO_SESSION_AUTH, "1")
        header("Accept", "application/json")
    }

    private fun fileUrl(r: V0Resource): String {
        if (!r.externalLink.isNullOrBlank()) return r.externalLink
        val b = base.newBuilder().addPathSegment("o").addPathSegment("r")
        when {
            !r.uid.isNullOrEmpty() -> b.addPathSegment(r.uid)
            !r.name.isNullOrEmpty() -> b.addPathSegment(r.name)
            !r.publicId.isNullOrEmpty() -> b.addPathSegment(r.id.toString()).addPathSegment(r.publicId)
            else -> b.addPathSegment(r.id.toString()).addPathSegment(r.filename)
        }
        return b.build().toString()
    }

    private fun convert(r: V0Resource) = RemoteResource(
        remoteId = r.id.toString(),
        date = Instant.ofEpochSecond(r.createdTs),
        filename = r.filename,
        uri = fileUrl(r),
        mimeType = r.type,
    )

    private fun convert(m: V0Memo) = RemoteMemo(
        remoteId = m.id.toString(),
        content = m.content,
        date = Instant.ofEpochSecond(m.createdTs),
        pinned = m.pinned,
        visibility = m.visibility.toModel(),
        resources = m.resourceList.orEmpty().map(::convert),
        archived = m.rowStatus == V0RowStatus.ARCHIVED,
        updatedAt = Instant.ofEpochSecond(m.updatedTs),
        creator = m.creatorName?.let { User(m.creatorId.toString(), it) },
    )

    override suspend fun serverVersion(): String = version().trim()

    suspend fun version(): String =
        http.fetchJson(request(base.path("api/v1/status").build()).build(), V0Status.serializer()).profile?.version.orEmpty()

    suspend fun me(): V0User = http.fetchJson(request(base.path("api/v1/user/me").build()).build(), V0User.serializer())

    override suspend fun currentUser(): User {
        val u = me()
        return User(
            identifier = u.id.toString(),
            name = u.nickname?.takeIf { it.isNotBlank() } ?: u.name?.takeIf { it.isNotBlank() } ?: u.username.orEmpty(),
            startDate = Instant.ofEpochSecond(u.createdTs),
            defaultVisibility = u.defaultVisibility,
            avatarUrl = u.avatarUrl,
        )
    }

    private suspend fun list(status: V0RowStatus): List<RemoteMemo> {
        val url = base.path("api/v1/memo").addQueryParameter("rowStatus", status.name).build()
        return http.fetchJson(request(url).build(), ListSerializer(V0Memo.serializer())).map(::convert)
    }

    override suspend fun listMemos() = list(V0RowStatus.NORMAL)
    override suspend fun listArchivedMemos() = list(V0RowStatus.ARCHIVED)

    override suspend fun listExploreMemos(pageSize: Int, pageToken: String?): Pair<List<RemoteMemo>, String?> {
        val offset = pageToken?.toIntOrNull() ?: 0
        val url = base.path("api/v1/memo/all")
            .addQueryParameter("limit", pageSize.toString())
            .addQueryParameter("offset", offset.toString())
            .build()
        val memos = http.fetchJson(request(url).build(), ListSerializer(V0Memo.serializer())).map(::convert)
        return memos to (if (memos.size < pageSize) null else (offset + pageSize).toString())
    }

    override suspend fun createMemo(
        content: String,
        visibility: MemoVisibility,
        resourceRemoteIds: List<String>,
        createdAt: Instant?,
        location: MemoLocation?,
    ): RemoteMemo {
        val body = V0CreateMemo(
            content = content,
            visibility = visibility.toApi(),
            resourceIdList = resourceRemoteIds.mapNotNull { it.toLongOrNull() },
            createdTs = createdAt?.epochSecond,
        )
        val req = request(base.path("api/v1/memo").build())
            .post(jsonBody(ApiJson.encodeToString(V0CreateMemo.serializer(), body)))
            .build()
        return convert(http.fetchJson(req, V0Memo.serializer()))
    }

    override suspend fun updateMemo(
        remoteId: String,
        content: String?,
        resourceRemoteIds: List<String>?,
        visibility: MemoVisibility?,
        pinned: Boolean?,
        archived: Boolean?,
        location: MemoLocationChange?,
    ): RemoteMemo {
        val id = remoteId.toLongOrNull() ?: throw ApiException(str(R.string.error_unknown_memo))
        var result: RemoteMemo? = null
        if (pinned != null) {
            val req = request(base.path("api/v1/memo").addPathSegment(id.toString()).addPathSegment("organizer").build())
                .post(jsonBody(ApiJson.encodeToString(V0Organizer.serializer(), V0Organizer(pinned))))
                .build()
            result = convert(http.fetchJson(req, V0Memo.serializer()))
        }
        if (content != null || visibility != null || resourceRemoteIds != null || archived != null) {
            val body = V0PatchMemo(
                id = id,
                content = content,
                visibility = visibility?.toApi(),
                resourceIdList = resourceRemoteIds?.mapNotNull { it.toLongOrNull() },
                rowStatus = archived?.let { if (it) V0RowStatus.ARCHIVED else V0RowStatus.NORMAL },
            )
            val req = request(base.path("api/v1/memo").addPathSegment(id.toString()).build())
                .patch(jsonBody(ApiJson.encodeToString(V0PatchMemo.serializer(), body)))
                .build()
            result = convert(http.fetchJson(req, V0Memo.serializer()))
        }
        return result ?: throw ApiException(str(R.string.error_nothing_to_change))
    }

    override suspend fun deleteMemo(remoteId: String) {
        http.fetchText(request(base.path("api/v1/memo").addPathSegment(remoteId).build()).delete().build())
    }

    override suspend fun createResource(
        filename: String,
        type: MediaType?,
        contentLength: Long?,
        openInputStream: () -> InputStream,
        memoRemoteId: String?,
    ): RemoteResource {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", filename, InputStreamRequestBody(type, contentLength, openInputStream))
            .build()
        val req = request(base.path("api/v1/resource/blob").build()).post(body).build()
        return convert(http.fetchJson(req, V0Resource.serializer()))
    }

    override suspend fun deleteResource(remoteId: String) {
        http.fetchText(request(base.path("api/v1/resource").addPathSegment(remoteId).build()).delete().build())
    }

    override fun memoUrl(remoteId: String): String =
        base.newBuilder().addPathSegment("m").addPathSegment(remoteId).build().toString()

    override suspend fun createTokenWithPassword(username: String, password: String, description: String): IssuedToken {
        // Memos 0.21 answers a password sign-in with a session cookie.
        val (body, headers) = http.fetchWithHeaders(
            Request.Builder().url(base.path("api/v1/auth/signin").build()).header(NO_SESSION_AUTH, "1")
                .header("Accept", "application/json")
                .post(jsonBody(ApiJson.encodeToString(V0SignIn.serializer(), V0SignIn(username, password))))
                .build(),
        )
        val session = headers.values("Set-Cookie").firstNotNullOfOrNull { cookie ->
            val (name, value) = cookie.substringBefore(';').split('=', limit = 2).let { it[0].trim() to it.getOrElse(1) { "" }.trim() }
            value.takeIf { name == "memos.access-token" && it.isNotEmpty() }
        } ?: throw ApiException(str(R.string.error_no_signin))
        val user = runCatching { ApiJson.decodeFromString(V0User.serializer(), body) }.getOrNull()
            ?: throw ApiException(str(R.string.error_not_memos))
        val created = http.fetchJson(
            Request.Builder()
                .url(base.path("api/v2/users").addPathSegment(user.id.toString()).addPathSegment("access_tokens").build())
                .header("Authorization", "Bearer $session")
                .header("Accept", "application/json")
                .post(jsonBody(ApiJson.encodeToString(V0CreateToken.serializer(), V0CreateToken(description))))
                .build(),
            V0CreateTokenResponse.serializer(),
        )
        val token = created.accessToken?.accessToken?.takeIf { it.isNotBlank() } ?: throw ApiException(str(R.string.error_no_token))
        return IssuedToken(token, "users/${user.id}")
    }

    override suspend fun revokeToken(tokenId: String) {
        val token = accessToken ?: return
        http.fetchText(
            request(base.path("api/v2").addPathSegments(tokenId).addPathSegment("access_tokens").addPathSegment(token).build()).delete().build(),
        )
    }
}
