package com.vknxworks.memoctrl.data.api

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val ApiJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}

/** A real answer from the server that isn't a success: a wrong token, a missing memo, a rejected change. */
class ApiException(message: String, val status: Int = 0) : IOException(message) {
    val isUnauthorized: Boolean get() = status == 401 || status == 403
}

/** The saved access token no longer works. The user has to sign in again with a new one. */
class AccessTokenInvalidException :
    IOException("Your access token no longer works. Create a new one in Memos (Settings > Access tokens) and sign in again.")

@Serializable
private data class ErrorBody(val message: String? = null, val error: String? = null)

private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

fun jsonBody(json: String): RequestBody = json.toRequestBody(JSON_TYPE)

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
}

/**
 * Runs [request] and returns the body text. Error answers become [ApiException] with the server's
 * own message when it sent one.
 */
suspend fun OkHttpClient.fetchText(request: Request): String = fetchWithHeaders(request).first

/** Like [fetchText], and also hands back the answer's headers (for sign-in cookies). */
suspend fun OkHttpClient.fetchWithHeaders(request: Request): Pair<String, okhttp3.Headers> {
    newCall(request).await().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val message = runCatching { ApiJson.decodeFromString(ErrorBody.serializer(), text) }.getOrNull()
                ?.let { it.message ?: it.error }
                ?.takeIf { it.isNotBlank() }
                ?: "Server answered ${response.code}${response.message.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()}"
            throw ApiException(message, response.code)
        }
        return text to response.headers
    }
}

suspend fun <T> OkHttpClient.fetchJson(request: Request, serializer: KSerializer<T>): T {
    val text = fetchText(request)
    return try {
        ApiJson.decodeFromString(serializer, text.ifBlank { "{}" })
    } catch (e: kotlinx.serialization.SerializationException) {
        throw ApiException("Unexpected answer from the server. Is this a Memos server?")
    } catch (e: IllegalArgumentException) {
        throw ApiException("Unexpected answer from the server. Is this a Memos server?")
    }
}

/** Appends a path like "api/v1/memos" to the server address, keeping any sub-path it has. */
fun HttpUrl.path(segments: String): HttpUrl.Builder = newBuilder().addPathSegments(segments.trim('/'))

/** RFC 3339 timestamps, with or without an offset. */
object Rfc3339InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Rfc3339Instant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        val s = decoder.decodeString()
        return runCatching { OffsetDateTime.parse(s).toInstant() }.getOrElse { Instant.parse(s) }
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }
}

@Serializable
enum class ApiVisibility {
    @SerialName("VISIBILITY_UNSPECIFIED") VISIBILITY_UNSPECIFIED,
    @SerialName("PRIVATE") PRIVATE,
    @SerialName("PROTECTED") PROTECTED,
    @SerialName("PUBLIC") PUBLIC,
    @SerialName("SPACE") SPACE,
}

fun ApiVisibility?.toModel(): com.vknxworks.memoctrl.data.model.MemoVisibility = when (this) {
    ApiVisibility.PROTECTED -> com.vknxworks.memoctrl.data.model.MemoVisibility.PROTECTED
    ApiVisibility.PUBLIC -> com.vknxworks.memoctrl.data.model.MemoVisibility.PUBLIC
    ApiVisibility.SPACE -> com.vknxworks.memoctrl.data.model.MemoVisibility.SPACE
    else -> com.vknxworks.memoctrl.data.model.MemoVisibility.PRIVATE
}

fun com.vknxworks.memoctrl.data.model.MemoVisibility.toApi(): ApiVisibility = when (this) {
    com.vknxworks.memoctrl.data.model.MemoVisibility.PRIVATE -> ApiVisibility.PRIVATE
    com.vknxworks.memoctrl.data.model.MemoVisibility.PROTECTED -> ApiVisibility.PROTECTED
    com.vknxworks.memoctrl.data.model.MemoVisibility.PUBLIC -> ApiVisibility.PUBLIC
    com.vknxworks.memoctrl.data.model.MemoVisibility.SPACE -> ApiVisibility.SPACE
}
