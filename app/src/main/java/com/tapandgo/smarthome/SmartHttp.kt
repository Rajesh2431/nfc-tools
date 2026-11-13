package com.tapandgo.smarthome

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Outcome of a smart-home call; never thrown. */
sealed interface SmartResult<out T> {
    data class Ok<T>(val value: T) : SmartResult<T>
    data class Err(val reason: String) : SmartResult<Nothing>
}

data class HttpResponse(val code: Int, val body: String)

/** HTTP transport seam (tests replace it). */
fun interface HttpTransport {
    suspend fun request(url: String, method: String, headers: Map<String, String>, body: String?): SmartResult<HttpResponse>
}

/**
 * Real transport: [LanPolicy] checked first, no redirects, 8 s timeouts,
 * response capped at 512 KB. Errors become [SmartResult.Err], never exceptions.
 */
class UrlConnectionTransport : HttpTransport {
    override suspend fun request(url: String, method: String, headers: Map<String, String>, body: String?): SmartResult<HttpResponse> =
        withContext(Dispatchers.IO) {
            LanPolicy.check(url)?.let { return@withContext SmartResult.Err(it) }
            val conn = try {
                URL(url).openConnection() as HttpURLConnection
            } catch (e: Exception) {
                return@withContext SmartResult.Err("invalid URL")
            }
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                conn.requestMethod = method
                conn.setRequestProperty("User-Agent", "TapAndGo")
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                if (body != null) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = conn.responseCode
                val stream = if (code >= 400) conn.errorStream else conn.inputStream
                val text = stream?.use { s ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    while (out.size() < MAX) { val n = s.read(buf); if (n < 0) break; out.write(buf, 0, minOf(n, MAX - out.size())) }
                    out.toString(Charsets.UTF_8.name())
                }.orEmpty()
                SmartResult.Ok(HttpResponse(code, text))
            } catch (e: javax.net.ssl.SSLException) {
                SmartResult.Err("TLS error: ${e.javaClass.simpleName}")
            } catch (e: IOException) {
                SmartResult.Err("network error: ${e.javaClass.simpleName}")
            } finally {
                conn.disconnect()
            }
        }

    companion object { const val MAX = 512 * 1024 }
}
