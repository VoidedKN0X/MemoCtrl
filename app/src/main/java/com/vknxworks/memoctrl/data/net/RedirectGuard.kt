package com.vknxworks.memoctrl.data.net

import okhttp3.HttpUrl
import okhttp3.Interceptor
import java.io.IOException
import com.vknxworks.memoctrl.R
import com.vknxworks.memoctrl.str

/**
 * OkHttp follows redirects on its own. A 307/308 redirect sends the request body again (a memo you
 * wrote, a photo you attached), and some reverse proxies forward cookies. Installed as a network
 * interceptor, this sees every hop: a hop to another server never carries the access token or
 * cookies, and a request with a body is never passed on to another server at all. Moving the same
 * host from HTTP to HTTPS counts as the same server.
 */
object RedirectGuard {
    private val SENSITIVE_HEADERS = listOf("Authorization", "Cookie", "Proxy-Authorization")

    private fun sameServer(hop: HttpUrl, original: HttpUrl): Boolean {
        if (!hop.host.equals(original.host, ignoreCase = true)) return false
        if (hop.scheme == original.scheme) return hop.port == original.port
        return !original.isHttps && hop.isHttps
    }

    val interceptor = Interceptor { chain ->
        val request = chain.request()
        val original = chain.call().request()
        if (sameServer(request.url, original.url)) return@Interceptor chain.proceed(request)
        if (request.body != null) {
            throw IOException(str(R.string.error_redirect_blocked, request.url.host))
        }
        val stripped = request.newBuilder().apply { SENSITIVE_HEADERS.forEach { removeHeader(it) } }.build()
        chain.proceed(stripped)
    }
}
