/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.tongban

import timber.log.Timber
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 童伴网络请求客户端
 *
 * 特性：
 *  - 独立线程池
 *  - 支持请求取消（cancel）
 *  - 5s 连接超时 / 30s 读取超时（AI 服务需要思考时间）
 *  - 异常分类（401/429/500/超时/无网络）
 */
object TongBanNetworkClient {
    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 30000
    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "TongBan-Network").apply { isDaemon = true }
    }

    /**
     * 同步发送 POST 请求
     *
     * @return RequestResult
     */
    fun postJson(
        url: String,
        body: String,
        request: TongBanRequest,
    ): TongBanResult {
        val cancelled = request.cancelled
        if (cancelled.get()) return TongBanResult.Cancelled
        var connection: HttpURLConnection? = null
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                doInput = true
                useCaches = false
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Charset", "UTF-8")
            }
            connection = conn
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body)
                writer.flush()
            }
            if (cancelled.get()) {
                conn.disconnect()
                return TongBanResult.Cancelled
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } ?: ""
            return when (code) {
                200 -> TongBanResult.Success(code, text)
                401 -> TongBanResult.Unauthorized(code, text)
                429 -> TongBanResult.RateLimited(code, text)
                in 500..599 -> TongBanResult.ServerError(code, text)
                else -> TongBanResult.UnknownError(code, text)
            }
        } catch (e: java.net.SocketTimeoutException) {
            Timber.w(e, "TongBan request timeout")
            return TongBanResult.Timeout
        } catch (e: IOException) {
            // 通常表示无网络
            Timber.w(e, "TongBan request io error")
            return TongBanResult.NoNetwork
        } catch (e: Exception) {
            if (cancelled.get()) return TongBanResult.Cancelled
            Timber.w(e, "TongBan request error")
            return TongBanResult.UnknownError(-1, e.message ?: "")
        } finally {
            connection?.disconnect()
        }
    }

    /** 异步执行 POST 请求，通过 callback 回调结果。callback 在主线程被调用。 */
    fun postJsonAsync(
        url: String,
        body: String,
        request: TongBanRequest,
        callback: TongBanCallback,
    ) {
        executor.execute {
            if (request.cancelled.get()) {
                callback.onResult(TongBanResult.Cancelled)
                return@execute
            }
            val result = postJson(url, body, request)
            callback.onResult(result)
        }
    }

    /** 创建一个可取消的请求句柄 */
    fun newRequest(): TongBanRequest = TongBanRequest()

    interface TongBanCallback {
        fun onResult(result: TongBanResult)
    }
}

class TongBanRequest {
    val cancelled = AtomicBoolean(false)
    fun cancel() {
        cancelled.set(true)
    }
    fun isCancelled(): Boolean = cancelled.get()
}

sealed class TongBanResult {
    data class Success(val code: Int, val body: String) : TongBanResult()
    data class Unauthorized(val code: Int, val body: String) : TongBanResult()
    data class RateLimited(val code: Int, val body: String) : TongBanResult()
    data class ServerError(val code: Int, val body: String) : TongBanResult()
    data class UnknownError(val code: Int, val body: String) : TongBanResult()
    object Timeout : TongBanResult()
    object NoNetwork : TongBanResult()
    object Cancelled : TongBanResult()
}
