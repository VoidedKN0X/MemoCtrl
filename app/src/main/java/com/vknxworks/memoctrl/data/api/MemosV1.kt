package com.vknxworks.memoctrl.data.api

import com.vknxworks.memoctrl.data.model.MemoLocation
import com.vknxworks.memoctrl.data.model.MemoLocationChange
import com.vknxworks.memoctrl.data.model.MemoVisibility
import com.vknxworks.memoctrl.data.model.RemoteMemo
import com.vknxworks.memoctrl.data.model.RemoteResource
import com.vknxworks.memoctrl.data.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
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
    /** The Memos version this account last saw on the server ("0.31.0", "26.10.0"), when there is one. */
    versionHint: String? = null,
) : MemosRemote {
    private val base: HttpUrl = host.toHttpUrl()

    /** What [versionHint] says about the API's root: true for "api/…", false for "api/v1/…", null for no idea. */
    private val hintUnversioned: Boolean? = unversionedSince(versionHint)

    override val supportsLocation: Boolean get() = true

    private fun request(url: HttpUrl): Request.Builder = Request.Builder().url(url).apply {
        if (!accessToken.isNullOrBlank()) header("Authorization", "Bearer $accessToken")
        else header(NO_SESSION_AUTH, "1")
        header("Accept", "application/json")
    }

    /**
     * The API's root on this server: "api/v1" (Memos 0.27 to 0.31, and the default), or "api" once it is
     * known that this server has the API there (Memos 26.10 and newer dropped the "v1" part).
     */
    private fun apiRoot(): HttpUrl.Builder = base.path(if (routes[host] == true) UNVERSIONED_ROOT else VERSIONED_ROOT)

    private fun api(path: String): HttpUrl.Builder = apiRoot().addPathSegments(path)

    /**
     * Asks for the server's profile under [root] without any token. Only a profile that names a version
     * counts; everything else is an [ApiException] (with the server's status when it answered with one).
     */
    private suspend fun probeProfile(root: String): V1Profile {
        val profile = http.fetchJson(
            Request.Builder().url(base.path("$root/instance/profile").build())
                .header(NO_SESSION_AUTH, "1")
                .header("Accept", "application/json")
                .build(),
            V1Profile.serializer(),
        )
        if (profile.version.isBlank()) throw ApiException(str(R.string.error_not_memos))
        return profile
    }

    /**
     * Finds out, without any token, which of the two roots has this server's profile, and remembers it.
     * The second root is only asked when the first one answered 404, 410 or something that isn't a Memos
     * profile. Throws what the server answered when neither root has a profile, or when the first one
     * failed in another way (the server is down, for example); nothing is remembered then.
     */
    private suspend fun determineRoutes(unversionedFirst: Boolean) {
        val first = if (unversionedFirst) UNVERSIONED_ROOT else VERSIONED_ROOT
        val second = if (unversionedFirst) VERSIONED_ROOT else UNVERSIONED_ROOT
        try {
            probeProfile(first)
            routes[host] = unversionedFirst
            return
        } catch (e: ApiException) {
            // Status 0 is an answer that wasn't a Memos profile.
            if (e.status != 404 && e.status != 410 && e.status != 0) throw e
        }
        probeProfile(second)
        routes[host] = !unversionedFirst
    }

    /**
     * Makes sure it is known where this server's API is before a request goes out, in this order:
     * 1. What was already found out for this server since the app started is used as it is.
     * 2. Otherwise the Memos version the account last saw decides. Older than 26.10: "api/v1/…", as always,
     *    with no extra request. 26.10 or newer: "api/…", but only after a request without the token has
     *    shown that the profile really is there (once per app start), so the token never goes to a route
     *    that hasn't been seen to exist.
     * 3. With no version to go by, one request without the token looks for the profile on "api/v1/…" and,
     *    if it isn't there, on "api/…". The profile request itself ([isProfile]) skips this: it is that
     *    check already. If the check can't tell, "api/v1/…" is used as before and nothing is remembered.
     */
    private suspend fun ensureRoutes(isProfile: Boolean) {
        if (routes.containsKey(host)) return
        if (hintUnversioned == false) return
        if (hintUnversioned == null && isProfile) return
        routesMutex.withLock {
            if (routes.containsKey(host)) return
            if (hintUnversioned == true) {
                determineRoutes(unversionedFirst = true)
            } else {
                try {
                    determineRoutes(unversionedFirst = false)
                } catch (e: ApiException) {
                    // Can't tell: the request goes to "api/v1/…" like it always did and reports what's wrong.
                }
            }
        }
    }

    /**
     * Runs a request built with [api] / [apiRoot], after [ensureRoutes] has settled where the API is.
     * Every supported Memos version (0.27 to 0.31) answers on "api/v1/…", so that stays the default. Memos
     * 26.10 moved the same routes to "api/…" and answers 410 Gone on the old ones; a later Memos removes
     * the old ones altogether (404). As a last resort for a server that changed after the choice was made:
     * when a request on "api/v1/…" answers 410, or the profile request ([isProfile]) answers 404 or 410 on
     * either root, and a request without the token finds the server's profile on the other root, the same
     * request is sent once more there and the choice is remembered for this server until the app is
     * restarted. A 404 on any other request is a memo or file that isn't there, and is left alone.
     * [call] builds its request anew each time it runs.
     */
    private suspend fun <T> routed(isProfile: Boolean = false, call: suspend () -> T): T {
        ensureRoutes(isProfile)
        val wasUnversioned = routes[host] == true
        try {
            return call()
        } catch (e: ApiException) {
            val gone = e.status == 410 && !wasUnversioned
            val profileMissing = isProfile && (e.status == 404 || e.status == 410)
            if (!gone && !profileMissing) throw e
            if ((routes[host] == true) == wasUnversioned) {
                val confirmed = try {
                    probeProfile(if (wasUnversioned) VERSIONED_ROOT else UNVERSIONED_ROOT)
                    true
                } catch (c: CancellationException) {
                    throw c
                } catch (x: Exception) {
                    false
                }
                if (!confirmed) throw e
                routes[host] = !wasUnversioned
            }
        }
        return call()
    }

    /** "memos/abc" (or "memos/abc|…") to "abc". */
    private fun idOf(name: String) = name.substringBefore('|').substringAfterLast('/')
    private fun nameOf(identifier: String) = identifier.substringBefore('|')

    private fun fileUrl(a: V1Attachment): String {
        // Only a web address counts as an external link: a file: or content: address from the server must
        // never make the app open or show a file on the phone.
        a.externalLink?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }?.let { return it }
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

    suspend fun profile(): V1Profile {
        val profile = routed(isProfile = true) {
            http.fetchJson(request(api("instance/profile").build()).build(), V1Profile.serializer())
        }
        // The root that answered with a real profile is the one to keep using for this server.
        if (profile.version.isNotBlank()) routes.putIfAbsent(host, false)
        return profile
    }

    override suspend fun serverVersion(): String = profile().version.trim()

    suspend fun me(): V1User =
        routed { http.fetchJson(request(api("auth/me").build()).build(), V1CurrentUser.serializer()) }.user
            ?: throw ApiException(str(R.string.error_not_signed_in), 401)

    override suspend fun currentUser(): User {
        val user = me()
        val visibility = runCatching {
            routed {
                http.fetchJson(
                    request(api("users/${idOf(user.name)}/settings/GENERAL").build()).build(),
                    V1UserSetting.serializer(),
                )
            }.generalSetting?.memoVisibility
        }.getOrNull()
        return User(
            identifier = user.name,
            name = user.displayName?.takeIf { it.isNotBlank() } ?: user.username,
            startDate = user.createTime ?: Instant.now(),
            defaultVisibility = visibility.toModel(),
            avatarUrl = user.avatarUrl,
        )
    }

    private suspend fun page(pageSize: Int, pageToken: String?, state: V1State?, filter: String?): V1ListMemos = routed {
        val url = api("memos").apply {
            addQueryParameter("pageSize", pageSize.toString())
            if (!pageToken.isNullOrEmpty()) addQueryParameter("pageToken", pageToken)
            if (state != null) addQueryParameter("state", state.name)
            if (filter != null) addQueryParameter("filter", filter)
        }.build()
        http.fetchJson(request(url).build(), V1ListMemos.serializer())
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
                        routed { http.fetchJson(request(api("users/${idOf(creator)}").build()).build(), V1User.serializer()) }
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
        val json = ApiJson.encodeToString(V1CreateMemo.serializer(), body)
        return convert(routed { http.fetchJson(request(api("memos").build()).post(jsonBody(json)).build(), V1Memo.serializer()) })
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
        val json = ApiJson.encodeToString(V1UpdateMemo.serializer(), body)
        return convert(
            routed {
                val url = api("memos").addPathSegment(idOf(remoteId))
                    .addQueryParameter("updateMask", body.updateMask(includeLocation = location != null))
                    .build()
                http.fetchJson(request(url).patch(jsonBody(json)).build(), V1Memo.serializer())
            },
        )
    }

    override suspend fun deleteMemo(remoteId: String) {
        routed { http.fetchText(request(api("memos").addPathSegment(idOf(remoteId)).build()).delete().build()) }
    }

    override suspend fun createResource(
        filename: String,
        type: MediaType?,
        contentLength: Long?,
        openInputStream: () -> InputStream,
        memoRemoteId: String?,
    ): RemoteResource {
        return convert(
            routed {
                val body = StreamingBase64JsonRequestBody(
                    filename = filename,
                    type = type?.toString() ?: "application/octet-stream",
                    memo = memoRemoteId?.let(::nameOf),
                    rawLength = contentLength,
                    openInputStream = openInputStream,
                )
                http.fetchJson(request(api("attachments").build()).post(body).build(), V1Attachment.serializer())
            },
        )
    }

    override suspend fun deleteResource(remoteId: String) {
        routed { http.fetchText(request(api("attachments").addPathSegment(idOf(remoteId)).build()).delete().build()) }
    }

    override fun memoUrl(remoteId: String): String =
        base.newBuilder().addPathSegments(nameOf(remoteId)).build().toString()

    override suspend fun createTokenWithPassword(username: String, password: String, description: String): IssuedToken {
        val signIn = routed {
            http.fetchJson(
                Request.Builder().url(api("auth/signin").build()).header(NO_SESSION_AUTH, "1")
                    .header("Accept", "application/json")
                    .post(jsonBody(ApiJson.encodeToString(V1SignIn.serializer(), V1SignIn(V1PasswordCredentials(username, password)))))
                    .build(),
                V1SignInResponse.serializer(),
            )
        }
        val session = signIn.accessToken?.takeIf { it.isNotBlank() } ?: throw ApiException(str(R.string.error_no_signin))
        val user = signIn.user ?: throw ApiException(str(R.string.error_no_signin))
        try {
            val created = routed {
                http.fetchJson(
                    Request.Builder()
                        .url(apiRoot().addPathSegments(user.name).addPathSegment("personalAccessTokens").build())
                        .header("Authorization", "Bearer $session")
                        .header("Accept", "application/json")
                        .post(jsonBody(ApiJson.encodeToString(V1CreateToken.serializer(), V1CreateToken(description))))
                        .build(),
                    V1CreateTokenResponse.serializer(),
                )
            }
            val token = created.token?.takeIf { it.isNotBlank() } ?: throw ApiException(str(R.string.error_no_token))
            return IssuedToken(token, created.personalAccessToken?.name)
        } finally {
            // The short sign-in session isn't needed anymore: the app uses its own token from now on.
            runCatching {
                routed {
                    http.fetchText(
                        Request.Builder().url(api("auth/signout").build())
                            .header("Authorization", "Bearer $session")
                            .post(jsonBody("{}"))
                            .build(),
                    )
                }
            }
        }
    }

    override suspend fun revokeToken(tokenId: String) {
        routed { http.fetchText(request(apiRoot().addPathSegments(tokenId).build()).delete().build()) }
    }

    companion object {
        private const val PAGE_SIZE = 200

        private const val VERSIONED_ROOT = "api/v1"
        private const val UNVERSIONED_ROOT = "api"

        /**
         * Where each server's API was found since the app started: true for "api/…", false for "api/v1/…".
         * A server that isn't in here hasn't been settled yet. Kept in memory only.
         */
        private val routes = ConcurrentHashMap<String, Boolean>()

        /** One check for the API's root at a time, so requests that start together share its result. */
        private val routesMutex = Mutex()

        /** Forgets where [host]'s API was found, so it is looked up again (signing in to a server). */
        fun forgetRoutes(host: String) {
            routes.remove(host)
        }

        /**
         * Whether a Memos [version] has the API on "api/…" (26.10 and newer, calendar versions like "26.10.0",
         * "26.10-rc.1" or "26.11.3-dev.abc1234") instead of "api/v1/…" ("0.31.0" and older). Null when
         * [version] isn't a version number.
         */
        fun unversionedSince(version: String?): Boolean? {
            val parts = version?.trim()?.removePrefix("v")?.split('.', '-', '+') ?: return null
            val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: return null
            return major > 26 || (major == 26 && minor >= 10)
        }
    }
}
