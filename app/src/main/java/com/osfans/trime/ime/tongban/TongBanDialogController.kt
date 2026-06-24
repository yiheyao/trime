/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.tongban

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.osfans.trime.R
import splitties.systemservices.clipboardManager
import timber.log.Timber

/**
 * 童伴问答浮窗控制器
 *
 * - 负责：输入清洗、查询防抖、loading 状态、网络异常展示
 * - 网络回调统一切到主线程
 */
class TongBanDialogController(
    private val context: Context,
    private val ui: TongBanDialogUi,
    private val service: TongBanService,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentRequest: TongBanRequest? = null
    private var querying = false
    private var lastNetworkType: String? = null

    init {
        ui.bindInputWatcher()
        ui.queryButton.setOnClickListener { onQueryClick() }
        ui.closeButton.setOnClickListener { onCloseClick() }
        // 长按输入框可粘贴
        ui.inputEditText.setOnLongClickListener {
            ui.pasteFromClipboard()
            true
        }
        recordNetworkType()
    }

    /** 打开浮窗（每次打开都重置 session） */
    fun open() {
        service.resetSession()
        ui.clearInput()
        ui.clearResponses()
        querying = false
        currentRequest?.cancel()
        currentRequest = null
    }

    /** 关闭浮窗（取消进行中的请求、重置 session） */
    fun close() {
        cancelRequest()
        service.resetSession()
        ui.clearInput()
        ui.clearResponses()
        querying = false
    }

    /** 强制中断进行中的请求（APP 切后台等） */
    fun cancelRequest() {
        currentRequest?.cancel()
        currentRequest = null
        querying = false
    }

    fun release() {
        cancelRequest()
    }

    private fun recordNetworkType() {
        lastNetworkType = currentNetworkType()
    }

    private fun onQueryClick() {
        if (querying) return
        val raw = ui.getInputText()
        val cleaned = cleanInput(raw)
        if (cleaned.isEmpty()) {
            ui.showError(context.getString(R.string.tongban_input_hint))
            return
        }
        // 网络切换检测
        val nowType = currentNetworkType()
        if (lastNetworkType != null && nowType != lastNetworkType) {
            cancelRequest()
            ui.showError(context.getString(R.string.tongban_error_network_switch))
            lastNetworkType = nowType
            return
        }
        lastNetworkType = nowType

        querying = true
        ui.showLoading()
        val request = TongBanNetworkClient.newRequest()
        currentRequest = request
        service.sendMessage(cleaned, request, object : TongBanNetworkClient.TongBanCallback {
            override fun onResult(result: TongBanResult) {
                mainHandler.post {
                    handleResult(cleaned, result)
                }
            }
        })
    }

    private fun handleResult(originalText: String, result: TongBanResult) {
        querying = false
        currentRequest = null
        ui.hideLoading()
        when (result) {
            is TongBanResult.Success -> {
                val resp = service.parseSuccess(result.body)
                if (resp == null || resp.response.isBlank()) {
                    ui.showError(context.getString(R.string.tongban_error_unknown))
                } else {
                    ui.showResponses(listOf(resp.response))
                }
            }
            is TongBanResult.Unauthorized -> ui.showError(context.getString(R.string.tongban_error_unauthorized))
            is TongBanResult.RateLimited -> ui.showError(context.getString(R.string.tongban_error_rate_limited))
            is TongBanResult.ServerError -> ui.showError(context.getString(R.string.tongban_error_server))
            is TongBanResult.UnknownError -> ui.showError(context.getString(R.string.tongban_error_unknown))
            TongBanResult.Timeout -> ui.showError(context.getString(R.string.tongban_error_timeout))
            TongBanResult.NoNetwork -> ui.showError(context.getString(R.string.tongban_error_no_network))
            TongBanResult.Cancelled -> {
                // 不展示任何错误
            }
        }
    }

    private fun onCloseClick() {
        close()
        onClosed?.invoke()
    }

    /** 关闭时回调，用于通知 InputView 隐藏浮窗 */
    var onClosed: (() -> Unit)? = null

    /**
     * 输入清洗规则
     *  - 去除首尾空白
     *  - 去除换行
     *  - 过滤常见特殊符号
     *  - 限制最大长度 200 个汉字（按字符截断）
     */
    private fun cleanInput(input: String): String {
        if (input.isEmpty()) return ""
        var s = input
        // 去除首尾空白
        s = s.trim()
        // 去除换行和回车
        s = s.replace("\n", "").replace("\r", "")
        // 过滤特殊控制字符
        s = s.replace(Regex("[\\p{Cntrl}]"), "")
        // 限制长度：按字符计，最多 200
        if (s.length > MAX_INPUT_CHARS) {
            s = s.substring(0, MAX_INPUT_CHARS)
        }
        return s
    }

    private fun currentNetworkType(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                ?: return "unknown"
            val network = cm.activeNetwork ?: return "none"
            val caps = cm.getNetworkCapabilities(network) ?: return "unknown"
            when {
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }

    companion object {
        const val MAX_INPUT_CHARS = 200
    }
}
