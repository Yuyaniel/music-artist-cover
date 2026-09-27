package com.musictag.artistcover.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

/**
 * 轻量 HTTP 客户端。
 *
 * 刻意只依赖 Android 平台自带的 HttpURLConnection，不引入 OkHttp/Gson：
 * 本机 Gradle 缓存里缺少 okhttp 核心包，外网又被中间人代理干扰，走平台内置 API 最稳。
 */
class HttpClient(
    private val userAgent: String =
        "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36",
) {

    fun getString(url: String, referer: String? = null, headers: Map<String, String> = emptyMap()): String =
        String(getBytes(url, referer, headers), Charsets.UTF_8)

    /** 请求并按允许跨 http/https 的方式跟随重定向，返回响应体字节。 */
    fun getBytes(url: String, referer: String? = null, headers: Map<String, String> = emptyMap()): ByteArray {
        var current = url
        var redirects = 0
        while (true) {
            val conn = open(current, referer, headers)
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                    if (location.isNullOrBlank() || redirects >= MAX_REDIRECTS) {
                        throw IOException("重定向异常（HTTP $code）：$current")
                    }
                    redirects++
                    current = resolve(current, location)
                    continue
                }
                if (code !in 200..299) {
                    throw IOException("HTTP $code：$current")
                }
                val raw = conn.inputStream ?: throw IOException("响应为空：$current")
                val gzipped = conn.contentEncoding?.contains("gzip", ignoreCase = true) == true
                val stream: InputStream = if (gzipped) GZIPInputStream(raw) else raw
                stream.use { return it.readAll() }
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun open(url: String, referer: String?, headers: Map<String, String>): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = false
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.setRequestProperty("User-Agent", userAgent)
        conn.setRequestProperty("Accept", "*/*")
        conn.setRequestProperty("Accept-Encoding", "gzip")
        conn.setRequestProperty("Connection", "close")
        if (!referer.isNullOrBlank()) {
            conn.setRequestProperty("Referer", referer)
        }
        headers.forEach { (key, value) -> conn.setRequestProperty(key, value) }
        return conn
    }

    private fun resolve(base: String, location: String): String =
        if (location.startsWith("http://", true) || location.startsWith("https://", true)) {
            location
        } else {
            URL(URL(base), location).toString()
        }

    private fun InputStream.readAll(): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (true) {
            val read = read(chunk)
            if (read < 0) break
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MAX_REDIRECTS = 5

        /** 统一的关键词编码（空格一律用 %20，避免个别接口不接受 '+'）。 */
        fun encodeKeyword(keyword: String): String =
            URLEncoder.encode(keyword, Charsets.UTF_8.name()).replace("+", "%20")
    }
}
