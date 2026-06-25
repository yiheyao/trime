/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.tongban

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.Toast
import timber.log.Timber

/**
 * 童伴问答浮窗控制器（精简版）
 *
 * - 输入清洗、查询防抖、loading 状态、网络异常展示
 * - 所有 UI 反馈通过 Toast 输出（弹窗只有一行，没有 responseScroll）
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
        ui.queryButton.setOnClickListener { onQueryClick() }
        ui.closeButton.setOnClickListener { onCloseClick() }
        // 长按输入框直接粘贴（监听器在 TongBanDialogUi 中绑定）
        recordNetworkType()
    }

    /** 打开浮窗（每次打开都重置 session） */
    fun open() {
        service.resetSession()
        ui.clearInput()
        ui.hideResponse()
        ui.hidePasteButton()
        querying = false
        currentRequest?.cancel()
        currentRequest = null
    }

    /** 关闭浮窗（取消进行中的请求、重置 session） */
    fun close() {
        cancelRequest()
        service.resetSession()
        ui.clearInput()
        ui.hideResponse()
        ui.hidePasteButton()
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
        Timber.tag("TongBan").i("onQueryClick")
        // 点击查询时震动一下，给用户查询的体感反馈
        performQueryHaptic()
        if (querying) {
            showToastAtTop("查询中，请稍候")
            return
        }
        val raw = ui.getInputText()
        val cleaned = cleanInput(raw)
        Timber.tag("TongBan").i("onQueryClick raw='%s' cleaned='%s'", raw, cleaned)
        if (cleaned.isEmpty()) {
            showToastAtTop("请输入问题")
            return
        }
        // 网络切换检测
        val nowType = currentNetworkType()
        if (lastNetworkType != null && nowType != lastNetworkType) {
            cancelRequest()
            showToastAtTop("网络已切换，请重试")
            lastNetworkType = nowType
            return
        }
        lastNetworkType = nowType

        querying = true
        showToastAtTop("查询中… cleaned='$cleaned'")
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

    private fun showToastAtTop(text: String) {
        val t = Toast.makeText(context, text, Toast.LENGTH_LONG)
        t.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 200)
        t.show()
    }

    private fun handleResult(originalText: String, result: TongBanResult) {
        querying = false
        currentRequest = null
        Timber.tag("TongBan").i("handleResult %s", result.javaClass.simpleName)
        when (result) {
            is TongBanResult.Success -> {
                val resp = service.parseSuccess(result.body)
                Timber.tag("TongBan").i("Success body=%s", result.body)
                if (resp == null || resp.response.isBlank()) {
                    showToastAtTop("服务器返回为空")
                } else {
                    // 成功响应写入弹窗内的响应框，可滚动可选择/复制
                    ui.showResponse(resp.response)
                    showToastAtTop("响应: ${resp.response.take(50)}")
                }
            }
            is TongBanResult.Unauthorized -> {
                ui.showResponse("⚠ 未授权 (401)\n请检查登录状态")
                showToastAtTop("未授权 (401)")
            }
            is TongBanResult.RateLimited -> {
                ui.showResponse("⚠ 请求过于频繁 (429)\n请稍后再试")
                showToastAtTop("请求过于频繁 (429)")
            }
            is TongBanResult.ServerError -> {
                ui.showResponse("⚠ 服务器错误 (5xx)\n${result.body.take(200)}")
                showToastAtTop("服务器错误 (5xx)")
            }
            is TongBanResult.UnknownError -> {
                ui.showResponse("⚠ 未知错误\n${result.body.take(200)}")
                showToastAtTop("未知错误")
            }
            TongBanResult.Timeout -> {
                ui.showResponse("⏱ 请求超时\n网络不稳定或服务繁忙，请重试")
                showToastAtTop("请求超时")
            }
            TongBanResult.NoNetwork -> {
                ui.showResponse("📡 无网络\n请检查网络连接后重试")
                showToastAtTop("无网络")
            }
            TongBanResult.Cancelled -> {
                // 不展示任何提示
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
        s = s.trim()
        s = s.replace("\n", "").replace("\r", "")
        s = s.replace(Regex("[\\p{Cntrl}]"), "")
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

    /**
     * 点击查询按钮时的震动反馈。
     * 优先尝试 HapticFeedbackConstants.CONFIRM（Android 12+），否则回退到 LONG_PRESS。
     * 使用 View 自身的 performHapticFeedback 不需要额外权限。
     */
    private fun performQueryHaptic() {
        val view = ui.queryButton
        val hfc =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
        try {
            val flags =
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            view.performHapticFeedback(hfc, flags)
        } catch (e: Exception) {
            // 设备不支持时静默失败，不影响主流程
        }
    }

    companion object {
        const val MAX_INPUT_CHARS = 200
    }
}
