package com.vknxworks.memoctrl.data.api

import com.vknxworks.memoctrl.data.model.MemoLocation
import com.vknxworks.memoctrl.data.model.MemoLocationChange
import com.vknxworks.memoctrl.data.model.MemoVisibility
import com.vknxworks.memoctrl.data.model.RemoteMemo
import com.vknxworks.memoctrl.data.model.RemoteResource
import com.vknxworks.memoctrl.data.model.User
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.time.Instant
import com.vknxworks.memoctrl.R
import com.vknxworks.memoctrl.str

// ---------- Memos 0.27 and newer (api/v1 with resource names) ----------

@Serializable
enum class V1State {
    @SerialName("STATE_UNSPECIFIED") STATE_UNSPECIFIED,
    @SerialName("NORMAL") NORMAL,
    @SerialName("ARCHIVED") ARCHIVED,
}

@Serializable
data class V1User(
    val name: String,
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    @Serializable(with = Rfc3339InstantSerializer::class)
    val createTime: Instant? = null,
)

@Serializable
private data class V1CurrentUser(val user: V1User? = null)

@Serializable
private data class V1GeneralSetting(val memoVisibility: ApiVisibility? = null)

@Serializable
private data class V1UserSetting(val generalSetting: V1GeneralSetting? = null)

@Serializable
data class V1Attachment(
    val name: String? = null,
    @Serializable(with = Rfc3339InstantSerializer::class)
    val createTime: Instant? = null,
    val filename: String? = null,
    val externalLink: String? = null,
    val type: String? = null,
    val size: String? = null,
    val memo: String? = null,
)

@Serializable
data class V1Memo(
    val name: String,
    val state: V1State? = null,
    val creator: String? = null,
    @Serializable(with = Rfc3339InstantSerializer::class)
    val createTime: Instant? = null,
    @Serializable(with = Rfc3339InstantSerializer::class)
    val updateTime: Instant? = null,
    val content: String? = null,
    val visibility: ApiVisibility? = null,
    val pinned: Boolean? = null,
    val attachments: List<V1Attachment>? = null,
    val location: MemoLocation? = null,
)

@Serializable
private data class V1ListMemos(val memos: List<V1Memo> = emptyList(), val nextPageToken: String? = null)

@Serializable
private data class V1CreateMemo(
    val content: String,
    val visibility: ApiVisibility? = null,
    val attachments: List<V1Attachment>? = null,
    @Serializable(with = Rfc3339InstantSerializer::class)
    val createTime: Instant? = null,
    val location: MemoLocation? = null,
)

@Serializable
private data class V1UpdateMemo(
    val content: String? = null,
    val visibility: ApiVisibility? = null,
    val state: V1State? = null,
    val pinned: Boolean? = null,
    @Serializable(with = Rfc3339InstantSerializer::class)
    val updateTime: Instant? = null,
    val attachments: List<V1Attachment>? = null,
    val location: MemoLocation? = null,
) {
    /** Absent fields are left out of the JSON, so the mask names what to change (and clears a removed location). */
    fun updateMask(includeLocation: Boolean): String = buildList {
        if (content != null) add("content")
        if (visibility != null) add("visibility")
        if (state != null) add("state")
        if (pinned != null) add("pinned")
        if (updateTime != null) add("update_time")
        if (attachments != null) add("attachments")
        if (includeLocation) add("location")
    }.joinToString(",")
}

@Serializable
data class V1Profile(val version: String = "")

@Serializable
private data class V1PasswordCredentials(val username: String, val password: String)

@Serializable
private data class V1SignIn(val passwordCredentials: V1PasswordCredentials)

@Serializable
private data class V1SignInResponse(val user: V1User? = null, val accessToken: String? = null)

@Serializable
private data class V1CreateToken(val description: String, val expiresInDays: Int = 0)

@Serializable
private data class V1TokenInfo(val name: String? = null)

@Serializable
private data class V1CreateTokenResponse(val personalAccessToken: V1TokenInfo? = null, val token: String? = null)

class MemosV1(
    override val host: String,
    private val http: OkHttpClient,
    private val accessToken: String?,
    /** "users/1"; empty while signing in, before the user is known. */
    private val userName: String = "",
) : MemosRemote {
    private val base: HttpUrl = host.toHttpUrl()

    override val supportsLocation: Boolean get() = true

    private fun request(url: HttpUrl): Request.Builder = Request.Builder().url(url).apply {
        if (!accessToken.isNullOrBlank()) header("Authorization", "Bearer $accessToken")
        else header(NO_SESSION_AUTH, "1")
        header("Accept", "application/json")
    }

    /** "memos/abc" (or "memos/abc|…") to "abc". */
    private fun idOf(name: String) = name.substringBefore('|').substringAfterLast('/')
    private fun nameOf(identifier: String) = identifier.substringBefore('|')

    private fun fileUrl(a: V1Attachment): String {
        if (!a.externalLink.isNullOrBlank()) return a.externalLink
        return base.newBuilder()
            .addPathSegment("file")
            .addPathSegments(a.name.orEmpty())
            .addPathSegment(a.filename.orEmpty())
            .build().toString()
    }

    private fun convert(a: V1Attachment) = RemoteResource(
        remoteId = a.name ?: throw ApiException(str(R.string.error_attachment_without_name)),
        date = a.createTime ?: Instant.now(),
        filename = a.filename.orEmpty(),
        uri = fileUrl(a),
        mimeType = a.type,
    )

    private fun convert(m: V1Memo) = RemoteMemo(
        remoteId = m.name,
        content = m.content.orEmpty(),
        date = m.createTime ?: Instant.now(),
        pinned = m.pinned ?: false,
        visibility = m.visibility.toModel(),
        resources = m.attachments.orEmpty().filter { it.name != null }.map(::convert),
        archived = m.state == V1State.ARCHIVED,
        updatedAt = m.updateTime,
        location = m.location,
    )

    suspend fun profile(): V1Profile =
        http.fetchJson(request(base.path("api/v1/instance/profile").build()).build(), V1Profile.serializer())

    override suspend fun serverVersion(): String = profile().version.trim()

    suspend fun me(): V1User =
        http.fetchJson(request(base.path("api/v1/auth/me").build()).build(), V1CurrentUser.serializer()).user
            ?: throw ApiException(str(R.string.error_not_signed_in), 401)

    override suspend fun currentUser(): User {
        val user = me()
        val visibility = runCatching {
            http.fetchJson(
                request(base.path("api/v1/users/${idOf(user.name)}/settings/GENERAL").build()).build(),
                V1UserSetting.serializer(),
            ).generalSetting?.memoVisibility
        }.getOrNull()
        return User(
            identifier = user.name,
            name = user.displayName?.takeIf { it.isNotBlank() } ?: user.username,
            startDate = user.createTime ?: Instant.now(),
            defaultVisibility = visibility.toModel(),
            avatarUrl = user.avatarUrl,
        )
    }

    private suspend fun page(pageSize: Int, pageToken: String?, state: V1State?, filter: String?): V1ListMemos {
        val url = base.path("api/v1/memos").apply {
            addQueryParameter("pageSize", pageSize.toString())
            if (!pageToken.isNullOrEmpty()) addQueryParameter("pageToken", pageToken)
            if (state != null) addQueryParameter("state", state.name)
            if (filter != null) addQueryParameter("filter", filter)
        }.build()
        return http.fetchJson(request(url).build(), V1ListMemos.serializer())
    }

    private suspend fun listAll(state: V1State): List<RemoteMemo> {
        val out = ArrayList<RemoteMemo>()
        var token: String? = null
        do {
            val p = page(PAGE_SIZE, token, state, "creator == \"$userName\"")
            out += p.memos.map(::convert)
            token = p.nextPageToken?.takeIf { it.isNotEmpty() }
        } while (token != null)
        return out
    }

    override suspend fun listMemos() = listAll(V1State.NORMAL)
    override suspend fun listArchivedMemos() = listAll(V1State.ARCHIVED)

    override suspend fun listExploreMemos(pageSize: Int, pageToken: String?): Pair<List<RemoteMemo>, String?> {
        val p = page(pageSize, pageToken, null, "visibility in [\"PUBLIC\", \"PROTECTED\"]")
        val creators = p.memos.mapNotNull { it.creator }.toSet()
        val users = coroutineScope {
            creators.map { creator ->
                async {
                    runCatching {
                        http.fetchJson(request(base.path("api/v1/users/${idOf(creator)}").build()).build(), V1User.serializer())
                    }.getOrNull()
                }
            }.awaitAll()
        }.filterNotNull().associateBy { it.name }
        val memos = p.memos.map { m ->
            convert(m).copy(
                creator = m.creator?.let { c ->
                    users[c]?.let { u -> User(u.name, u.displayName?.takeIf { it.isNotBlank() } ?: u.username, u.createTime ?: Instant.now()) }
                },
            )
        }
        return memos to p.nextPageToken?.takeIf { it.isNotEmpty() }
    }

    override suspend fun createMemo(
        content: String,
        visibility: MemoVisibility,
        resourceRemoteIds: List<String>,
        createdAt: Instant?,
        location: MemoLocation?,
    ): RemoteMemo {
        val body = V1CreateMemo(
            content = content,
            visibility = visibility.toApi(),
            attachments = resourceRemoteIds.map { V1Attachment(name = nameOf(it)) },
            createTime = createdAt,
            location = location,
        )
        val req = request(base.path("api/v1/memos").build())
            .post(jsonBody(ApiJson.encodeToString(V1CreateMemo.serializer(), body)))
            .build()
        return convert(http.fetchJson(req, V1Memo.serializer()))
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
        val body = V1UpdateMemo(
            content = content,
            visibility = visibility?.toApi(),
            state = archived?.let { if (it) V1State.ARCHIVED else V1State.NORMAL },
            pinned = pinned,
            updateTime = Instant.now(),
            attachments = resourceRemoteIds?.map { V1Attachment(name = nameOf(it)) },
            location = location?.value,
        )
        val url = base.path("api/v1/memos").addPathSegment(idOf(remoteId))
            .addQueryParameter("updateMask", body.updateMask(includeLocation = location != null))
            .build()
        val req = request(url).patch(jsonBody(ApiJson.encodeToString(V1UpdateMemo.serializer(), body))).build()
        return convert(http.fetchJson(req, V1Memo.serializer()))
    }

    override suspend fun deleteMemo(remoteId: String) {
        http.fetchText(request(base.path("api/v1/memos").addPathSegment(idOf(remoteId)).build()).delete().build())
    }

    override suspend fun createResource(
        filename: String,
        type: MediaType?,
        contentLength: Long?,
        openInputStream: () -> InputStream,
        memoRemoteId: String?,
    ): RemoteResource {
        val body = StreamingBase64JsonRequestBody(
            filename = filename,
            type = type?.toString() ?: "application/octet-stream",
            memo = memoRemoteId?.let(::nameOf),
            rawLength = contentLength,
            openInputStream = openInputStream,
        )
        val req = request(base.path("api/v1/attachments").build()).post(body).build()
        return convert(http.fetchJson(req, V1Attachment.serializer()))
    }

    override suspend fun deleteResource(remoteId: String) {
        http.fetchText(request(base.path("api/v1/attachments").addPathSegment(idOf(remoteId)).build()).delete().build())
    }

    override fun memoUrl(remoteId: String): String =
        base.newBuilder().addPathSegments(nameOf(remoteId)).build().toString()

    override suspend fun createTokenWithPassword(username: String, password: String, description: String): IssuedToken {
        val signIn = http.fetchJson(
            Request.Builder().url(base.path("api/v1/auth/signin").build()).header(NO_SESSION_AUTH, "1")
                .header("Accept", "application/json")
                .post(jsonBody(ApiJson.encodeToString(V1SignIn.serializer(), V1SignIn(V1PasswordCredentials(username, password)))))
                .build(),
            V1SignInResponse.serializer(),
        )
        val session = signIn.accessToken?.takeIf { it.isNotBlank() } ?: throw ApiException(str(R.string.error_no_signin))
        val user = signIn.user ?: throw ApiException(str(R.string.error_no_signin))
        try {
            val created = http.fetchJson(
                Request.Builder()
                    .url(base.path("api/v1").addPathSegments(user.name).addPathSegment("personalAccessTokens").build())
                    .header("Authorization", "Bearer $session")
                    .header("Accept", "application/json")
                    .post(jsonBody(ApiJson.encodeToString(V1CreateToken.serializer(), V1CreateToken(description))))
                    .build(),
                V1CreateTokenResponse.serializer(),
            )
            val token = created.token?.takeIf { it.isNotBlank() } ?: throw ApiException(str(R.string.error_no_token))
            return IssuedToken(token, created.personalAccessToken?.name)
        } finally {
            // The short sign-in session isn't needed anymore: the app uses its own token from now on.
            runCatching {
                http.fetchText(
                    Request.Builder().url(base.path("api/v1/auth/signout").build())
                        .header("Authorization", "Bearer $session")
                        .post(jsonBody("{}"))
                        .build(),
                )
            }
        }
    }

    override suspend fun revokeToken(tokenId: String) {
        http.fetchText(request(base.path("api/v1").addPathSegments(tokenId).build()).delete().build())
    }

    companion object {
        private const val PAGE_SIZE = 200
    }
}
